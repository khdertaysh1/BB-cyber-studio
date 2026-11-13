# Blue Dragon 0.5.1 alpha
Release publication now requires private stable signing. Unsigned/debug APKs cannot be silently published as a release. CI checks non-debuggable APKs, validates signatures and tests a same-key version upgrade using disposable test keys. Public certificate fingerprints and checksums accompany published release builds.

Browser menu → App & installation info reports the installed build type, version, certificate SHA-256 and WebView provider. Clearing browser data also clears the copyable console result and ignores pending old evaluation results. Background WebViews pause/resume with the Activity.

Production signing requires the four documented Actions secrets. Play Protect scan recommendations depend on Google's assessment; release signing does not guarantee their removal.

# Blue Dragon 0.5 Alpha

- Rebranded the Android app, icon and browser start screen as Blue Dragon.
- Redesigned native colors and header, with functional Network Clear / Reload / Copy URLs.
- Added counters for real observed request metadata and unique hosts.
- Added optional stably signed Android release APK with secrets stored only in repository settings.
- Preserved `studio.bb.cyber` package ID to avoid creating a separate Android app.

## Known limitations

Android WebView cannot provide original response body, complete status codes, bytes transferred or request timing with the current interception-free architecture. The Network UI does not invent this data. Debug APK signatures can vary on GitHub runners, and APKs installed outside Google Play may trigger Play Protect security scanning; stable signing does not suppress all warnings. Terminal uses an Android app sandbox or a separately installed Termux application.
