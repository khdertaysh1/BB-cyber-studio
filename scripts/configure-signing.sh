#!/usr/bin/env bash
# Run on your own trusted computer or Termux. Never use shell tracing.
set -euo pipefail
set +x
for command in keytool gh base64; do
  command -v "$command" >/dev/null || { echo "Missing dependency: $command" >&2; exit 1; }
done
repo="${1:-khdertaysh1/BB-cyber-studio}"
task_dir="${BLUE_DRAGON_SIGNING_DIR:-$HOME/.local/share/blue-dragon-signing}"
umask 077
mkdir -p "$task_dir"
chmod 700 "$task_dir"
key_file="$task_dir/release.jks"
export BLUE_DRAGON_KEY_ALIAS=blue-dragon
read -r -s -p 'Private keystore password (keep a secure backup): ' BLUE_DRAGON_KEYSTORE_PASSWORD
printf '\n'
[[ ${#BLUE_DRAGON_KEYSTORE_PASSWORD} -ge 12 ]] || { echo 'Use at least 12 characters.' >&2; exit 1; }
export BLUE_DRAGON_KEYSTORE_PASSWORD
export BLUE_DRAGON_KEY_PASSWORD="$BLUE_DRAGON_KEYSTORE_PASSWORD"
if [[ ! -f "$key_file" ]]; then
  read -r -s -p 'Confirm password: ' confirmation
  printf '\n'
  [[ "$confirmation" == "$BLUE_DRAGON_KEYSTORE_PASSWORD" ]] || { echo 'Passwords do not match.' >&2; exit 1; }
  unset confirmation
  keytool -genkeypair -noprompt -keystore "$key_file" -storetype JKS \
    -storepass:env BLUE_DRAGON_KEYSTORE_PASSWORD -keypass:env BLUE_DRAGON_KEY_PASSWORD \
    -alias "$BLUE_DRAGON_KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname 'CN=Blue Dragon'
fi
keytool -list -keystore "$key_file" -storepass:env BLUE_DRAGON_KEYSTORE_PASSWORD -alias "$BLUE_DRAGON_KEY_ALIAS" >/dev/null
base64 < "$key_file" | tr -d '\n' | gh secret set BLUE_DRAGON_KEYSTORE_BASE64 --repo "$repo"
printf '%s' "$BLUE_DRAGON_KEYSTORE_PASSWORD" | gh secret set BLUE_DRAGON_KEYSTORE_PASSWORD --repo "$repo"
printf '%s' "$BLUE_DRAGON_KEY_ALIAS" | gh secret set BLUE_DRAGON_KEY_ALIAS --repo "$repo"
printf '%s' "$BLUE_DRAGON_KEY_PASSWORD" | gh secret set BLUE_DRAGON_KEY_PASSWORD --repo "$repo"
unset BLUE_DRAGON_KEYSTORE_PASSWORD BLUE_DRAGON_KEY_PASSWORD
echo "Signing configured. Keep the keystore at $key_file and its password in a secure offline backup."
echo "Use the same keystore for future updates. Play Protect scanning is a separate Google assessment."
