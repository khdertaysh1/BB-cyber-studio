#!/usr/bin/env bash
set -euo pipefail
# Pin the emulator instead of relying on a preinstalled binary or the latest SDK archive.
SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export ANDROID_AVD_HOME="$RUNNER_TEMP/bb-avd"
mkdir -p "$ANDROID_AVD_HOME"
export PATH="$SDK_ROOT/cmdline-tools/latest/bin:$SDK_ROOT/platform-tools:$PATH"
curl --fail --location --retry 3 --output /tmp/bb-emulator.zip https://dl.google.com/android/repository/emulator-linux_x64-12836668.zip
echo '160fec5c31b4e3b81c47c18dcb0d103fc4dfb13396dd75463d02a945e8f60d5c  /tmp/bb-emulator.zip' | sha256sum --check
unzip -q -o /tmp/bb-emulator.zip -d "$SDK_ROOT"
"$SDK_ROOT/emulator/emulator" -version
(set +o pipefail; yes | sdkmanager --licenses > /dev/null)
sdkmanager 'system-images;android-35;google_apis;x86_64'
printf 'no\n' | avdmanager create avd --force --name bb-smoke --package 'system-images;android-35;google_apis;x86_64' --device pixel_2 --path "$ANDROID_AVD_HOME/bb-smoke.avd"
"$SDK_ROOT/emulator/emulator" -avd bb-smoke -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 2048 > emulator.log 2>&1 &
EMU_PID=$!
trap 'adb emu kill || true; kill "$EMU_PID" 2>/dev/null || true' EXIT
timeout 180 adb wait-for-device || { cat emulator.log; exit 1; }
booted=false
for attempt in $(seq 1 90); do
  if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == '1' ]]; then booted=true; break; fi
  sleep 2
done
if [[ "$booted" != true ]]; then cat emulator.log; exit 1; fi
adb shell input keyevent 82
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
result=0
gradle --no-daemon connectedDebugAndroidTest || result=$?
adb pull /sdcard/Android/data/studio.bb.cyber/files/ device-screenshots/ || true
adb logcat -d > device-logcat.txt
exit "$result"
