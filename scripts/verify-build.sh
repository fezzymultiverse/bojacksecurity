#!/usr/bin/env bash
# V0.6.30: build guard. Fails the pipeline when the app would be built WITHOUT its native part (the cause of "Needs the new build").
# Usage: bash scripts/verify-build.sh project        (after build.sh ci-prepare: checks the generated android/ project)
#        bash scripts/verify-build.sh apk <file.apk>  (after signing: checks the real APK)
set -uo pipefail
cd "$(dirname "$0")/.."
FAILS=0
ok(){ printf '[verify] ok:   %s\n' "$*"; }
bad(){ printf '[verify] FAIL: %s\n' "$*" >&2; FAILS=$((FAILS+1)); }
warn(){ printf '[verify] warn: %s\n' "$*"; }
VER=$(sed -n "s/.*const VER='\([^']*\)'.*/\1/p" www/index.html | head -1)
[ -n "$VER" ] || { bad "VER not found in www/index.html"; exit 1; }
PLUGINS="BojackKeepAlive BojackStore"
OWN="PrivBrowser BojackStoreJs"   # V0.6.33 privacy browser + V0.6.34 direct vault-storage bridge (classes + bridges must be present)
GONE="WebSheet BrowserExt BojackBrowser BjDownload BjBlob"   # the old browser: must NOT be in the project or the APK
J=android/app/src/main/java/com/fezzy/vault
M=android/app/src/main/AndroidManifest.xml
G=android/app/build.gradle

project(){
  [ -d "$J" ] || { bad "$J does not exist (cap add android did not make the com.fezzy.vault package)"; return; }
  local f b p
  for f in scripts/android-native/*.java; do
    b=$(basename "$f")
    if [ ! -f "$J/$b" ]; then bad "$b was not copied into the android project"
    elif ! cmp -s "$f" "$J/$b"; then bad "$b in the android project differs from scripts/android-native/$b"
    else ok "$b is in the android project"; fi
  done
  head -n 1 "$J/MainActivity.java" 2>/dev/null | grep -q '^package com.fezzy.vault;' && ok "MainActivity is in package com.fezzy.vault" || bad "MainActivity.java is missing or in the wrong package"
  for p in $PLUGINS; do
    grep -q "registerPlugin($p.class)" "$J/MainActivity.java" 2>/dev/null && ok "MainActivity registers $p" || bad "MainActivity does not register $p"
  done
  grep -q 'addJavascriptInterface(new BojackJs' "$J/MainActivity.java" 2>/dev/null && ok "MainActivity adds the BojackNative bridge" || bad "MainActivity does not add the BojackNative bridge"
  grep -q 'addJavascriptInterface(new PrivBrowser' "$J/MainActivity.java" 2>/dev/null && ok "MainActivity adds the BojackPB browser bridge" || bad "MainActivity does not add the BojackPB browser bridge"
  grep -q 'addJavascriptInterface(new BojackStoreJs' "$J/MainActivity.java" 2>/dev/null && ok "MainActivity adds the BojackST storage bridge" || bad "MainActivity does not add the BojackST storage bridge"
  [ -f "$J/BojackStoreJs.java" ] && ok "BojackStoreJs.java is in the android project" || bad "BojackStoreJs.java is missing"
  [ -f "$J/PrivBrowser.java" ] && ok "PrivBrowser.java is in the android project" || bad "PrivBrowser.java is missing"
  grep -q 'KeepAliveService' "$M" 2>/dev/null && ok "manifest declares KeepAliveService" || bad "manifest has no KeepAliveService"
  for p in $GONE; do
    [ -f "$J/$p.java" ] && bad "$p.java (old browser) is still in the android project" || ok "$p.java is not in the android project"
  done
  grep -q 'androidx.webkit' "$G" 2>/dev/null && bad "androidx.webkit (old browser dependency) is still in $G" || ok "no browser dependency in $G"
  grep -q "versionName \"$VER\"" "$G" 2>/dev/null && ok "versionName is $VER" || bad "versionName in $G is not $VER"
  if [ -f android/app/src/main/assets/public/index.html ]; then
    grep -q "const VER='$VER'" android/app/src/main/assets/public/index.html && ok "synced www is V$VER" || bad "android/app/src/main/assets/public/index.html is not V$VER (cap sync did not copy the current www)"
  else warn "assets/public/index.html not found yet (cap sync not run?)"; fi
  [ -f www/vendor/pdf.min.js ] && ok "pdf.js is in www/vendor" || warn "pdf.js is not in www/vendor (PDFs will offer Restore to phone)"
}

apk(){
  local A="${1:-}" BT B D c miss="" extra=""
  [ -f "$A" ] || { bad "APK not found: $A"; return; }
  BT=$(ls -d "${ANDROID_HOME:-/nonexistent}"/build-tools/* 2>/dev/null | sort -V | tail -1)
  if [ -n "$BT" ] && [ -x "$BT/aapt2" ]; then
    B=$("$BT/aapt2" dump badging "$A" 2>/dev/null | head -n 1)
    if [ -n "$B" ]; then printf '[verify] %s\n' "$B"; case "$B" in *"versionName='$VER'"*) ok "APK versionName is $VER";; *) bad "APK versionName is not $VER";; esac
    else warn "aapt2 could not read the APK"; fi
  else warn "aapt2 not found, APK version not read"; fi
  unzip -p "$A" assets/public/index.html 2>/dev/null | grep -q "const VER='$VER'" && ok "the screens inside the APK are V$VER" || bad "the screens inside the APK are not V$VER"
  # V0.6.34: look for the exact dex type descriptor (Lcom/fezzy/vault/<Class>;) in every classes*.dex of the APK, not a loose substring.
  local DEX; DEX=$(unzip -Z1 "$A" 2>/dev/null | grep -E '^classes[0-9]*\.dex$')
  [ -n "$DEX" ] || { bad "the APK has no classes.dex at all"; return; }
  D=$(mktemp); for c in $DEX; do unzip -p "$A" "$c" >>"$D" 2>/dev/null; done
  [ -s "$D" ] || { rm -f "$D"; bad "could not read the dex files inside the APK"; return; }
  for c in MainActivity $PLUGINS BojackJs KeepAliveService $OWN; do grep -aq "Lcom/fezzy/vault/$c;" "$D" || miss="$miss $c"; done
  for c in $GONE; do grep -aq "Lcom/fezzy/vault/$c;" "$D" && extra="$extra $c"; done
  rm -f "$D"
  [ -z "$miss" ] && ok "native classes are inside the APK" || bad "native classes missing from the APK:$miss"
  [ -z "$extra" ] && ok "no browser classes inside the APK" || bad "browser classes found inside the APK:$extra"
}

case "${1:-}" in
  project) project;;
  apk) shift; apk "$@";;
  *) echo "usage: $0 project | apk <file.apk>"; exit 2;;
esac
if [ "$FAILS" -gt 0 ]; then printf '[verify] %s check(s) FAILED. This APK would be missing parts of the app. Do not install it.\n' "$FAILS" >&2; exit 1; fi
printf '[verify] OK (V%s)\n' "$VER"
