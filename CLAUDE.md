# CLAUDE.md

Perfume Prices (Ценовник парфема): a single-page web app that tracks monthly perfume price lists, plus an Android WebView wrapper. No backend, no build step, no package manager. See `README.md` for the user-facing feature list.

## Working with the owner
- The owner writes in Serbian (Cyrillic). Reply in Serbian Cyrillic, short and non-technical.
- Changes go through a PR into `main`. Merging to `main` publishes a new APK release, so tell the owner the release link after a merge (`https://github.com/kosmet-crypto/Cenovnik-Parfema/releases/latest/download/perfume-prices.apk`).
- Don't change anything the owner didn't ask for.

## Layout
- `index.html`: the whole app (HTML, CSS and one inline `<script>`). Plain JS, no framework, no modules.
- `lib/xlsx.full.min.js`: vendored SheetJS. Never edit it by hand. To upgrade, change `lib/SHEETJS_VERSION` and push; `.github/workflows/sheetjs.yml` downloads and commits it.
- `sw.js`: service worker for the PWA. **Bump `VERSION` whenever `index.html`, `lib/`, `manifest.json` or `icons/` change**, or installed copies keep the old files.
- `manifest.json`, `icons/`: PWA metadata.
- `android/`: WebView wrapper (`app.perfumeprices`). Gradle copies `index.html`, `manifest.json`, `icons/`, `lib/` into the APK assets at build time (`copyWebApp` in `android/app/build.gradle`).
  - `MainActivity.java`: WebView plus the JS bridge `window.PerfumeAndroid` (`getVersion`, `checkUpdate`, `saveFile`, `canDeletePicked`, `deletePicked`).
  - `Ota.java`: silent updates of `index.html` from the newest commit on `main`, with no new APK.
  - `SelfUpdate.java`: offers a new APK from GitHub Releases, only when the native fingerprint changed.

## index.html conventions
- **Translations:** every user-visible string goes through `t('српски текст', {vars})`. The Serbian Cyrillic text is the key. `TR['key'] = [english, norwegian]`. Serbian Latin is not translated. It comes from transliterating the output with `L()`/`toLat()`. When adding a string, add its `TR` entry with both English and Norwegian, and remove entries that are no longer used. Plurals use `PW(n, key)` with the `PL` table.
- Languages: `sr` (default), `sr-lat`, `en`, `no`. `base()` gives `sr`/`en`/`no`.
- **Storage:** `localStorage` keys end in `V1` (`perfumePricesV1`, `perfumeFredrikPricesV1`, `perfumeFavV1`, `perfumeCurrencyV1`, `perfumeLangV1`, `perfumeLastBackupV1`). Keep old data readable: existing users' data must survive updates. Anything new that is user data must also go into backup export/import.
- Code comments are in Serbian. Match the compact existing style.
- Things that only make sense in the APK (for example, deleting the imported Excel file) check `Native` (`window.PerfumeAndroid || null`) first. The browser version must keep working without it.
- **Android bridge version:** `<meta name="app-native" content="N">` must match `Ota.NATIVE_API`. If `index.html` starts using a new bridge method, bump both. Otherwise OTA would push a page that older APKs can't run.

## CI / releases
- `.github/workflows/android.yml` builds the APK on PRs and on pushes to `main` that touch `index.html`, `manifest.json`, `icons/`, `lib/`, `android/`. On `main` it publishes release `v1.0.<run_number>` with `perfume-prices.apk`, and puts `native: <hash>` in the notes. The app uses that hash to decide whether an APK update is needed.
- The signing keystore is committed on purpose (sideloading needs a fixed key). Don't change it, or installed apps can't update.

## Checking changes
- No tests or linter. Serve the repo root (`python3 -m http.server`) and open it in Chromium via Playwright (preinstalled). Check light and dark mode and all four languages, and look for page errors and missing `TR` keys.
- Local APK build (needs the Android SDK): `cd android && ./gradlew assembleRelease`. Usually just rely on the PR's CI build.
