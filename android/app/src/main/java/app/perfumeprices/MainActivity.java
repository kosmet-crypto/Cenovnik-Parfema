package app.perfumeprices;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Hosts the Perfume Prices web app (bundled in assets/www) in a full-screen WebView.
 * Pages are served from https://appassets.androidplatform.net so localStorage
 * behaves like on a normal https site.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_PICK_FILE = 1;
    private static final int REQ_SAVE_FILE = 2;

    private WebView webView;
    private ValueCallback<Uri[]> pendingPick;
    private String pendingSaveText;
    /** Last file the page opened (a price list or a backup), so it can offer to delete it. */
    private volatile Uri lastPicked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0E0D0C);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        webView.addJavascriptInterface(new Bridge(), "PerfumeAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (HOST.equals(url.getHost())) return false;
                // Anything outside the app opens in the browser.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingPick != null) pendingPick.onReceiveValue(null);
                pendingPick = callback;
                // OPEN_DOCUMENT (not GET_CONTENT) so the picked file can be deleted afterwards.
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                // The page accepts JSON, CSV, text and .xlsx; MIME types for those vary by
                // file manager, so allow any file and let the page validate it.
                i.setType("*/*");
                try {
                    startActivityForResult(i, REQ_PICK_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingPick = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);

        if (savedInstanceState == null) checkForUpdate();
    }

    /* ---------- update check ---------- */

    private static final long UPDATE_CHECK_INTERVAL = 12 * 60 * 60 * 1000L;

    /**
     * Looks up the latest GitHub Release (tagged v1.0.<versionCode>) and offers to download it
     * when it is newer than this install. Silent when offline or on any error.
     */
    private void checkForUpdate() {
        final SharedPreferences prefs = getSharedPreferences("update", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("lastCheck", 0) < UPDATE_CHECK_INTERVAL) return;
        prefs.edit().putLong("lastCheck", now).apply();

        new Thread(() -> {
            try {
                URL api = new URL("https://api.github.com/repos/" + BuildConfig.UPDATE_REPO + "/releases/latest");
                HttpURLConnection c = (HttpURLConnection) api.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                if (c.getResponseCode() != 200) return;
                String body;
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[8192];
                    for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                    body = buf.toString("UTF-8");
                }
                String tag = new JSONObject(body).optString("tag_name", "");
                int dot = tag.lastIndexOf('.');
                if (dot < 0) return;
                final long latest = Long.parseLong(tag.substring(dot + 1));
                final String name = tag.startsWith("v") ? tag.substring(1) : tag;
                if (latest > installedVersionCode()) runOnUiThread(() -> showUpdateDialog(name));
            } catch (Exception ignored) {
                // No network, rate limit or unexpected response: try again next time.
            }
        }).start();
    }

    private long installedVersionCode() throws Exception {
        PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private void showUpdateDialog(String version) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("Perfume Prices " + version + " is ready. Download it and open the file to update. Your price lists stay in place.")
                .setPositiveButton("Download", (d, w) -> {
                    Uri apk = Uri.parse("https://github.com/" + BuildConfig.UPDATE_REPO
                            + "/releases/latest/download/perfume-prices.apk");
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, apk));
                    } catch (ActivityNotFoundException ignored) {
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    /** Called from index.html to save a backup, since WebView cannot download blob: URLs. */
    private class Bridge {
        @JavascriptInterface
        public void saveFile(final String name, final String text) {
            runOnUiThread(() -> {
                pendingSaveText = text;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, name);
                try {
                    startActivityForResult(i, REQ_SAVE_FILE);
                } catch (ActivityNotFoundException e) {
                    pendingSaveText = null;
                    Toast.makeText(MainActivity.this, "No app available to save files", Toast.LENGTH_LONG).show();
                }
            });
        }

        /** True when the file the page last opened is called {@code name} and the phone allows deleting it. */
        @JavascriptInterface
        public boolean canDeletePicked(String name) {
            Uri uri = lastPicked;
            if (uri == null || name == null || !DocumentsContract.isDocumentUri(MainActivity.this, uri)) return false;
            String[] cols = {OpenableColumns.DISPLAY_NAME, DocumentsContract.Document.COLUMN_FLAGS};
            try (Cursor c = getContentResolver().query(uri, cols, null, null, null)) {
                if (c == null || !c.moveToFirst()) return false;
                int flags = c.isNull(1) ? 0 : c.getInt(1);
                return name.equals(c.getString(0)) && (flags & DocumentsContract.Document.FLAG_SUPPORTS_DELETE) != 0;
            } catch (Exception e) {
                return false;
            }
        }

        /** Deletes the file the page last opened (after canDeletePicked said yes). The app's data is not touched. */
        @JavascriptInterface
        public boolean deletePicked(String name) {
            Uri uri = lastPicked;
            if (!canDeletePicked(name)) return false;
            try {
                boolean ok = DocumentsContract.deleteDocument(getContentResolver(), uri);
                if (ok) lastPicked = null;
                return ok;
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = (resultCode == RESULT_OK && data != null) ? data.getData() : null;

        if (requestCode == REQ_PICK_FILE && pendingPick != null) {
            lastPicked = uri;
            pendingPick.onReceiveValue(uri != null ? new Uri[]{uri} : null);
            pendingPick = null;
        } else if (requestCode == REQ_SAVE_FILE) {
            String text = pendingSaveText;
            pendingSaveText = null;
            if (uri == null || text == null) return;
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not save backup", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }
}
