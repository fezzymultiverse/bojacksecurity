# Bojack Secures

An encrypted vault for Android (Capacitor). Files, notes and photos are sealed with AES-256-GCM and kept in the app's own private storage. Black and neon purple (#b026ff). Strategy Over Impulse.

## Which APK do I install?
The app is the web screens (`www/`) plus native Android code (`scripts/android-native/*.java`). Both are baked into **one** APK, so you cannot update the screens alone: every change needs a new APK.

1. GitLab, your project, **Build > Pipelines**. The top pipeline must say **Passed**.
2. On that row, open the download arrow (**Download artifacts**) and pick **build_apk:archive**. Unzip it.
3. Install `BojackSecures-signed.apk` over the old app (same signing key, so your vault stays).
4. Open **Settings > System info**. You want to see **Installed APK: V<this version> · matches** and every native part **present**. If so, the build is right.

The job log of `build_apk` ends with `[verify] OK (V<version>)`. If it says `FAIL`, the pipeline refused to ship an APK with missing parts; read the lines above it.

## "Needs a newer APK" (was "Needs the new build (V0.6.9 native code)")
That message means a button wants native code the installed APK does not have. From V0.6.30 the app says exactly what the phone has:
- **Installed APK is older than the feature** (for example V0.6.8 and a feature from V0.6.9): install the newest APK as above.
- **Installed APK is current but a native part is missing**: the pipeline built the app without its native files. It is not a download you forgot. Check the `build_apk` log for `[patch] 7 native Java files installed` and `[verify] OK`.

## Browser and downloads
- Open the browser with the **globe** button in the top bar of any screen. DuckDuckGo is the default search. Menu: tabs, bookmarks, history (off by default), find in page, reader mode, desktop site, add-ons, settings, clear data.
- Add-ons and Settings: ad and tracker blocker, cookie banner remover, HTTPS only, fingerprint shield, WebRTC guard, dark pages, image blocker, JavaScript switch, text size, clear data on close.
- **Download media** (Menu or the Media button) lists the images, videos, music and documents a page exposes, with filters, Save per file and Save all (up to 40). You can also long-press an image or link.
- Every download goes to a private inbox, a copy is saved to the phone Downloads folder (unless you turn that off), it is sealed into the vault, and the inbox copy is deleted after. If the vault is locked or busy the file waits (Menu > Downloads shows it) and is sealed later.
- Settings > Downloads has **Also save a copy to the phone Downloads folder** (on by default, V0.6.35). The copy in Downloads is NOT encrypted; the vault copy is. Menu > Downloads also has a 📲 button per waiting file and a Copy-all button. Turn the setting off to keep downloads only in the vault.
- Needs APK V0.6.35 or newer (the browser and the save-to-phone copy are native code).

## Getting the project into GitLab
Put the **contents** of the unzipped folder at the repository root, not the folder itself. `.gitlab-ci.yml` must sit at the root or the pipeline will not find `build.sh`, `www/` or `scripts/`. If `git status` shows a single folder like `bojack-secures-V0_6_xx/`, move its contents up one level.

## Build and release
See `docs/BUILD.md` (GitLab variables and keystore), `docs/RELEASING.md`, and `CHANGELOG.md`. Checks: `./build.sh check`.

## Honest status
Java is checked for syntax only and the pipeline compiles it; nothing in this project has been run on a phone by the person who wrote the change. The changelog marks each entry that way.
