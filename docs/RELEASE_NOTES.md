# Blue Dragon 0.5 Alpha

- Rebranded the Android app, icon and browser start screen as Blue Dragon.
- Redesigned native colors and header, with functional Network Clear / Reload / Copy URLs.
- Added counters for real observed request metadata and unique hosts.
- Added optional stably signed Android release APK with secrets stored only in repository settings.
- Preserved `studio.bb.cyber` package ID to avoid creating a separate Android app.

## Known limitations

Android WebView cannot provide original response body, complete status codes, bytes transferred or request timing with the current interception-free architecture. The Network UI does not invent this data. Debug APK signatures can vary on GitHub runners, and APKs installed outside Google Play may trigger Play Protect security scanning; stable signing does not suppress all warnings. Terminal uses an Android app sandbox or a separately installed Termux application.
