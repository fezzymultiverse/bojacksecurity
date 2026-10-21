#!/usr/bin/env bash
# Idempotent patches applied to the generated Capacitor Android project.
# Used by build.sh (Termux) and .gitlab-ci.yml, so both always build the same app.
set -euo pipefail
cd "$(dirname "$0")/.."
M=android/app/src/main/AndroidManifest.xml
G=android/app/build.gradle
[ -f "$M" ] || { echo "[patch] $M not found. Run: npx cap add android" >&2; exit 1; }

# no cloud/adb backup of app data
sed -i 's/android:allowBackup="true"/android:allowBackup="false"/' "$M"

add_perm(){ # $1 = permission, $2 = optional extra attributes
  grep -q "android.permission.$1" "$M" || \
    sed -i "s#<application#<uses-permission android:name=\"android.permission.$1\"${2:+ $2} />\n    <application#" "$M"
}
add_perm USE_BIOMETRIC
add_perm POST_NOTIFICATIONS
add_perm MANAGE_EXTERNAL_STORAGE
add_perm READ_EXTERNAL_STORAGE 'android:maxSdkVersion="32"'
add_perm WRITE_EXTERNAL_STORAGE 'android:maxSdkVersion="32"'
add_perm FOREGROUND_SERVICE
add_perm FOREGROUND_SERVICE_DATA_SYNC
add_perm WAKE_LOCK

# V0.6.6: foreground service + JS plugin so uploads keep running with the app in the background
grep -q 'KeepAliveService' "$M" || \
  sed -i 's#</application>#    <service android:name=".KeepAliveService" android:exported="false" android:foregroundServiceType="dataSync" />\n    </application>#' "$M"
# V0.6.34: the Java files go into the package folder the generated project REALLY uses (read from build.gradle), and the script stops
# with an error, instead of carrying on, if the package is wrong, nothing was copied, or any file did not arrive byte-identical.
EXPECTED_PKG="com.fezzy.vault"
PKG=""
[ -f "$G" ] && PKG=$(sed -n "s/^[[:space:]]*namespace[[:space:]]*=\{0,1\}[[:space:]]*[\"']\([^\"']*\)[\"'].*/\1/p" "$G" | head -1)
if [ -z "$PKG" ]; then
  echo "[patch] warn: no namespace found in $G, assuming $EXPECTED_PKG" >&2; PKG="$EXPECTED_PKG"
elif [ "$PKG" != "$EXPECTED_PKG" ]; then
  echo "[patch] FAIL: $G has namespace '$PKG' but the native code is package $EXPECTED_PKG. Delete android/ and run: npx cap add android (appId must be $EXPECTED_PKG)." >&2; exit 1
fi
J="android/app/src/main/java/${PKG//.//}"
SRC=(scripts/android-native/*.java)
[ -e "${SRC[0]}" ] || { echo "[patch] FAIL: no .java files in scripts/android-native/" >&2; exit 1; }
mkdir -p "$J"
for f in "${SRC[@]}"; do
  b=$(basename "$f")
  cp -f "$f" "$J/$b"
  cmp -s "$f" "$J/$b" || { echo "[patch] FAIL: $b was not copied into $J" >&2; exit 1; }
  # a copy of the same class in another folder (e.g. the MainActivity that `cap add` made somewhere else) would be a duplicate class
  while IFS= read -r dup; do [ -n "$dup" ] && { rm -f "$dup"; echo "[patch] removed duplicate $dup"; }; done < <(find android/app/src/main/java -name "$b" -not -path "$J/*" 2>/dev/null)
  echo "[patch] installed $b"
done
echo "[patch] ${#SRC[@]} native Java files installed into $J"

# V0.6.32/33: the OLD Prodigy-based browser is gone for good (the new PrivBrowser.java is not in this list). An android/ project left over from an older build (Termux keeps it) may still hold the
# old browser classes and the androidx.webkit dependency, so both are removed here and the build can never carry them.
for old in WebSheet BrowserExt BojackBrowser BjDownload BjBlob; do
  [ -f "$J/$old.java" ] && { rm -f "$J/$old.java"; echo "[patch] removed leftover $old.java"; }
done
if [ -f "$G" ] && grep -q 'androidx.webkit' "$G"; then
  sed -i '/androidx\.webkit:webkit/d' "$G"
  echo "[patch] removed leftover androidx.webkit dependency"
fi

# versionName/versionCode follow VER in www/index.html (0.6.0 -> code 600; 1.2.3 -> 10203)
VER=$(sed -n "s/.*const VER='\([^']*\)'.*/\1/p" www/index.html | head -1)
if [ -n "$VER" ] && [ -f "$G" ]; then
  IFS=. read -r MA MI PA <<<"$VER"
  CODE=$(( ${MA:-0}*10000 + ${MI:-0}*100 + ${PA:-0} ))
  sed -i "s/versionCode [0-9]\+/versionCode $CODE/; s/versionName \"[^\"]*\"/versionName \"$VER\"/" "$G"
  echo "[patch] version $VER (code $CODE)"
fi
echo "[patch] AndroidManifest + gradle patched"
