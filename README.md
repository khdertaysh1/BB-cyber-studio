# BB Cyber Studio

**An Android workspace for web development and security research.**

Browser • JavaScript console • DOM inspector • HTTP Repeater • Termux commands

[![Android APK](https://github.com/khdertaysh1/BB-cyber-studio/actions/workflows/android.yml/badge.svg)](https://github.com/khdertaysh1/BB-cyber-studio/actions/workflows/android.yml)

**0.1.0 alpha** — a working foundation, not a complete Burp Suite replacement. No root, account, analytics or hosted backend. Android 8.0+; an updated Android System WebView is recommended.

## Install from GitHub on your phone

1. Open [Actions → Android APK](https://github.com/khdertaysh1/BB-cyber-studio/actions/workflows/android.yml).
2. Choose a **successful** run for the branch you want. Scroll to **Artifacts**.
3. Download `BB-cyber-studio-0.1.0-alpha-debug`, extract the ZIP, and install `app-debug.apk`.
4. GitHub requires sign-in to download Actions artifacts. You may need to allow APK installation for your browser/files app.

These are development APKs signed with a CI debug key. Different runs may require uninstalling the previous build, which deletes its local data. Stable signed releases are a later milestone; never commit signing keys.

## Try it without an external service

1. Launch the app. Its **Local lab** runs on your phone's loopback interface.
2. Tap **Send GET** on the lab page, then select **Network**.
3. Tap the echo request → **To Repeater** → **Send request**. Inspect the actual HTTP response.
4. Change its query string, send again, then **Compare last two**.
5. In **Console**, evaluate `document.title` or `document.body.style.background = '#183247'`.
6. In **Elements**, inspect `h1` and change its `color`.
7. **To Terminal** prepares a quoted curl command without executing it. Connect Termux to run it.

The local lab is reachable only at `127.0.0.1` using an unguessable per-launch URL. It is an echo fixture, not an isolated sandbox for hostile code. Its address changes after restart; saved requests targeting an old lab address need updating.

## Implemented vs planned

| Feature | 0.1 alpha |
|---|---|
| Embedded browser | One WebView with back navigation, URL bar and local lab |
| Network | Up to 200 observed request URLs, methods and available headers; filtering and transfer to Repeater |
| Repeater | GET, POST, PUT, DELETE, HEAD, OPTIONS; editable headers/body; verified TLS; bounded response viewer |
| Redirects / cookies | Redirects displayed without following; browser cookies are not automatically shared with Repeater |
| Console | Top-level JavaScript evaluation and WebView console logs |
| Elements | CSS selector → DOM snapshot; inline CSS editing |
| Terminal | Real Termux background commands, completed stdout/stderr/exit code; button to open interactive Termux |
| Workspace | One explicitly saved request encrypted using Android Keystore; delete and browser-data clearing controls |
| HTTP(S) interception | **Not implemented** — Network is metadata observation, not a MITM proxy |
| Captured bodies/status | **Not implemented** for browser traffic; Repeater shows responses to its own requests |
| Full DevTools | Not included: JS breakpoints, source debugger, iframe/worker inspection, element picking |
| Interactive embedded PTY | Planned; the current command runner does not stream output or accept stdin |
| WebSocket, HAR, profiles, tabs | Planned |

WebView callbacks do not see every redirect, service-worker request, cached resource or WebSocket frame. Imported request metadata may omit cookies and bodies. Review every request before sending. Native HTTP framing headers are managed by the client; raw protocol fuzzing and duplicate headers are not supported.

## Connect Termux

Use an official [Termux](https://github.com/termux/termux-app) build with `RUN_COMMAND` result support (0.109+). Open Termux once, then:

```sh
mkdir -p ~/.termux
# Edit the file and set this property once:
nano ~/.termux/termux.properties
# allow-external-apps=true
termux-reload-settings
```

Grant **BB Cyber Studio → Permissions → Additional permissions → Run commands in Termux environment**. The app can request this permission with **Run in Termux**. Run `pwd` first. Termux remains a separate dependency in this release.

Commands run under Termux's user and permissions, after confirmation in the native UI. Results appear on completion; use **Open Termux** for interactive programs. OEM battery restrictions can interrupt execution. Upstream integration reference: [RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).

## Build

JDK 17, Android SDK platform 35, and Gradle **8.10.2**:

```sh
gradle testDebugUnitTest lintDebug assembleDebug
```

The CI workflow installs the pinned Gradle version. A Gradle wrapper is not yet bundled; do not run an arbitrary system Gradle version. The app uses Android platform Java APIs without third-party runtime dependencies. CI compiles the APK and runs JVM tests; see [testing](docs/TESTING.md) for device verification.

## Design and security

- The browser has **no JavaScript-to-native bridge** and cannot invoke Termux.
- TLS errors cancel navigation; native requests use platform certificate verification. No trust-all manager or SSL bypass.
- Cleartext HTTP is enabled intentionally for local development and HTTP test endpoints.
- Browser data remains in WebView's app-private storage. Saved request files use AES-GCM with an Android Keystore key. Backups are disabled.
- Network, console, responses and terminal output remain in memory. Clipboard export is explicit and can contain credentials.
- The Termux result receiver is non-exported; callbacks use an explicit, one-shot mutable PendingIntent so Termux can return result extras.
- Only user-selected HTTP requests and commands run. No automatic scanning or external telemetry.

See [architecture](docs/ARCHITECTURE.md), [testing](docs/TESTING.md), and [roadmap](docs/ROADMAP.md).

## بالعربي

**BB Cyber Studio** مساحة عمل للمبرمج والباحث الأمني على أندرويد: متصفح، كونسول JavaScript، فحص عناصر، محرر طلبات HTTP وربط Termux.

لتجربة النسخة: من **Actions** افتح بناء ناجح، ونزّل ملف APK من **Artifacts** وفك الضغط وثبّته. جرّب **Local lab** ثم **Network → To Repeater → Send request**.

النسخة الأولى تعرض معلومات طلبات المتصفح، وليست اعتراض HTTPS كاملاً. الطرفية تشغّل أوامر حقيقية عبر Termux وتعرض نتيجتها عند الانتهاء؛ الطرفية التفاعلية المدمجة ضمن خطة التطوير. واجهة التطبيق حالياً بالإنكليزي، والترجمة العربية الكاملة لاحقاً.
