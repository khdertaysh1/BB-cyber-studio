# Architecture

Native `MainActivity` owns all trusted controls. Up to eight untrusted Android WebViews render target content. Each has its own navigation, request list and console log, with shared WebView cookies/storage. Native controls evaluate JavaScript only on an explicit user action and receive string results through `evaluateJavascript`; no page-callable Java bridge exists.

`WebViewClient.shouldInterceptRequest` observes metadata and returns `null` so WebView continues its normal network stack. It is deliberately not presented as a complete network capture. `WebChromeClient` receives console messages.

`RequestSpec` validates HTTP(S) URLs, headers, method and body, and performs POSIX shell quoting for explicit curl export. `HttpRunner` runs off the UI thread with platform TLS, no redirects, 15-second connect/read timeouts, a 1 MiB response cap and no browser-cookie synchronization. Read timeout is an inactivity timeout, not an overall deadline. Native framing headers and duplicate headers are rejected.

`WorkspaceStore` saves a single request using AES-GCM and Android Keystore. `TermuxResultReceiver` receives only explicit PendingIntent callbacks. Termux commands originate solely from the native editor and require confirmation. Local Android shell commands run only when the user submits the native terminal input; LocalShell owns a persistent process and a background reader. No shell is exposed to website content. Results are memory-only. Termux lifecycle/battery management remains outside this app.

`LabServer` is a small loopback HTTP fixture with a random URL prefix, bounded request size, socket timeout and bounded worker queue. It serves a bundled page and an echo endpoint. It has no file or shell access endpoints and should never be exposed on a network interface.

## Next architecture decisions

Before implementing proxy interception, prove HTTPS CONNECT, per-device CA storage, upstream TLS verification and HTTP/2 behaviour on target devices. The WebView callback API alone cannot implement full interception. Select and audit a suitable proxy dependency before expanding scope.

For an interactive PTY bridge, keep authentication separate from target pages, restrict binding to loopback, validate origins, use per-session credentials, and provide a native stop control. A command-execution endpoint must never be exposed to WebView content. Do not embed Termux code without assessing its licenses and Android execution constraints.
