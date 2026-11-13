# Blue Dragon — Android 0.5.1 Alpha

**Blue Dragon** is the new branding for BB Cyber Studio. The Android application ID stays `studio.bb.cyber` so existing installations can be updated **when they share the same signing certificate**.

A mobile-first browser and developer workspace for web inspection on Android 8+ (no root). The app uses Android System WebView, not a full Chromium fork.

## What's new in v0.5.1

- Release builds require a private signing configuration; publication cannot fall back to Debug.
- CI verifies non-debuggable APKs and an in-place same-key version update on Android.
- Browser menu → App & installation info shows version, build type and public certificate fingerprint.
- Clearing browser data clears pending/copyable console results as well.

## Branding in v0.5

- Redesigned new-tab browser page with Blue Dragon icon, sapphire and cyan branding.
- New Android vector launcher icon and updated native Tools header.
- Network includes working Clear, Reload and Copy URLs actions plus real observed-request and unique-host counters.
- Required stable release signing for publication through GitHub Actions secrets. Read [APK signing and installation troubleshooting](docs/SIGNING.md).
- Request status codes, timing, and byte sizes are **not** fabricated: WebView only exposes request metadata.

## Previous v0.4 capabilities

- **Google** is the default search engine, in the address bar and new-tab page.
- The new-tab page no longer shows Local Lab or Example Website shortcuts. The isolated loopback fixture remains internally for automated tests.
- **Console** is focused on Run and Copy: execute JavaScript in the active page, copy the latest evaluated result with one tap, or copy all logs from Tools ⋮.
- **Elements** explains the DOM inspector, lets you Inspect HTML and Copy HTML, and hides temporary CSS edits under an expandable control.
- **Terminal** explicitly offers Android or Termux mode. Android's local shell cannot run `pkg` or `apt`. Termux mode uses the *separate installed Termux application* with explicit user permission; it is not an embedded full Linux terminal.
- More consistent spacing, colors and tab styles. Tools remain a bottom panel above the live browser.

## Install

Download a debug APK from a green [Blue Dragon Android APK workflow run](https://github.com/khdertaysh1/BB-cyber-studio/actions/workflows/android.yml) → **Artifacts** → `BlueDragon-0.5.1-alpha-debug` → extract `app-debug.apk`. GitHub Releases will publish `BlueDragon.apk` (signed) when the repository's four stable release-signing secrets are configured; publication stops until signing is configured. Debug APKs created by separate CI runs may not upgrade one another. **Uninstalling an old build may delete its local data.** See [signing instructions](docs/SIGNING.md).

## Practical usage

**Browser:** search with Google, enter website URLs, reload, manage up to eight tabs. The web page stays behind the expandable Tools panel.

**Console:** Tools → Console → write JavaScript → Run → Copy. To copy all collected logs, use the Tools ⋮ menu. The output TextView supports text selection.

**Elements:** Tools → Elements → type a CSS selector like `h1`, `.item` or `#login` → Inspect HTML → Copy HTML. Use **Edit CSS** only when needed; changes are temporary and local.

**Network:** an observed WebView request list with URL/method filtering, best-effort resource categories, request headers and transfer to independent Repeater. This is NOT full HTTPS interception; original browser response status/body and POST payloads are unavailable. Avoid claiming a request is reproduced with the same session.

**Repeater:** native HTTP client with verified TLS, edited URL/method/headers/body and a response viewer. There is no automatic browser-cookie synchronization. No redirects follow automatically; outputs are bounded.

**Terminal:** Android mode launches a persistent pipe-based shell inside the app sandbox, with no Termux packages. Termux mode sends explicit commands to the separate Termux `RUN_COMMAND` service and shows output after completion (not a live interactive PTY). Open interactive Termux for programs requiring a terminal UI.

## Connect Termux

Install an official [Termux](https://github.com/termux/termux-app) distribution. In Termux set `allow-external-apps=true` in `~/.termux/termux.properties`, then run `termux-reload-settings`. Grant **Run commands in Termux environment** to Blue Dragon under Android app permissions. Select **Terminal → Android ▾ → Termux ▾**. You may need to grant the permission and press Run again.

Commands originate only from the native input and require confirmation before Termux executes them. The app does not expose shell APIs to webpages.

## Security and limitations

- No root, account, analytics, remote backend, page-accessible JavaScript bridge or trust-all TLS overrides.
- Per-tab request metadata and logs are in memory, WebView cookies/storage are shared.
- One optional saved request can be encrypted using Android Keystore; access through the browser menu.
- Some identity providers and websites reject WebView. Browser extensions, JS breakpoints, interactive embedded PTY, real HTTPS interception, WebSocket frames and HAR export are not implemented.

See [Design](docs/DESIGN.md), [Testing](docs/TESTING.md), and [Roadmap](docs/ROADMAP.md).
