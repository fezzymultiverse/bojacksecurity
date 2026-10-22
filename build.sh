#!/usr/bin/env bash
# Bojack Secures build helper (Termux, rootless). Usage: ./build.sh <init|check|keystore|assets|ci-prepare|ci-vars|wiki|help>
set -euo pipefail
cd "$(dirname "$0")"
APP_ID="com.fezzy.vault"; APP_NAME="Bojack Secures"
PLUGINS="@capacitor/core @capacitor/cli @capacitor/android @capacitor/filesystem @capacitor/local-notifications @capacitor-community/privacy-screen @capgo/capacitor-native-biometric @capacitor/app @capacitor/device @capacitor/share"
say(){ printf '\e[38;5;129m[vault]\e[0m %s\n' "$*"; }
need(){ command -v "$1" >/dev/null || { say "missing $1 -> pkg install $2"; exit 1; }; }

vendor_pdfjs(){ # PDF reader for the viewer (the Android WebView cannot show PDFs inline). Apache-2.0. Not fatal: without it the viewer offers Restore to phone.
  local T="${TMPDIR:-/tmp}/bojack-pdfjs" V="4.10.38" B
  if [ -f www/vendor/pdf.min.js ] && [ -f www/vendor/pdf.worker.min.js ]; then say "pdf.js already in www/vendor"; return 0; fi
  rm -rf "$T"; mkdir -p "$T" www/vendor
  if ( cd "$T" && npm init -y >/dev/null 2>&1 && npm i "pdfjs-dist@$V" --no-audit --no-fund >"$T/install.log" 2>&1 ); then
    B="$T/node_modules/pdfjs-dist/legacy/build"
    if [ -f "$B/pdf.min.mjs" ] && [ -f "$B/pdf.worker.min.mjs" ]; then
      cp "$B/pdf.min.mjs" www/vendor/pdf.min.js && cp "$B/pdf.worker.min.mjs" www/vendor/pdf.worker.min.js
      cp "$T/node_modules/pdfjs-dist/LICENSE" www/vendor/pdfjs-LICENSE.txt 2>/dev/null || true
      say "pdf.js $V copied to www/vendor"; return 0
    fi
    say "pdf.js files not found in $B (viewer will use Restore to phone for PDFs)"
  else
    say "could not install pdfjs-dist (viewer will use Restore to phone for PDFs)"; tail -n 5 "$T/install.log" 2>&1 || true
  fi
  return 0
}

project(){ # shared by init and ci-prepare
  [ -f capacitor.config.json ] || npx cap init "$APP_NAME" "$APP_ID" --web-dir www
  [ -d android ] || npx cap add android
  bash scripts/patch-android.sh
  vendor_pdfjs
  npx cap sync android
}

init(){
  need node nodejs; need git git
  [ -f package.json ] || npm init -y >/dev/null
  # shellcheck disable=SC2086
  npm i $PLUGINS
  project
  [ -d .git ] || git init -q
  ci_file
  say "init done. Next: ./build.sh check && ./build.sh keystore && ./build.sh assets && git push"
}

check(){ need node nodejs; node scripts/check-js.js && node scripts/test-share.js && bash -n build.sh scripts/patch-android.sh scripts/verify-build.sh && say "all checks passed"; }

ci_prepare(){ # non-interactive, used by .gitlab-ci.yml. Does not need android/ or package.json committed.
  [ -f package.json ] || npm init -y >/dev/null
  if [ -f package-lock.json ] && grep -q '"@capacitor/share"' package.json; then npm ci
  else
    # shellcheck disable=SC2086
    npm i $PLUGINS
  fi
  project
  ci_icons
}

