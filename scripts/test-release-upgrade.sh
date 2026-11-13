#!/usr/bin/env bash
# Runs ONLY on the disposable CI emulator. Test keys are never distribution keys.
set -euo pipefail
(
  task_dir="$(mktemp -d)"
  trap 'rm -rf "$task_dir"' EXIT
  export BLUE_DRAGON_KEYSTORE_FILE="$task_dir/ci-test.jks"
  export BLUE_DRAGON_KEYSTORE_PASSWORD="$(openssl rand -hex 24)"
  export BLUE_DRAGON_KEY_PASSWORD="$BLUE_DRAGON_KEYSTORE_PASSWORD"
  export BLUE_DRAGON_KEY_ALIAS=blue-dragon-ci-only
  keytool -genkeypair -noprompt -keystore "$BLUE_DRAGON_KEYSTORE_FILE" -storetype JKS \
    -storepass:env BLUE_DRAGON_KEYSTORE_PASSWORD -keypass:env BLUE_DRAGON_KEY_PASSWORD \
    -alias "$BLUE_DRAGON_KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 2 \
    -dname 'CN=Blue Dragon CI Test Only' >/dev/null 2>&1
  gradle --no-daemon assembleRelease
  cp app/build/outputs/apk/release/app-release.apk "$task_dir/first.apk"
  bash scripts/verify-release.sh "$task_dir/first.apk" > release-verification.txt
  # Removing a package is permitted only on this disposable test emulator.
  adb uninstall studio.bb.cyber >/dev/null 2>&1 || true
  adb install "$task_dir/first.apk"
  adb shell am start -W -n studio.bb.cyber/.MainActivity
  gradle --no-daemon -PblueDragonVersionCode=7 assembleRelease
  apk=app/build/outputs/apk/release/app-release.apk
  bash scripts/verify-release.sh "$apk" >> release-verification.txt
  adb install -r "$apk"
  adb shell dumpsys package studio.bb.cyber > release-package.txt
  grep -q 'versionCode=7 ' release-package.txt
  adb shell am start -W -n studio.bb.cyber/.MainActivity
  mkdir -p device-screenshots
  adb shell screencap -p /data/local/tmp/release-start.png
  adb pull /data/local/tmp/release-start.png device-screenshots/release-start.png
  echo "Non-debuggable release install and same-key version upgrade passed." >> release-verification.txt
)
