#!/usr/bin/env bash
set -euo pipefail
apk="${1:?Usage: verify-release.sh APK}"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Android SDK is required}}"
build_tools="$sdk/build-tools/35.0.0"
report="$("$build_tools/apksigner" verify --verbose --print-certs "$apk")"
if [[ "$report" == *"CN=Android Debug"* ]]; then
  echo "Release rejected: Android debug signing certificate." >&2
  exit 1
fi
if "$build_tools/aapt" dump badging "$apk" | grep -q '^application-debuggable'; then
  echo "Release rejected: debuggable manifest." >&2
  exit 1
fi
# Only public certificate information is printed.
printf '%s\n' "$report"
