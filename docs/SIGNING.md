# Blue Dragon: signing and installation warnings

## Why the APK may fail to update

GitHub Actions debug builds ordinarily use ephemeral Android debug signing keys. If a new APK and an existing installed app have the same package name `studio.bb.cyber` but different signing certificates, Android rejects the update. Renaming the app's visible label does not fix certificate mismatch.

Back up any data you need before removing an old installation. A debug-to-release key migration generally requires uninstalling the debug build first; **uninstalling deletes app-local data**.

## Configure stable, private release signing

1. Generate a release JKS keystore using Java `keytool`, store it securely offline and keep a backup. Never commit the keystore or its passwords to GitHub.
2. In **Repository Settings → Secrets and variables → Actions**, set the following four repository secrets:
   - `BLUE_DRAGON_KEYSTORE_BASE64` — single-line base64 representation of the JKS file
   - `BLUE_DRAGON_KEYSTORE_PASSWORD` — keystore password
   - `BLUE_DRAGON_KEY_ALIAS` — alias of your key
   - `BLUE_DRAGON_KEY_PASSWORD` — key password
3. On successful builds of `main`, the release workflow then publishes `BlueDragon.apk` signed with that persistent key. Without these secrets it falls back to a clearly labeled `BlueDragon-debug.apk`; debug updates across CI runs are not guaranteed.
4. Keep the same private signing key for subsequent updates and always increment `versionCode`.

Do not post the JKS, passwords or base64 keystore in issues, public code or chat.

## Google Play Protect alerts

A non-Play-store APK may trigger a scan recommendation because Play Protect has not seen the app before. A warning that the app is **harmful** is a different, more serious category that requires investigating permissions and behavior and potentially filing a review/appeal. An APK's size, name or signing alone does not guarantee that Play Protect warnings disappear. Keep Play Protect enabled and follow official developer warning guidance.
