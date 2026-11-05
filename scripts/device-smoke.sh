#!/usr/bin/env bash
set -euo pipefail
# Use the runner's installed emulator instead of upgrading to an unpinned latest archive.
SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
"$SDK_ROOT/emulator/emulator" -version
sdkmanager 'system-images;android-35;google_apis;x86_64'
printf 'no\n' | avdmanager create avd --force --name bb-smoke --package 'system-images;android-35;google_apis;x86_64' --device pixel_2
"$SDK_ROOT/emulator/emulator" -avd bb-smoke -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 2048 > emulator.log 2>&1 &
EMU_PID=$!
trap 'adb emu kill || true; kill "$EMU_PID" 2>/dev/null || true' EXIT
timeout 180 adb wait-for-device
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
