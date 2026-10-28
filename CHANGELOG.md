# Changelog
## 0.6.35
- New: browser downloads are now also saved to the phone's own Downloads folder, so they show up in the file manager. Before this, every download went only to a private inbox and then into the encrypted vault, so a browser download seemed to "save nowhere". New `PrivBrowser.savePhone(name)`: Android 10 and newer write through MediaStore (no permission needed), Android 9 and older write to the public Downloads folder (needs the storage permission already declared). The inbox copy stays, so sealing into the vault still happens afterwards. If the copy to Downloads fails, a message says why and the file still goes to the vault.
- New: Settings > Downloads > "Also save a copy to the phone Downloads folder" (on by default). The copy in Downloads is NOT encrypted. Turn it off to keep downloads only in the vault.
- New: Menu > Downloads shows a 📲 button on each waiting file and a "Copy N waiting files to the phone Downloads" button. These copy only; they do not seal.
- Java changed (PrivBrowser.java): needs a fresh pipeline build and APK install. NEED_MIN knows savePhone needs V0.6.35.
- Build (GitHub): .github/workflows/build.yml now uses Node 22 and Java 21 (Capacitor 8), installs Android platform and build-tools 36 next to 34, runs scripts/patch-android.sh (installs the native Java files, so PrivBrowser is in the APK) and runs build.sh assets (paw launcher icon and splash from assets/). Before this the GitHub APK had the default Capacitor icon and none of the native files.
- Build (icon): assets/ now also holds real PNG sources (icon-only.png, icon-foreground.png, icon-background.png, splash.png, splash-dark.png) drawn from the paw in icon.svg, so icon generation no longer depends on the tool reading SVG files.
- Build (GitLab): .gitlab-ci.yml builds assembleRelease and signs it with your own key from the CI variables KEYSTORE_B64, KEYSTORE_PASS, KEY_ALIAS, prints the signing certificate SHA-256, runs verify-build.sh on the APK, and stops if a variable is missing. Before, it built assembleDebug, which is signed with a different throw-away debug key on every runner, so a new APK could not be installed over the old one. Output: android/BojackSecures-signed.apk. Version code now follows VER (0.6.35 = 635).
- Not tested on a phone and not run in a pipeline (no Android SDK here).
## 0.6.34
- Fix: "Failed to execute 'transaction' on 'IDBDatabase': The database connection is closing". IndexedDB was opened once and that connection was reused forever; when the WebView closed it, every later transaction threw. It is now opened on demand, waits for the open to finish before any transaction, drops the connection when the browser closes it or asks to upgrade, and retries a transaction up to 3 times on a closing connection. Transactions that abort now reject instead of hanging. The duplicate cursor read in the loader now uses the same idbRows().
- Build: patch-android.sh now reads the real package from android/app/build.gradle, refuses to continue unless it is com.fezzy.vault, copies every scripts/android-native/*.java into that package folder, removes any other MainActivity.java left in another folder, and stops with an error if any file did not arrive byte-identical.
- Build: verify-build.sh now looks for the exact dex class descriptors (Lcom/fezzy/vault/BojackStore; etc.) inside the signed APK, also requires MainActivity, KeepAliveService and PrivBrowser, and fails if the APK has no classes*.dex.
- Fix (wording): the self-test said "NOT in this APK" and "the pipeline built the app without its native files" whenever BojackStore was not registered by Capacitor, even when BojackKeepAlive (copied by the same line) was present. Both files are always copied together, so that wording was wrong. It now says "NOT loaded", explains that the Java was built in, and prints the Native start-up log inside the self-test report. The pipeline log line to look for is now "[patch] 7 native Java files installed".
- New: the vault storage no longer depends on Capacitor registering BojackStore. New `BojackStoreJs.java` exposes the same storage as a plain Android bridge (`window.BojackST`), writing the same files in the same folder (filesDir/vault), so either path reads what the other wrote. `ST()` uses the Capacitor plugin when Capacitor registered it and the direct bridge otherwise. Fixes V0.6.33 phones where the self-test said BojackStore was not loaded and the vault fell back to IndexedDB. MainActivity adds the bridge (one new try-block, same pattern as BojackJs). verify-build.sh requires BojackStoreJs in the project and the APK. Settings > System info shows which path is in use.
- Changed: MainActivity.java now also adds the BojackST bridge (see above). It still registers BojackKeepAlive and BojackStore with registerPlugin() before super.onCreate().
- Not changed: MainActivity.java already registers BojackKeepAlive and BojackStore with registerPlugin() before super.onCreate(). PrivBrowser is not a Capacitor plugin (it is a plain @JavascriptInterface, window.BojackPB) so it is attached with addJavascriptInterface, not registerPlugin().
- Untested on a phone and not run in a real pipeline (no Android SDK here). JS parses (check-js.js), bash -n passes, verify-build.sh was run against a simulated project and fake APKs.
## 0.6.33
- New: the privacy browser is back, written fresh (no code from the removed Prodigy-based browser). Globe button in the top bar of every screen. Web pages are native (new `PrivBrowser.java`, reached as `window.BojackPB`, no Capacitor plugin and no androidx.webkit); every bar and panel is drawn by the vault page in black and neon purple.
- Search: DuckDuckGo is the default. Also DuckDuckGo Lite, Brave Search, Startpage, Mojeek, Qwant and a custom https link with %s. Optional home page; otherwise a Bojack start page with your bookmarks.
- Tabs (max 10, each with its own history), address bar, back / forward / reload / stop, progress bar, find in page, bookmarks, optional history (off by default), long-press a link or image for open in new tab / save / copy.
- Add-ons: ad and tracker blocker (about 170 known hosts blocked in the network layer, ad boxes hidden), cookie banner remover, HTTPS only, fingerprint shield (fixed CPU/memory values, Do Not Track, Global Privacy Control), WebRTC leak guard, dark pages, image blocker, JavaScript switch, text size, desktop site, reader mode, download media.
- Downloads: images, videos, music and documents. Menu > Download media lists everything the page exposes (All / Images / Videos / Music / Documents, Save, Save all up to 40). Direct links, data: and page-made blob: files (96 MB max) work. Files go to a private inbox (`filesDir/browser-inbox`), are sealed into the vault through the normal import path and the inbox copy is deleted only after it is sealed. Locked or busy vault: the file waits and is sealed on the next unlock or every 15 s while the browser is open.
- Privacy defaults: camera, microphone and location requests are always denied; no popups without a tap; certificate errors never proceed; mixed content blocked; other-app links (intent:, fb:, market:) blocked; clear cookies, cache and site data when the browser closes (switch). The vault locking closes the browser.
- Not supported: uploading files from a web page, pages that stream video in pieces, plain http sites while HTTPS only is on.
- Build: `verify-build.sh` now requires PrivBrowser in the project and the APK and still fails on the old browser classes and androidx.webkit. Java changed: needs a fresh pipeline build and APK install. Untested on a phone.
## 0.6.32
- Removed: the built-in browser, completely. The globe button in the top bar, the Browser tab in the bottom bar, browser downloads, the download inbox and everything behind them are gone. Nothing in the app opens a web page any more. The vault, encryption, storage, import, viewer, sharing and Settings are unchanged.
- Removed (Java): WebSheet, BrowserExt, BojackBrowser, BjDownload, BjBlob. BojackJs now only reports the start-up log. MainActivity registers BojackKeepAlive and BojackStore only. androidx.webkit is no longer a dependency.
- Build: patch-android.sh deletes leftover browser classes and the androidx.webkit line from an older android/ project. verify-build.sh now fails the build if any browser class or androidx.webkit is still in the project or the APK. Java changed, so this needs a fresh pipeline build and APK install.
- Note: files a browser download left in the app's private inbox folder on V0.6.25 to V0.6.31 stay there untouched; this version has no screen that reads them.
- Entries 0.6.31 and older below are history and still describe the browser as it was.
## 0.6.31
- Fix: the built-in browser now opens even when Capacitor does not load the BojackBrowser plugin. V0.6.30 phones showed "Built-in browser: NOT in this APK" and the globe button fell back to an outside browser. New `BojackJs.java` exposes a direct Android bridge (`window.BojackNative`: open and close the browser, list and delete the download inbox) that uses WebSheet without Capacitor. `index.html` uses the Capacitor plugin when the phone has it and this bridge otherwise, so downloads still go to the private inbox and are sealed into the vault.
- New: `MainActivity` registers each plugin on its own and records what happened. Settings > System info shows a "Native start-up log" row, e.g. `Capacitor loaded: KeepAlive=true Store=false Browser=false`, so the real reason for any missing plugin is on screen instead of guessed.
- New: Browser tab in the bottom bar (next to the category tabs). It does the same as the globe button. A failed open now shows the real error text and writes it to the error log.
- Build: Java changed (BojackJs new, MainActivity). verify-build.sh also checks BojackJs is in the dex and that MainActivity adds the bridge. Needs a fresh pipeline build and APK install.
- Untested on device. BojackJs and MainActivity compile clean against stubs of the Android/Capacitor APIs (javac 21), not against the real SDK. Not run in a pipeline.
## 0.6.30
- Fix: the message "Needs the new build (V0.6.9 native code)" is gone. It was a fixed sentence that never said what the phone had. The app now reads the real installed APK version (App plugin, works in every APK) and checks the three native parts (keep-alive/file picker/link opener, vault storage, browser). The new screen "Needs a newer APK" says one of three true things: the APK is older than the feature (with both version numbers), the APK is current but its native part is missing (the build left the native files out, not a download you forgot), or the version could not be read. Used by Open link, every Android settings shortcut and the Browser button, which no longer shows the long guess about "older than its screens".
- New: Settings > System info shows Installed APK (version, build number, matches / older / incomplete), each native part present or missing, and a plain "what to do" line when something is wrong. The self-test has a matching first step.
- New: once per app start, 3.5 s after unlock, a single notice appears only if the APK is older or incomplete. Nothing shows when everything is fine.
- New: build guard `scripts/verify-build.sh`. In the pipeline, `project` runs after the Android project is generated (all 9 Java files copied and identical, MainActivity registers BojackKeepAlive, BojackStore and BojackBrowser, KeepAliveService in the manifest, androidx.webkit, versionName equals VER, synced www equals VER) and `apk` runs on the signed APK (versionName via aapt2 when present, the screens inside the APK equal VER, the native classes are inside the dex). Any failure stops the job with the reason, so a hollow APK is never published. `build.sh check` and the check job also syntax-check it.
- New: README.md was empty. It now says which APK to install and how to confirm it, what the browser and Downloads do, and to put the contents of the zip folder (not the folder) at the repo root, which is what broke the pipelines before.
- Not changed: Java, encryption, vault format, the browser and its Downloads / Download media screens (already black and purple since 0.6.29). Because the screens live inside the APK, this still needs a fresh pipeline build and APK install.
- Tested here: diagnostic logic with mocked phones (old APK, hollow APK, unreadable version, plain browser, once-only notice, no repeat page), verify-build.sh against a simulated Android project and fake APKs (healthy passes; missing Java file, stale www and missing native classes each fail), repo check script, bash -n. Not run on a phone; not run in a real pipeline; aapt2 version read untested.
## 0.6.29
- Fix: the browser is no longer in the Add files sheet. It opens only from the globe button in the top bar of every screen.
- New: Download media (browser menu > Download media, also Extensions). Works on ANY page, not only social sites. Lists the images, videos and music the page exposes (img/srcset, video, audio, direct file links, og: tags) with All / Images / Videos / Music filters, Save per file, Save all (up to 40). Every Save goes through the same path as other browser downloads: private inbox, sealed into the vault, inbox copy deleted after. The in-page green buttons now open this list instead of a page overlay (the old overlay used a download attribute that cross-origin files ignore, so files often played instead of saving).
- New: Bojack download toast. A branded pill shows above the browser, its panels and the vault: Downloading (live percent and MB) > Saved, sealing into your vault > In your vault (sealed and encrypted), or the failure reason. The plain Android toast it replaces was easy to miss.
- New: Bojack look. The browser was Prodigy's green/cyan; it is now black + neon purple (#b026ff) everywhere (bars, panels, toggles, arrows, injected page buttons), with a Bojack brand strip under the address bar.
- New: privacy. Search engines add Mojeek and Qwant (DuckDuckGo stays the default; private engines are labelled). Settings > Privacy and security: ad and tracker blocker, HTTPS only (plain http is upgraded), Do Not Track + Global Privacy Control, Clear data when the browser closes. Bookmarks (saved in two places, survive a kill) are unchanged.
- Build: Java changed (WebSheet, BrowserExt, BjDownload, BjBlob, BojackBrowser). Needs a fresh CI build and APK install; www alone cannot add it.
- Untested on device: Java not compiled here (no Android SDK), only parsed clean. Injected JS syntax-checked in node. Nothing run on a phone.
## 0.6.28
- New: Browser button (globe) in the top bar of every screen, next to Hidden files / Settings / Lock. One tap from anywhere in the vault. The Add files sheet entry stays. Also includes everything in 0.6.27 (blob/data downloads, Downloads page, DuckDuckGo default), which needs the new APK.
- Untested on device.
## 0.6.27
- New: blob: and data: downloads now work in the browser (pages that build the file themselves: Export / Save buttons, generated PDFs and images, canvas saves). The page hands the file over in chunks to a one-time-token bridge (BjBlob), it lands in the private inbox and is sealed like any other download. The file name from the page's download attribute is kept; a missing extension is added from the file type. Cap 1 GB per file.
- New: the browser's Downloads page is real. Menu > Downloads (and Settings > Downloads) lists each file with its state: Saved, sealing into your vault / In your vault / Failed with the reason, plus size and time. Clear list keeps files still waiting.
- Change: the browser now starts on DuckDuckGo (was Google). Anyone who already picked an engine keeps their choice; change it in browser Settings > Search engine. The older-APK fallback also opens DuckDuckGo.
- Change: a plain http link now says why it failed (Android blocks plain http) instead of a raw error.
- Build: new file scripts/android-native/BjBlob.java (copied by patch-android.sh with the others). Needs a fresh CI build and APK install; www alone cannot add it.
- Untested: Java not compiled here (no Android SDK), nothing run on a device. Checked: Java parses clean, inline JS passes the repo check script.
## 0.6.26
- Fix: tapping Browser on an install that has no native browser said "The browser is not in this build. Rebuild the app", which read like "download a browser". It now says plainly that the installed APK is older than the screens (updating the web files alone cannot add the native browser), and opens the phone's own browser so you are not stuck. To get the built-in browser you must install an APK built from this version: the browser is native code (WebSheet.java, BojackBrowser.java), not part of www/.
- Untested on device. Java not touched.
## 0.6.25
- New: in-app browser, copied from the Prodigy Syncing browser (tabs, bookmarks, history, search engines, ad blocker, dark mode, reader mode, desktop site). Open it from the add-files sheet ("Browser"). Opens full screen, Back closes it, the menu has "Close browser".
- New: downloads from the browser go straight into the vault. The file is saved in the app's private inbox folder (never the public Downloads folder), sealed with the normal import pipeline, and the inbox copy is deleted only after it is sealed. If the vault is locked or busy, or a file fails, it stays in the inbox and is retried (on unlock/return, and every 15 s). Lands in the folder you are viewing.
- Change: the vault locking closes the browser, so no web page stays on screen over the lock screen.
- Removed from the copy: the YouTube download button (needs NewPipe, which this app does not have).
- Not supported: blob: and data: downloads (the browser says so). http (non-https) pages are blocked by Android by default.
- Privacy note: the browser's history, bookmarks and cookies are kept in the app's private storage but are NOT encrypted by the vault. Use browser menu > Settings > clear data when you want them gone.
- Build: new files scripts/android-native/WebSheet.java, BrowserExt.java, BjDownload.java, BojackBrowser.java; MainActivity registers the plugin and forwards the file-chooser result; patch-android.sh adds androidx.webkit.
- Untested: Java not compiled here, nothing run on a device. Checked: all inline JS passes the repo check script, patch script passes bash -n.
## 0.6.24
- Fix (the big one): the safe no longer sits empty for minutes. Unlocking now shows a loading bar that says "Loading safe…" with a live count and seconds, and your file list appears at once: an encrypted copy of the list (sealed with the vault key, kept in the vault's own storage) is drawn the moment you log in, then the real read runs behind it and only redraws if something changed. The first unlock after updating has no copy yet, so it shows the bar until the first read finishes; every unlock after that is instant
- Fix: the old WebView storage is no longer read all at once before the list can show. The native list shows first, then older records are read one at a time with the bar ("Loading older files… 12/80"), and remembered for the session. The move-to-new-storage job now starts only after loading finishes and waits while a file is open
- Fix: if reading the vault fails or times out, the screen says so with a Try again button instead of staying on "Opening your vault…"
- Fix: documents, CSV, spreadsheets, slides and text opened into a blank or squashed page (they were laid out as a centred row meant for one photo). They now use normal page flow. The viewer shows "Decrypting… Ns" while it works and, if anything fails, the reason with a Try again button
- Fix: PDF showed a blank white page because the Android WebView cannot show PDFs inside the app. PDFs are now drawn page by page with pdf.js (loaded from www/vendor, fetched by build.sh at CI time) and only the pages near the screen are drawn. If pdf.js is not in the build the viewer says so and offers Restore to phone instead of a blank page
- Not changed: Java, encryption, vault format. Old .doc / .xls / .ppt (binary) are still not readable, only .docx / .xlsx / .pptx
- Untested on device. Layout and loading logic tested in desktop Chromium only; pdf.js itself is not tested here
## 0.6.23
- New: Recycle bin. Deleting a file or note (select bar, the viewer, the note editor) now moves it to the bin instead of erasing it. It disappears from every list at once, and can be restored for 30 days; after that it is removed for good. Open the bin with the trash button in the row under the title (a purple number shows how many are waiting) or Settings > Vault > Recycle bin. Restore, Delete (for good), Restore all and Empty bin are there. The bin mark lives inside the file's sealed record, so nothing new is readable from outside.
- New: Refresh button next to it. Re-reads the vault and shows how many items it found.
- Unchanged on purpose: deleting a folder (files inside move out, as before) and the Duplicate scanner (still removes extra copies for good, after its own confirmation).
- Untested on device. Java not touched.
## 0.6.22
- Fix: leaving the app no longer locks it early. The in-app idle timer used to keep running while the app was in the background and locked it after the idle seconds. Now leaving the app locks only when the away minutes run out (0 = never lock while away). Idle seconds only count while you are in the app
- Fix: slow start (the wait on entering). The one-time copy of an old WebView vault no longer blocks the lock screen or the list. It runs in the background, one record at a time, verified, resumable, and records not yet moved are read from the old place meanwhile
- Fix: thumbnails. Photo thumbnails are made 2 at a time, only for cards on screen, and saved encrypted so they load instantly next time. Idle warm-up makes the rest. This also stops imports freezing, because thumbnails no longer flood the storage bridge
- Fix: cleanup at start never deletes anything written in the last 10 minutes (native + JS guard), so it cannot remove a file that is still being saved
- New: Duplicate scanner (Settings > Vault). Compares content by SHA-256, keeps the oldest copy, never removes every copy of a file
- New: Background re-encryption (Settings > Vault). Files added from now on are re-sealed with fresh nonces while idle, read back and verified, original restored on any failure. "All" does the existing files too
- New: tribute is 14 words per box with Decipher (default), Typewriter or Flashing effect (Settings > Tribute)
- New: small thumbnails in the Hidden files list
- New: "Opening your vault" shows at once on landing; items that cannot be read are reported instead of silently missing
## 0.6.21
- Fix (the big one): the vault is now its own storage. Every encrypted file, chunk and note is saved in the app's private internal storage (the new native BojackStore plugin), not in the WebView's IndexedDB. Each save goes to a temp file, is flushed to disk and then renamed into place, so a crash, a freeze or a forced restart cannot lose a saved file or leave a half-written one. Import something, come back later, it is still there. The app's Data size in Android settings now grows with the vault (System info shows Vault storage). Needs a fresh CI build
- An older vault that sits in WebView storage is copied across once at the first start of this version, checked file by file, and only then removed from WebView storage
- Pieces of an upload that never finished are cleaned up at start
- Move, hide and rename now rewrite only the small record, not the whole file
## 0.6.20
- Change: leaving the app no longer locks it straight away. It locks only when the time limit is up (the idle seconds or the away minutes, whichever is shorter), also when you come back after that time
- Change: tribute is now 8 boxes of up to 7 words, typed in Settings > Tribute. The Juice WRLD name is gone, you type the whole line. Filled boxes show in red, flashing and glowing, along the top of the login screen from the left edge and slide under REC. They rotate every 6 seconds and at each login. Boxes save as you type and stay on the phone until you clear them. Old tribute lines are carried into the boxes (first 8, cut to 7 words)
- New: permissions are asked once right after the first unlock: notifications, files, All files access, then battery (background uploads)
- New: Copy in the select bar makes an encrypted copy in a folder you pick. Move now reports moved/failed per file and keeps the vault awake while it runs
- Fix: folders are greyed out and not tappable while you select files; selected items show a tick
- Change: the background service starts when the file picker opens, so Android keeps the app alive while you choose files. Uploads retry each failed file once. CPU wake lock safety limit raised from 1 to 6 hours (needs a fresh build)
- Fix (the big one): uploads now use a native Android file picker and native chunked reads instead of the WebView file input, which was the slow, freezing and app-killing part. Files are read straight from the phone in 2 MB pieces on background threads, so 700 files no longer go through the WebView. Needs a fresh CI build (BojackKeepAlive.java gained pickFiles, readChunk, closeFiles, deleteUris); an old build falls back to the old picker
- Fix: the Move option in the upload sheet now deletes the originals after they are safely in the vault (Android may ask the provider; any it cannot delete are counted and reported)
- Untested on device. Java not compiled locally
## 0.6.19
- New: Settings > Tribute lines. Type your own lines on the phone (one per row, optional "line | Song title"). They feed the red flashing line on the lock screen, stored only on the device, so no rebuild is needed to change them
## 0.6.18
- New: the REC panel shows a red tribute line ("Juice WRLD – your line") that fades on and off slowly, one fixed-width line, different one each login. Lines live in `JW_LINES` in index.html and are written by the owner; empty list shows "Juice WRLD – 999 Forever". Over 40 characters is shortened at a word. No flashing when the phone has reduced motion on
## 0.6.17
- New: the paw watermark now also shows behind the photo viewer, video viewer, document reader, music player, Notes editor and every info page (About, Privacy, System info, Vault statistics). Before, those full-screen pages covered it
## 0.6.16
- New: every info page (About, Privacy, System info, Vault statistics and the rest) starts with the paw logo as a badge with "Strategy Over Impulse" under it
- New: About now covers open source (MIT), who Bojack really is, the builder as a self-taught ethical hacker and developer, awareness, and the multi-AI workflow
- New: System info adds app version, screen, CPU cores, WebView engine, language and time zone, network state, app storage used and quota, persistent storage, encryption engine
- New: Vault statistics adds original size, folders, hidden files, per-section counts and sizes, average and largest file, first and latest added, unlock method, auto-lock and sync state
## 0.6.15
- Fix (the big one): files over 8 MB are now encrypted and stored in 4 MB pieces, read from the phone a piece at a time. Before, each file was loaded whole, encrypted whole and copied again to storage (3-4x its size in RAM), which killed the app on a phone with little memory and dropped you at the login screen. Memory use is now a few MB no matter how big the file. The vault is still encrypted, once, inside the vault; nothing is encrypted twice
- Faster Cancel: stops within one 4 MB piece, and the half-written file is removed
- Raised the file size limit from 500 MB to 2 GB. Very large videos can still be too big to play back in memory
- Sync/backup/restore understand chunked files (very large ones are still built in memory there)
- Native: the background service no longer leaves Android waiting for a notification if starting it fails (that could crash the app)
- Uploads still use the wake lock, the foreground service and the notification/All files permissions from 0.6.6
## 0.6.14
- New: tap REC (top right of the lock screen) for a "Did you know?" privacy and security fact. A different one shows every time the lock screen appears, with "Rotates each login" underneath. Tap anywhere to close it
- Fix: the biometrics button on the lock screen was hidden whenever the plugin was not detected or in password+biometric mode. It now always shows on an existing vault; if the plugin is missing it says so and points to the self-test
## 0.6.13
- New: Settings > System info > Run self-test. Checks, on the phone itself: full-screen overlays, encryption, vault storage speed, the native plugin version, All files access, background service, notifications, writing to Documents, biometrics, playable video/audio formats, and PDF support. Copy report gives one block of text to send for debugging
## 0.6.12
- Safety: video thumbnails could overload a phone (decrypting and decoding many videos at once while an upload was also running). Now: they wait until no upload is running, one at a time, only videos up to 30 MB are decoded after the fact (up to 100 MB at import, streamed from the file), and Settings > Video thumbnails turns the feature off completely
## 0.6.11
- Fix: uploads were slow. 0.6.10 waited for a video thumbnail inside the upload loop (up to 5 s per video that the phone cannot decode). Thumbnails now build in the background after the files are saved
- Fix: with Sync on, each file was copied to the sync folder (base64 over the Android bridge) before the next one could start. All files are now saved first, then the sync copies are written
- Faster: 4 files at a time when they are all under 20 MB (was 3)
- New: Settings > Permissions > Background uploads opens Android's battery screen. Set Bojack Secures to Don't optimize so MagicOS stops killing it when you leave
- New: Recent errors logs how long each import took
## 0.6.10
- Fix: the viewer, Details page, notes and music player were rendering as normal page content instead of full-screen overlays (a CSS rule added in 0.6.2 overrode their fixed positioning). That is why videos had no player, the i button on a thumbnail did nothing and nothing opened. They are full-screen overlays again
- Fix: leaving the app for longer than the away timer (5 min default) while an upload was running locked the vault and cut the upload. The lock is now held until the upload finishes, then applied
- New: video thumbnails (first frame). Made during import and, for older videos up to 100 MB, the first time they are shown. Stored encrypted
- New: the lock screen always shows a biometrics button. If biometrics are not on yet, it turns them on right after your next successful password unlock
- Fix: Export, Backup and Sync need Android "All files access" on Android 11+. The app now checks it, explains it, and opens that exact setting (needs the new native build). Export errors now show the real reason and go to Recent errors
- New: if an upload is cut off by the app restarting, Recent errors says so. Foreground-service failures are logged too
- Native: WebView renderer priority kept high while the app is in the background; BojackKeepAlive gained hasAllFiles() and openSettings({pkg:true})
- Not changed: files are still sealed whole in memory (500 MB cap). A very large video can still run the WebView out of memory and restart the app; chunked encryption is the real fix
## 0.6.9
- Fix: Airplane mode, Block apps > Open Android app settings, and Feedback (email, WhatsApp) used browser-style links that Android's WebView cannot open. They could do nothing or take the WebView off the app. They now go through the native BojackKeepAlive plugin (openSettings, openUrl). Needs a fresh CI build
- Fix: opening Settings no longer re-opens Settings after the next unlock, so a bad Settings screen can no longer lock you into a restart loop
- Fix: removed the blur filter on the full-screen paw watermark, which stacked a GPU blur layer under every overlay on low-end phones
- Fix: biometric and permission prompts have timeouts (8 s checks, 60 s for prompts you must answer), so a hung system dialog ends with a message instead of leaving the app waiting
- Fix: Settings keeps its scroll position when a toggle redraws it
- New: Settings sections (Unlock, Vault, Auto-lock, Privacy, Backup, Permissions, Sync, Device tools, About, Danger zone) with neon-purple headers
- New: error log of the last 20 failures, shown as Recent errors under System info
## 0.6.8
- Fix: Move, Hide, Import and Export kicked you to the lock screen or froze the phone. The file list reload pulled every encrypted file into memory at once, which crashed the WebView on a big vault. It now reads only the small metadata, one row at a time
- Fix: Move and Hide update the list in place instead of reloading the whole vault
- Fix: Export writes big files in 3 MB slices instead of one huge string through the native bridge
## 0.6.7
- Fix: CI icon step failed on npm 12 / Node 24 (@capacitor/assets reported "up to date" but was never placed in node_modules). build.sh now installs it into an isolated tools directory, verifies it, resolves the CLI from its package.json bin field, and prints full diagnostics on failure. No app changes
## 0.6.6
- New: background securing. A small native foreground service (with a low-priority "Bojack is securing your files" notification) keeps the app and CPU alive while an upload runs and you switch apps or turn the screen off. Needs a fresh CI build (adds the BojackKeepAlive plugin via scripts/android-native)
- New: real wake lock during uploads (native FLAG_KEEP_SCREEN_ON), plus the web Wake Lock where the WebView has it, re-acquired when you come back to the app
- New: live speed and time left (MB/s) in the progress bar; progress is now by bytes, not by file count
- Fix: Cancel stops at the next step (before reading, before encrypting, before saving) instead of waiting for whole files, and the button shows Cancelling…
- Fix: app crash on big batches. Memory use is now budgeted (large files go one at a time, small ones up to 3 together), files over 500 MB are skipped with a message instead of crashing the WebView, storage is checked first, and a failing file no longer stops the rest
- Fix: opening Settings could break its own buttons because the Hidden files row was missing from the page. The row is there now
- New: Hidden files shortcut (eye icon, top right) as well as Settings → Hidden files; Unhide all
- Fix: Export now writes decrypted copies to Documents/BojackSecures/Exported on Android (the old download link did nothing in the app), with a warning first and unique names
- Fix: long press no longer swallows the next tap, ignores scrolling, and does not trigger Android's text-select menu; new All button in the Select bar
## 0.6.5
- New: keep-awake screen wake lock during file imports, with safe release when the job finishes.
- New: upload progress has a Cancel button; cancellation stops scheduling new files and lets in-flight files finish safely.
- New: long-press a vault item to enter selection mode; bulk Select actions now include Export and Hide.
- New: hidden files are excluded from normal browsing and can be unhidden from Settings → Hidden files.
- Safety: exported selected files are decrypted copies; the app warns before writing them outside the vault.
- Note: Android may suspend WebView work when the app is backgrounded; guaranteed long-running uploads require a native foreground service and notification permission.
## 0.6.4
- New: eye button on the password fields to show or hide what you type (hides again on unlock/lock)
- Changed: no scroll-to-top arrow inside Settings; the list scrolls with a visible purple scroll bar (also on the main lists)
- New: tap the paw on the lock screen (only there, not inside the app) and a banner drops from the top: Strategy Over Impulse · Bojack Secures · Privacy First
- New: the app reopens where you left off after unlocking (section, folder, Settings screen and scroll position). Only the position is remembered, never any file content
- New: lock screen greeting (Good morning / afternoon / evening / night) with a privacy quote in street-smart slang, changing every hour
- Changed: the background watermark is now one big transparent paw (replaces the retriever)
- New: swipe up on the bottom dock (or tap the grip bar) for a two-sentence note on why privacy matters and why this app was built; changes every hour
## 0.6.3
- Fix: the paw app icon never reached CI-built APKs because CI did not run icon generation; `ci-prepare` now generates the launcher icon and splash
- New: third unlock mode, Password + biometric (Settings): right password, then a fingerprint/face check too. Biometric-only button is hidden while it is on
- New: Vault statistics page (total encrypted size, number of files, notes, last backup export time)
- New: flashing REC dot top right of the lock screen
- New: "Press Enter ↵ to unlock" hint fades in under the password field while typing
- New: black file-size pill bottom-right on Grid tiles
- New: Secure Note type picker uses neon purple radio rings
- New: faint folder/document icon in empty states
- New: scroll-to-top arrow above the + button
- New: haptic feedback (Settings toggle, on by default): unlock, wrong password, lock, errors
- New: toast icons (check, cross, warning, info); errors show red
- Details > Compute SHA-256 already existed since 0.6.0, unchanged
## 0.6.2
- Fix: selecting several files at once could still kick you back to the password screen even after the 0.6.1 fix. The deferred-lock window that protects an open file picker only held for 15 seconds; a large or multi-file pick can take Android longer than that to resolve every file's URI and hand control back, so the idle/away lock could fire and wipe the key before the import ever started. That window is now 90 seconds; the import itself still clears it the instant it actually begins, so a quick single-file pick behaves exactly as before
- New app icon: a paw print with claws, replacing the lock-and-key mark, everywhere (app icon, splash, lock screen, toolbar)
- New: a low-opacity Golden Retriever watermark (Bojack) behind the app's screens
- New: a flashing red "REC" indicator in the toolbar while an import, share-encrypt, backup, or sync job is running
- New: crop tool in the photo viewer — drag a resizable box over the image, Crop & save re-encrypts the cropped image back into the same vault item (same id, so Share/Details/EXIF/sync/backup all see the new image automatically)
- New: taller toolbar showing the "Strategy Over Impulse" slogan under the title; the paw mark is now a button that expands an in-toolbar About + Privacy panel in place, no page navigation needed
- New: a short privacy disclaimer at the bottom of the lock screen, with a direct link to the full Privacy page (readable before unlocking)
- New: the Android hardware/gesture back button now closes whichever sheet, viewer, page or panel is open (innermost first), then steps out of a folder or selection mode, then minimizes the app, instead of doing nothing or exiting unexpectedly
## 0.6.1
- Fix: adding files kicked you back to the password screen. The Android file picker and permission dialogs backgrounded the app, which fired the background auto-lock and wiped the key mid-upload. Locks are now deferred while the picker is open or an upload/restore is running, then applied as soon as it is safe (an open picker still re-locks after the away timer)
- Notification and file-access permissions are now requested when the app first opens (before the password screen), not on the first upload
- Faster uploads: up to 3 files encrypted and stored in parallel (1 at a time when a file is over 40 MB), vault sync no longer re-reads each item or rewrites the meta file per file, progress notifications throttled, vault list decrypts in parallel, restore writes in one transaction
## 0.6.0
- Layouts: List, Grid, Tiles and Detailed, switchable per section (remembered); coloured file-type badges; thumbnails for photos
- Detailed layout shows per item: name, extension, MIME type, size in bytes, encrypted size, added date, original modified date, folder, section, pixel size (photos) and ID
- Details sheet (ⓘ on any item, or Details in the viewer): all of the above plus SHA-256, EXIF/GPS/camera for photos, duration/resolution/bitrate for audio and video, characters/words/lines for text, rows/columns for CSV, approximate page count for PDF
- Share with a password: the item is encrypted (PBKDF2-SHA256 900k, HKDF, AES-256-GCM) into one self-decrypting HTML page. The receiver needs only a browser, no app, and must type the same password. Name, type and message are inside the encryption. Sent through the Android share sheet (WhatsApp etc.) or saved to Documents/BojackSecures/Shared. Includes a strong-password generator, strength meter, optional message, SHA-256 integrity check on open, and a retry delay on wrong passwords
- Items added from V0.6.0 record the original modified date (older items show "not recorded")
- Branding: gradient header with lock mark, glow cards
- GitLab: CI now generates the Android project itself (no committed android/ needed), caches npm and Gradle, checks required variables up front, writes a SHA-256 for the APK and attaches it to releases
- New: scripts/check-js.js (parse + version + template checks), scripts/test-share.js (share-format round-trip and tamper tests), scripts/patch-android.sh (shared by build.sh and CI, also sets versionName/versionCode from VER)
- build.sh: new `check` and `ci-prepare` commands; @capacitor/share added to dependencies
## 0.5.0
- System info page (Settings): RAM/memory, storage, battery, device/OS, and how long the app session has been open; uses @capacitor/device, falls back to browser-reported values outside the Android app
- Block apps (Settings): a self-maintained in-vault focus list plus a button to Android's own app settings screen — a rootless app with no Device Admin/Accessibility Service cannot force-close or truly block another app, so this does not claim to
- Airplane mode (Settings): deep-links to Android's Airplane mode settings screen; Android has blocked silent toggling without root since 5.0, so this opens the real screen instead of pretending to flip it
- Reset now explicitly warns, with a confirm step, that wiping the vault also deletes the Syncthing sync folder and can propagate the wipe to other devices if Syncthing two-way-syncs that folder
- Feedback: WhatsApp (067 134 6413) alongside the existing email option
- Stronger key derivation: PBKDF2-SHA256 raised from 600,000 to 900,000 rounds, then passed through HKDF-SHA256 for domain separation; vaults made before 0.5.0 keep using their original key for the session (nothing is silently re-encrypted) and new vaults get the stronger derivation
- "Away" auto-lock: a separate, user-settable timer (default 5 minutes, 0 = off) that shows a blocking red "Strategy Over Impulse" lock screen when the app has been left in the background past that time, on top of the existing seconds-based idle auto-lock
- Viewer: CSV and the first sheet of .xlsx now render as a table; .docx and .pptx get a read-only text/slide preview (binary .doc/.ppt are not supported); plain text/.sh/.json/.md/.log files are now editable in place with a Save button instead of read-only; .html files render sandboxed
## 0.4.0
- Light mode toggle
- Folders for every section (names and membership encrypted), select mode with bulk move and delete
- Photo gallery viewer: swipe, double-tap zoom, info panel with EXIF (camera, exposure, ISO, focal length, date, GPS)
- Music player: queue, shuffle, repeat, speed, mini and full player, background playback, lock-screen controls
- Video player: previous/next, auto-advance, speed
## 0.3.0
- Secure notes, About, Privacy, Feedback, screenshot toggle, auto-delete of restored files, backup file, Reset
## 0.2.0
- Renamed to Bojack Secures, slogan, notifications with copy progress, file access, Syncthing sync folder
## 0.1.0
- First release: AES-256-GCM vault, password and biometric unlock, zero-trust auto-lock