gen_assets(){ # shared by ci_icons and assets. Installs @capacitor/assets into an isolated tools dir (not the app's package.json), because a project-level `npm i -D` reported "up to date" on npm 12 without placing the package.
  local ROOT T L CA="" c
  ROOT="$(pwd)"; T="${TMPDIR:-/tmp}/bojack-assets-tools"; L="$T/install.log"
  rm -rf "$T"; mkdir -p "$T"
  ( cd "$T" && npm init -y >/dev/null 2>&1 && npm i @capacitor/assets --no-audit --no-fund --loglevel=http >"$L" 2>&1 ) || {
    say "npm i @capacitor/assets failed. Last log lines:"; tail -n 40 "$L" 2>&1 || true; exit 1; }
  if [ ! -d "$T/node_modules/@capacitor/assets" ]; then
    say "@capacitor/assets missing after install. Diagnostics:"
    tail -n 40 "$L" 2>&1 || true
    ( cd "$T" && cat package.json && ls -la node_modules node_modules/@capacitor 2>&1 | head -40 && npm ls 2>&1 | head -20 ) || true
    node -v; npm -v; exit 1
  fi
  # sharp ships prebuilt binaries; only if it will not load do we let npm run install scripts (npm 12 blocks them by default).
  if ! ( cd "$T/node_modules/@capacitor/assets" && node -e "require('sharp')" ) 2>/dev/null; then
    say "sharp did not load, approving install scripts and rebuilding"
    ( cd "$T" && { npm install-scripts approve --all >/dev/null 2>&1 || true; npm rebuild sharp 2>&1 | tail -n 15 || true; } )
    ( cd "$T/node_modules/@capacitor/assets" && node -e "require('sharp')" ) 2>&1 | tail -n 5 || true
    ( cd "$T/node_modules/@capacitor/assets" && node -e "require('sharp')" ) >/dev/null 2>&1 || { say "sharp failed to load (native build blocked); icons cannot be generated"; exit 1; }
  fi
  # Resolve the CLI from the package.json bin field, then known paths.
  CA="$(cd "$T/node_modules/@capacitor/assets" && node -e "const p=require('./package.json');const b=typeof p.bin==='string'?p.bin:(p.bin||{})['capacitor-assets'];if(b)console.log(require('path').resolve(b))" 2>/dev/null || true)"
  if [ -z "$CA" ] || [ ! -f "$CA" ]; then
    CA=""
    for c in bin/capacitor-assets bin/capacitor-assets.js dist/cli.js; do
      [ -f "$T/node_modules/@capacitor/assets/$c" ] && { CA="$T/node_modules/@capacitor/assets/$c"; break; }
    done
  fi
  if [ -z "$CA" ]; then
    say "capacitor-assets CLI not found. Diagnostics:"
    ls -la "$T/node_modules/@capacitor/assets" "$T/node_modules/@capacitor/assets/bin" 2>&1 | head -30 || true
    exit 1
  fi
  say "using $CA"
  ( cd "$ROOT" && node "$CA" generate --android --iconBackgroundColor '#000000' --splashBackgroundColor '#000000' )
  npx cap sync android
}

ci_icons(){ # generate the paw launcher icon + splash in CI
  gen_assets
}

keystore(){
  need keytool openjdk-17
  [ -f fezzy-vault.jks ] && { say "fezzy-vault.jks already exists, refusing to overwrite"; exit 1; }
  read -rsp "Keystore password: " P; echo
  keytool -genkeypair -v -keystore fezzy-vault.jks -alias fezzyvault -keyalg RSA -keysize 4096 \
    -validity 10000 -storepass "$P" -keypass "$P" -dname "CN=Fezzy Vault, O=Fezzy, C=ZA"
  base64 -w0 fezzy-vault.jks > keystore.b64
  say "Back up fezzy-vault.jks somewhere safe. Lose it and you can never update the app."
  say "Add these GitLab CI/CD variables (masked): KEYSTORE_B64 (contents of keystore.b64), KEYSTORE_PASS, KEY_ALIAS=fezzyvault"
}

assets(){ gen_assets; }

ci_file(){
  [ -f .gitlab-ci.yml ] || { say '.gitlab-ci.yml is missing from the repo'; return 1; }
  say ".gitlab-ci.yml present. Needed CI variables: KEYSTORE_B64, KEYSTORE_PASS, KEY_ALIAS"
}

wiki(){
  [ -n "${1:-}" ] || { say "usage: ./build.sh wiki <git-url-of-repo>.wiki.git"; exit 1; }
  local t; t=$(mktemp -d); git clone "$1" "$t" 2>/dev/null || git init -q "$t"
  cp docs/WIKI.md "$t/Home.md"; cp docs/THREAT_MODEL.md "$t/Threat-Model.md"; cp docs/PRIVACY.md "$t/Privacy.md"
  ( cd "$t" && git add -A && git commit -qm "Update wiki" && git push -u origin HEAD )
}

case "${1:-help}" in
  init) init;; check) check;; keystore) keystore;; assets) assets;; ci-prepare) ci_prepare;; ci-vars) ci_file;; wiki) shift; wiki "$@";;
  *) echo "usage: $0 {init|check|keystore|assets|ci-prepare|ci-vars|wiki <url>}";;
esac
