# HANDOVER: Bojack Secures V0.6.32
Stack: single-file www/index.html (vanilla JS, Web Crypto, IndexedDB) wrapped with Capacitor Android. Plugins: @capacitor/filesystem, @capgo/capacitor-native-biometric, @capacitor/local-notifications, @capacitor-community/privacy-screen, @capacitor/app, @capacitor/device, @capacitor/share (@capacitor/share added in V0.6.0 — run `npm i @capacitor/share && npx cap sync android` on an existing project; app and device were added in V0.5.0 for settings deep-links and system info — run `./build.sh init` again, or `npm i @capacitor/app @capacitor/device && npx cap sync android`, to pick them up).
Package: com.fezzy.vault. Build via build.sh and .gitlab-ci.yml (unsigned release -> zipalign -> apksigner).
Rules: surgical edits only, no unapproved changes, keep black (#000) + neon purple (#b026ff).
## V0.6.35 changes
- PrivBrowser.java: new `@JavascriptInterface savePhone(name)` copies filesDir/browser-inbox/<name> to the public Downloads folder (MediaStore.Downloads on API 29+, Environment public Downloads + MediaScanner below 29). Runs on the executor, reports `{t:'pub',state,name,saved|err}` through emit().
- www/index.html: setting `pub` (default 1) in DEF and SETS > Downloads; `phoneSave()` and `dlAfter()`; ev() handles `pub`; on a finished download with `pub` on, the copy runs first and sealing (`dlAfter`) continues when the `pub` event arrives, success or failure. Buttons `phone` / `phoneall` in dlsPanel copy only (ST.pubMan marks manual copies so they do not seal).
- Not run on a phone or in a pipeline.
## V0.6.34 changes
- IndexedDB (www/index.html): `dbp` (one connection opened once, kept forever) is replaced by `dbOpen()` / `dbTx()`. The connection is opened on demand, nothing runs before `onsuccess`, `onclose` / `onversionchange` clear it, and a transaction that hits "connection is closing" reopens and retries (max 3). `idbTx` and `idbRows` use it; the loader's copy of the cursor code now calls `idbRows()`.
- scripts/patch-android.sh: reads the package from build.gradle (must be com.fezzy.vault), copies scripts/android-native/*.java there, cmp-checks every file, deletes duplicate copies in other folders, exits 1 on any problem.
- scripts/verify-build.sh: APK check looks for exact dex descriptors (`Lcom/fezzy/vault/<Class>;`) for MainActivity, BojackKeepAlive, BojackStore, BojackJs, KeepAliveService, PrivBrowser; fails with no classes*.dex.
- BojackStoreJs.java + `window.BojackST`: direct (non-Capacitor) vault storage, same files as BojackStore; `ST()` in index.html picks the Capacitor plugin if registered, else this bridge. MainActivity adds it after BojackJs.
- MainActivity.java registration unchanged: registerPlugin(BojackKeepAlive/BojackStore) already runs before super.onCreate(). PrivBrowser is a plain @JavascriptInterface (window.BojackPB), not a Capacitor Plugin, so registerPlugin() cannot take it.
- Not run on a phone or in a real pipeline.

## V0.6.33 changes
- Browser rebuilt from nothing. `scripts/android-native/PrivBrowser.java` (WebViews, ad/tracker host block in `shouldInterceptRequest`, HTTPS upgrade, DNT/GPC main-frame headers, privacy JS in `onPageStarted`, private inbox downloads incl. blob: via sync XHR slices, `window.BojackPB` @JavascriptInterface, events back through `window.PB.ev`). Web pages never get a Java object. `MainActivity` adds the bridge after BojackJs and destroys it in onDestroy.
- index.html: header `#webBtn`, overlay `#pbr` (markup before the script), `<style id="pbCss">`, one IIFE at the very end of the script wrapped in try/catch (nothing after it can be stopped by a browser error). Hooks: `handleBack` first line, `lock()` first statement (closes the browser), `showApp` (5 s later `PB.sweep()`). The native pane is laid over `#pbHole` (rect sent in CSS px, Java converts with density) and hidden whenever an HTML panel is open.
- Vault import reuse: `PFile` mimics `NFile` (name, size, type, slice().arrayBuffer()) and reads from `BojackPB.readChunk`; `runImport` is awaited with `#mode` forced to copy, then `all.doneF` inbox files are deleted.
- Idle lock: `bump()` is called every 15 s while the browser is open, but only for 10 minutes after the last tap or page event.
- Checked here: `check-js.js`, `bash -n`, and PrivBrowser/MainActivity/BojackJs compiled with javac against hand-made Android stubs (types only; not a real SDK build). Not run on a phone.
## V0.6.32 changes
- The built-in browser is removed completely. Bojack Secures has no browser, no in-app web view, no download inbox and no download list. Deleted: `WebSheet.java`, `BrowserExt.java`, `BojackBrowser.java`, `BjDownload.java`, `BjBlob.java`. In index.html: the top-bar globe button (`#webBtn`), the bottom-nav Browser tab (`#navWeb`), `BB()`, `importInbox()` and its listeners, `openBrowser`, the browser close call in `lock()`, the `browser` entry in `NEED_MIN`, and the browser row in `nativeParts()`. `androidx.webkit` is no longer a dependency.
- `scripts/patch-android.sh` now also deletes the old browser classes and the `androidx.webkit` line from an android/ project left over from an older build. `scripts/verify-build.sh` now FAILS if any browser class or `androidx.webkit` is found in the android project or the APK.
- `BojackJs.java` keeps only `status()` (window.BojackNative.status(), shown in Settings > System info). `MainActivity` registers BojackKeepAlive and BojackStore only.
- Not touched: the vault, encryption, storage format, import, viewer, sharing, Settings, README.md. `openExt()` / `BojackKeepAlive.openUrl` stay: they only hand a mailto: / WhatsApp link to another Android app (Feedback).
- Leftover on phones that ran V0.6.25-V0.6.31: the old `filesDir/inbox` folder, `dl-log.json` and the browser's own preferences stay in the app's private storage. The new build never reads or deletes them.

## V0.6.31 changes
- `scripts/android-native/BojackJs.java` (window.BojackNative, @JavascriptInterface). `MainActivity` registers plugins one by one, keeps `regLog`, adds the bridge after super.onCreate. System info shows `BojackNative.status()`. (The browser parts of this change were removed in V0.6.32.)
## V0.6.30 changes
- index.html, block before `openExt`: `apkInfo()` (App.getInfo -> real versionName/build), `nativeParts()` (BojackKeepAlive methods, BojackStore present), `buildReport()` verdict ok | old | hollow | unknown | web, `buildText(r,what)`, `needBuild(what)` (page 'Needs a newer APK', 4 s debounce), `buildNotice()` (once per launch, called 3.5 s after showApp). `NEED_MIN` maps feature -> first version that has it. Helpers are function declarations / var on purpose (start-up TDZ lesson from the Festers hotfix)
- Replaced: both 'Needs the new build (V0.6.9 native code)' toasts. `KA()` is unchanged on purpose
- `scripts/verify-build.sh project|apk`, called from .gitlab-ci.yml after ci-prepare and after apksigner. Dex check is a plain grep of `com/fezzy/vault/<Class>` over classes*.dex: it assumes minify is off (Capacitor default). If R8 is ever turned on, keep rules are needed first
- README.md was empty, now filled. Java untouched
- Untested on device and in a real pipeline

## V0.6.24 changes
Loading bar + instant list ('idx' sealed record in the meta store, `idxLoad`/`idxSave`, `reload({bg,onFirst,onDone})`, `legacyRows`), viewer layout/errors (`#vbody.doc`, `view` wraps `view0`), PDF via `www/vendor/pdf.min.js` (`showPdf`, `build.sh` `vendor_pdfjs`). See CHANGELOG 0.6.24.

## V0.6.23 changes
- Recycle bin: soft delete. `toBin(list)` sets `trash:<ms>` through `setMeta` (small sealed record only). `reload()` splits rows into `items` (live) and `trashed`; every existing view keeps reading `items` only. `hardDel(ids)` is the old delete path (row, chunks, `vt:`/`th:` cache, unmirror). `binPage()`, `binBadge()`, `binPurgeOld()` (BIN_DAYS=30, runs after each reload while not `busy`). `delSel`, `#vdel`, `#nDel` now call `toBin`. `delFolder` and the duplicate scanner still hard-delete
- Refresh: `#refBtn` runs `reload()` with `busy++` so the vault does not lock mid-read
- Untested on device. Not changed: Java, share template
## V0.6.15 changes
- IndexedDB v2: new `chunks` store (key `<id>:<i>`, value {iv,ct}). Files > CHUNK_MIN (8 MB) are stored as a row `{id,cat,n,csz,miv,meta}` with no `ct`; chunks sealed with AAD `c:<id>:<i>:<n>` (`sealChunked`/`openChunked`/`dropChunks`). `blobOf` handles both formats; legacy rows unchanged. Edit paths (text save, crop) convert a chunked row back to a single record. `rec()` v2 format carries `chunks[]`; `importBjv` restores them
- Untested on device. Not covered: setMeta on legacy rows still reads the whole row; playback of very large videos builds the whole Blob in memory
## V0.6.22 changes
- Lock: `bump()` callback returns when `document.hidden`; `armBgLock()` uses only `awaylock` minutes; the visible branch no longer applies `idleUp`, it calls `bump()`
- Storage: `nvReady` is now resolved at once. `migrateIdb()` is resumable and non-blocking (started 2.5 s after showApp): per record copy, verify, delete from IDB. `legacy()` = flag `nvMig` not set; `nGet/nHeadOf/nDel/nativeRows` fall back to / merge IndexedDB while legacy. `sCleanup` runs 15 s after unlock only when not legacy and not migrating; Java skips files younger than 10 min
- Thumbs: `thPush` queue (2 at a time), sealed cache `th:<id>` in meta (AAD `th:<id>`), `lazyRun` + IntersectionObserver in `cardEl` and Hidden page, `thEnsure` warm-up from `bgTick`
- `dupScan()` + `fpOf()` (SHA-256 of 4 MB piece hashes), cache `fp:all` sealed in meta; `bgTick()` + `resealItem()` (per chunk / per record, read-back verify, rollback), flags `rseal`, `rsAll`, `rsd` (id prefixes done), `rsBase` (first run time; older files only with All)
- Tribute: `TB_W=14`, `TB_MS=9000`, `tbAnimate()` effects, localStorage `tbfx`
- Untested on device. Java changed only in `sCleanup` (not compiled here). Not done: a foreground service while unlocked in background (Android may still kill the app on MagicOS)
## V0.6.21 changes
- Storage layer: `tx()` now routes to native `BojackStore` (scripts/android-native/BojackStore.java, registered in MainActivity) when `ST()` is available, else `idbTx` (IndexedDB). Records are packed by `nPack/nUnpack` and moved in 1 MB slices (`nWrite/nRead`); `files` rows are split into head `<id>.m` (id,cat,n,csz,miv,meta,ctLen,hd) and body `<id>.d` (iv,ct); `setMeta` uses `getHead/putHead`. `reload()` uses `sMetaAll` (one call). One-time `migrateIdb()` (flag `nvMig`, error in `nvMigErr`). Files on disk are hex(key) names under filesDir/vault/{meta,files,chunks}; atomic write = .tmp + fsync + rename; `sCleanup` at boot removes orphans
- Untested on device; Java compiled only against stubs
## V0.6.20 changes
- `armBgLock()` + `bgT`: hidden no longer calls lock(); a timer (min of autolock s / awaylock min) and an elapsed check in the visible branch (`gone`, `idleUp`) do it. `holdLock()` still defers
- Tribute boxes: `TB_N=8`, `TB_W=7`, `TB_MS=6000`, localStorage `jwBoxes` (JSON array), migrated once from `jwUser`; `#lockTrib/#tribTxt` fixed top-left under `#lockRec` (black pill). `nextLyric(stay)` name kept. No tribute text in code; keep it that way
- `startupPerms()` (called from showApp, flag `permsStart=3`), `permBatt()`, `permsNext` resumes the battery step after the All-files screen
- `copySel()` (decrypt, reseal under new id; chunk AAD is bound to the id so rows cannot be copied raw), `moveSel()` per-file try/catch, `setMeta` mirror failure is non-fatal
- `#main.selmode .fol` greys folders; `sPick` starts keepAlive with the picker, `endPick` stops it if no upload started; failed files retry once (`tried`, `retryLater`)
- KeepAliveService cpuLock 6 h. Untested on device; Java not compiled
- Native import path: `NP()`, `NFile` (File look-alike over `readChunk`), `nativePick()`, `runImport(all)` (the old onchange body), `deleteUris` on Move. Positional reads via FileChannel on a cached ParcelFileDescriptor, fallback to stream+skip. `vtFromFile` only for real Blobs
## V0.6.19 changes
- `tributePage()` / `userTribute()` (localStorage `jwUser`) override `JW_LINES`; row `#sTrib` in Settings. Content is typed by the owner on the device and never enters the repo
## V0.6.18 changes
- `JW_LINES` (+`nextLyric()`, called from `nextFact()`) feeds `#recLyric`. The owner writes and maintains the lines; the assistant does not author or edit lyric text. Keep it that way
## V0.6.17 changes
- Watermark on overlays is a `::before` (data-URI paw, z-index -1) on `#viewer,#mplayer,#page,#note`; do not give those elements children with their own position/z-index that cover it. Verified in headless Chromium with a screenshot pixel check
## V0.6.16 changes
- `PAWBADGE` is prepended inside `page()`, so every info page gets it; `aboutHtml()` rewritten; `sysInfoExtra()` appended to `sysInfoRows()` in `sysInfoPage()`; `statsPage()` is async and longer
## V0.6.14 changes
- `FACTS`/`nextFact()`/`toggleFact()` (after `lockFill()`): REC badge `#lockRec` opens `#recFact`; index in localStorage `factIdx`, advanced from `lock()` so it changes each login
- `#bio` hidden only when `fresh`; `NB()` missing is reported on tap instead of hiding the button
- Still open (reported on 0.6.13, not changed): import crash/return-to-login, slow or frozen upload, no thumbnails/previews on device, i button, Cancel feel. Root cause needs the self-test report from the phone. `setMeta()` reads and rewrites the whole encrypted row (file included) just to change meta, a likely OOM for Move on big videos
## V0.6.13 changes
- `selfTest()` (System info > Run self-test): PASS/FAIL/INFO list, `busy++` during the run so the vault does not lock, Copy report. Read this first when something fails on a device
## V0.6.12 changes
- Thumbnail safety: `vtWait()` holds work while `busy`; `VT_LAZY` 30 MB; `vthumb` pref (Settings > Video thumbnails). A phone froze for 5 min on a previous build; cause unconfirmed, thumbnails were the newest heavy work
## V0.6.11 changes
- Import loop: no thumbnail work inline (`vtFromFile` queues it on `vtQ`); sync mirroring moved after all rows are saved (`syncIds`); concurrency 4 for small files; `import:` timing line in errlog; Settings row `#sBatt` deep-links IGNORE_BATTERY_OPTIMIZATION_SETTINGS
## V0.6.10 changes
- CSS: `#app,#viewer,#page,#note,#mplayer{position:relative}` (V0.6.2) overrode the fixed overlays. Now only `#app` is relative; do not re-add the others
- `lockAfterWork()` + `awayPending`: away/idle lock is deferred while `picking||busy`, applied when work ends
- `frameOf/vtSave/vtLoad/vthumb`: video first-frame thumbs, sealed in `meta` store key `vt:<id>` (AAD `vt:<id>`), made at import from the File and lazily (<=100 MB) otherwise; removed in `delSel`
- `offerBio(pw)` + `bioOffer` flag: lock-screen biometrics button sets it up after the next password unlock
- `allFilesOK()/needAllFiles()` use native `hasAllFiles()`; `openSettings({action,pkg:true})` opens the per-app All files access page. Export/backup/sync check it first
- `impRun` localStorage breadcrumb: if set at boot, the last upload was cut off (logged in Recent errors)
- Untested on device: all of the above. Java not compiled locally. Verified in headless Chromium: overlay positions, import, viewer, details, video thumbnail path with a generated WebM
## V0.6.9 changes
- Native: BojackKeepAlive gained `openSettings({action})` (android.settings.* only, falls back to ACTION_SETTINGS) and `openUrl({url})` (mailto/http/https via ACTION_VIEW). JS: `openAndroidSettings()` and `openExt()` call them; `picking` is set first so the vault does not lock while you are away. Without the new build they toast "Needs the new build"
- `saveLast(false)` from the gear and `showApp()` ignores `last.set`: no auto-resume into Settings. Gear re-render keeps `keepSc` scroll
- `withTO()` wraps biometric/permission/native calls; `errLog()` ring buffer (localStorage `errlog`, 20 entries) feeds System info > Recent errors
- `.shd` section headers in Settings; `#wmark` blur filter removed
- Untested on device: openSettings on MagicOS (AIRPLANE_MODE_SETTINGS may be refused, falls back to main Settings), whether removing the blur ends the freezes. The Java has not been compiled locally. If Settings still freezes, read System info > Recent errors right after restart and bisect by turning rows off
## V0.6.8 changes
- `reload()` uses an IndexedDB cursor (metadata only) instead of getAll(); `setMeta()` patches the in-memory item; `fsWriteBlob()` writes export files in 3 MB slices (writeFile then appendFile). Still not done: import seals whole files in memory, sync mirror (`rec`/`mirrorRow`) still base64s a whole file
## V0.6.7 changes
- build.sh `gen_assets` installs @capacitor/assets into `${TMPDIR:-/tmp}/bojack-assets-tools` (isolated) instead of `npm i -D` in the app; app code unchanged
## V0.6.6 changes
- Native: `scripts/android-native/{BojackKeepAlive,KeepAliveService,MainActivity}.java` are copied into the generated android/ project by `scripts/patch-android.sh`, which also adds FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, WAKE_LOCK and the `<service ... foregroundServiceType="dataSync">` manifest entry. MainActivity is overwritten to `registerPlugin(BojackKeepAlive.class)`. JS side: `KA()`, `keepAliveOn/Text/Off`, called only from the `#file` import handler
- Import pipeline: byte-budgeted (MEMBUDGET 160 MB, BIGFILE 500 MB skip), cancel checkpoints between read/seal/save, MB/s + ETA in `note()`. Still not chunked: whole file is sealed in memory
- `exportSelected()` writes via Filesystem to Documents/BojackSecures/Exported; `#hidBtn` + Settings `#sHidden` open `hiddenPage()`
- Untested on device: foreground service start on Android 14/15 (dataSync has a system time limit on 15), the Java has not been compiled locally, notification shown only if POST_NOTIFICATIONS granted, big-file behavior near the 500 MB cap
## V0.6.4 changes
- `#pawBanner` (lock logo + `#pawBtn`), `lockFill()` greeting + LOCKQ, `#qsheet` + DOCKQ opened by swipe-up on `#nav`/`#dockGrip`; both quote lists index by `Math.floor(Date.now()/3600000)` so they rotate on the hour. The DOCKQ "why I made this" wording is first person and generic, edit freely
- Resume: `saveLast()` (localStorage `last` = cat, fold, settings flag, scroll) called from `render()` and the gear; restored in `showApp()`. Only runs while `key` is set
- `#wmark` is now a single large paw at 7% opacity
- Untested on device: swipe-up gesture vs Android gesture nav, resume with a folder deleted elsewhere (falls back to root)
## V0.6.3 changes
- `ci_icons()` in build.sh (called by ci_prepare) runs capacitor-assets so CI APKs get the paw icon
- `bio2` localStorage flag = password+biometric mode; checked in `attempt()` after the password verifies (`bioOK` skips the second prompt when the biometric button already ran)
- `buzz()` wraps navigator.vibrate behind the `haptic` flag; `toast()` classifies by text (`toastKind`) and adds an icon, so existing toast() calls needed no edits
- `statsPage()`, `syncKind()` (custom radio rings over the hidden #nKind select), `#totop`, `.szp` size pill, `#lockRec`, `#enterHint`, EMPTY_FOLDER/EMPTY_DOC
- Untested on device: navigator.vibrate in the WebView, biometric second step, REC dot over the away (red) lock screen
## V0.6.2 changes
- Multi-file import lock bug: root cause was the picker-return grace window (`endPick`) zeroing the `picking` flag after a fixed 15s, before `onchange` had actually started on a large/multi-file selection; window is now 90s and `onchange` still clears the flag the instant it begins, closing the race
- App icon/mark: paw with claws (assets/icon.svg, icon-only.svg, splash.svg, and the inline lock-screen + toolbar SVGs in index.html) replaces the padlock+key everywhere
- `#wmark`: fixed, low-opacity (5%/8% light) Golden Retriever silhouette SVG behind `#app`/`#viewer`/`#page`/`#note`/`#mplayer` (z-index 0 under z-index 1 content)
- `#rec`/`.recdot`: shown via `progress()` (add/show/hide tied 1:1 to the existing progress bar), covers import, share-encrypt, backup export, sync export
- Crop tool: `#vcrop` button (photos only) opens a draggable/resizable `#cropBox` over the viewer `<img>`; Crop & save maps the box's screen rect to natural-pixel coordinates via `createImageBitmap(blob,sx,sy,sw,sh)`, re-encodes to the original's format (JPEG default, PNG if source was PNG/.png), and re-seals through the same row/id used by every other edit path (text Save, etc.) — Share/Details/EXIF/sync/backup need no changes
- Toolbar: `.pawbtn` (paw mark, now tappable) + `.htxt`/`.hslogan` (title + "Strategy Over Impulse") replace the old static `.hmark`; `#pawPanel` is a height-animated `<div>` built once from `aboutHtml()+privHtml()` and toggled by `togglePaw()`
- Lock screen: `#lockDisclaimer` footer with a `#lockPrivLink` that opens the existing `#page`/`privHtml()` without unlocking
- Back button: `handleBack()` closes open overlays innermost-first (`#shr`→`#pick`→`#mplayer`→`#page`→`#note`→`#sheet`→`#viewer`), then the paw panel, then Settings-back-to-list, then folder, then select mode, then minimizes via `App.minimizeApp()` (exits via `App.exitApp()` only from the lock screen); registered with `App.addListener('backButton',...)`, Capacitor-only, no manifest change needed
- Untested on device (same caveat as prior `Untested on device` entries): crop drag on real touch hardware, REC indicator legibility against the gradient header, back-button behavior across all overlay combinations, watermark rendering performance on lower-end hardware
## Known gaps / next
- Sharing (V0.6.0): the output is a self-decrypting HTML page, so the receiver only needs a browser. Whole item is encrypted in one piece and built in memory, so it is capped at 50 MB (SH_MAX). Larger items need chunked encryption. Android's share sheet is used for WhatsApp etc; the vault locks when you switch apps (by design) so unlock again after sending. Untested on device: @capacitor/share with a CACHE file URI, opening the HTML from WhatsApp on a phone with no browser handler, Documents/BojackSecures/Shared write. Cache copies are deleted 10 minutes after sharing (sweepShare)
- A weak share password can be brute-forced offline from the file; the app requires 10+ characters and offers a generator. The receiver retry delay is cosmetic (the file can be attacked outside the page)
- Share page is dark-only (brand); it needs crypto.subtle, which Chrome/Firefox provide for local files; some in-app viewers do not, and the page says so
- Details: PDF page count is approximate (regex on the raw file); audio/video info needs a codec the WebView can decode
- Move mode cannot delete originals (needs native MediaStore delete request)
- Change-password needs full re-encryption pass
- Large-file chunked encryption/streaming
- Untested on device: biometric flow, Documents export, FLAG_SECURE patch
- Block apps and Airplane mode are honest deep-links to Android's own settings screens, not real enforcement — a rootless app with no Device Admin / Accessibility Service cannot force-close another app or silently flip airplane mode (blocked since Android 5.0). If real app-blocking is wanted later, that needs an Accessibility Service plugin and a separate permission grant flow, which is a meaningfully bigger addition, not a toggle.
- System info relies on @capacitor/device's getInfo()/getBatteryInfo(); CPU model/exact total storage are not exposed to apps on Android and are labeled as such rather than guessed
- .docx/.pptx/.xlsx preview is read-only, hand-rolled (no bundler, no JSZip — ZIP entries are inflated with the browser's native DecompressionStream('deflate-raw') and OOXML text is pulled straight out of the XML). Binary legacy .doc/.ppt/.xls are not supported. xlsx preview shows only the first sheet, capped at 500 rows. Untested on device — DecompressionStream has been in Chrome/WebView for a while but confirm on-device before relying on it.
- Text file editing (Save button in the viewer) only covers plain-text-ish files (.txt/.md/.json/.log/.sh/.js/.py/text/*); it writes back through the same seal()/setMeta() path as everything else and re-mirrors to the sync folder if sync is on. .html preview is read-only and sandboxed, not editable.
## V0.6.0 changes
- Layouts (List/Grid/Tiles/Detailed, per section, key `lay:<cat>`), type badges, ⓘ Details on every item, Details also in the viewer bar
- Item meta gained `mod` (original lastModified) at import; `ctSize` is read from the stored row, not stored in meta
- Share: `/*SHARE-CORE-BEGIN*/` block in index.html (encrypt) + `www/share-template.html` (decrypt, markers `/*BJ-DECRYPT-BEGIN*/`); both are exercised by `scripts/test-share.js`. Keep the two in sync: same PBKDF2/HKDF/AAD constants
- CI generates android/ via `build.sh ci-prepare`; `scripts/patch-android.sh` is the single source for manifest and version patches
## V0.5.0 changes
- System info (Settings): RAM/memory, storage, battery, device/OS, app session uptime
- Block apps (Settings): in-vault focus list + link to Android's app settings
- Airplane mode (Settings): deep-link to the system Airplane mode screen
- Reset: explicit confirm step naming the Syncthing sync folder before wipe
- Feedback: added WhatsApp alongside email
- Key derivation: PBKDF2 600k -> 900k rounds, added HKDF-SHA256 step; legacy vaults keep their original key for the session rather than being silently re-encrypted
- Away-timer red lock screen (separate, user-settable minutes, default 5, composes with the existing seconds-based idle auto-lock)
- Viewer: CSV/xlsx table preview, docx/pptx read-only preview, in-place text editing with Save, sandboxed HTML preview
## V0.4.0 changes
- Renamed to Bojack Secures, slogan "Strategy Over Impulse" on lock screen
- Notifications permission + live "Securing n of N" progress (in-app bar and ongoing notification), completion notification
- File access permission row; first import asks for notifications + file access
- Syncthing: mirror to Documents/BojackSecures/Sync as encrypted .bjv files, Export all, Import, Restore from sync folder on a fresh install (same password)
- Untested on Android 14: write access to shared Documents may need "All files access" granted in system settings (permission is in the manifest)
## V0.4.0 changes
- Notes tab: encrypted notes (kinds: SSH key, API key, phone number, password, other); editable, masked until Reveal, Copy with best-effort clipboard clear after 30s
- Settings: Allow screenshots (privacy-screen plugin; off by default), auto-delete restored files (5/30/60 min, runs while app is open or next launch), Delete now, Backup export (.bjvault in Documents/BojackSecures/Backups) and Restore, About, Privacy, Feedback (mailto festersfezzy@gmail.com), Reset (also removes Documents/BojackSecures)
- MainActivity FLAG_SECURE patch removed from build.sh; screenshots now toggled by @capacitor-community/privacy-screen. Untested on device.
- Backup bundle is built in memory; very large vaults may fail
## V0.4.0 changes
- Folders for all sections: stored as encrypted records (cat `_folder`, meta `{name,fcat}`), files point to them with meta `folder`; select mode with bulk move/delete; folders sync and back up like files
- Photo viewer: swipe, double-tap zoom, Info panel with EXIF parsed from the first 256 KB of JPEGs (tested with generated files)
- Music player: queue per folder, shuffle, repeat, speed, mini and full player, lock-screen controls (track name hidden unless enabled), background play while music runs (vault locks when it stops)
- Video player: prev/next, auto-advance, speed; light mode toggle
- Added LICENSE (MIT), CHANGELOG, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, GitLab issue/MR templates, CODEOWNERS, full .gitlab-ci.yml (check, build, release), docs/BUILD.md, docs/RELEASING.md
- Untested on device: background audio (Android may suspend the WebView; a foreground-service plugin may be needed), lock-screen controls, light mode on all screens
