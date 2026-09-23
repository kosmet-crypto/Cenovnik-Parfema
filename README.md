# Perfume Prices (Cenovnik Parfema)

Monthly perfume price lists with price trends. Import each month's Excel or CSV list and see what went up, down, is new or was removed, with price history per perfume and your own Fredrik & Louisa price comparison. Works on phones and desktop browsers, with no account and no server: everything stays on your device (`localStorage`).

## Features
* Import `.xlsx`, `.xls`, `.csv` or text lists (perfume + price columns are detected automatically).
* Month-to-month comparison, search, filters, sparklines and a price chart per perfume.
* Four languages: Serbian (Cyrillic, default), Serbian (Latin), English and Norwegian. Switch on the Lists tab (*Језик*). The Android app's own dialogs follow the phone's language.
* Backup and restore in one small JSON file: all months, the currency label and your Fredrik & Louisa prices.
* Installable as an app (PWA) and works offline after the first visit.
* Android app (APK) that tells you when a new version is out.

## Excel files are not kept
Importing reads the file once and saves only the perfume names and prices. The app never stores the Excel file itself, so you can delete it afterwards.
In the Android app, right after an import you are asked whether to delete the file from your phone. **Keep file** leaves it; **Delete file** removes only the file. The list, trends and backups are not affected either way.

## Install as an app
* **Android / Chrome:** open the link, then menu → *Install app* (or *Add to Home screen*).
* **iPhone / Safari:** open the link, tap *Share* → *Add to Home Screen*.

When changing `index.html`, `lib/` or the icons, bump `VERSION` in `sw.js` so installed copies pick up the update.

## Android app (APK)
Every change merged into `main` builds a new APK with GitHub Actions and publishes it as a release.
Always the newest version: https://github.com/kosmet-crypto/Cenovnik-Parfema/releases/latest/download/perfume-prices.apk

1. Open the link on your Android phone and download `perfume-prices.apk`.
2. Open the file. Android will ask to allow installs from your browser or file manager; allow it once.
3. Install. Newer APKs install over the old one and keep your price lists.

The app checks for a newer release at most twice a day and offers to download it. Updates are not silent: you tap **Download**, then open the file to install.

The APK bundles `index.html` and the Excel reader, so it works offline from the first launch. Its data is stored inside the app,
separately from the browser version, so use **Export backup** in the browser and **Import backup** in the app (Lists tab) to move your data.
The Android project lives in `android/` (a small WebView wrapper). To build locally: `cd android && ./gradlew assembleRelease`.
