# Verification

## Automated

`gradle testDebugUnitTest lintDebug assembleDebug`

JVM tests cover request scheme/credential validation, header injection and ambiguity, shell quoting, request size limits, real loopback GET/POST, unavailable lab paths, redirect non-following, response caps and binary handling. Android lint and Java compilation are required before the APK artifact is uploaded.

Additional tests cover cookie-value omission and report-only CSP handling. The `device-smoke` job runs Android 15 emulator tests for launch, local browser/console, Repeater, DOM inspection, page analysis and Android Keystore saving. It uploads screenshots and test reports. The separate Termux application is not installed by these tests; its real-device integration still needs verification.

## Device acceptance (not implied by a green JVM build)

Test on Android 8+ and Android 15 with a recent WebView:

- Launch; Local lab loads without network access. GET and POST buttons show actual JSON.
- Network shows requests; pause stops recording; URL/method filter works; clear empties it.
- Transfer GET to Repeater; verify JSON, modify query, compare two responses.
- Transfer POST; verify the missing-body notice; manually enter JSON and Content-Type before sending.
- Console evaluates expressions and catches errors. Lab logs appear. Elements edits the selected DOM node.
- Invalid TLS fails; no bypass prompt. Unsupported navigation schemes do not open external apps.
- Save and load a request; restart; verify encrypted load. Delete saved request. Clear browser cookies/storage.
- Termux: missing installation, denied permission, setup disabled, stdout, stderr, nonzero shell exit, successful internal `err=-1`, lifecycle return, very large output.
- Rotate during a request; ensure no callback crashes. Rotation keeps the active workspace and switches the console/DOM layout to side-by-side panels in landscape. Process death still loses unsaved in-memory state.
- Small screen, large font, keyboard open and landscape: controls remain reachable using scrolling.

The Termux handoff is completion-based. The local Android shell streams output, keeps cwd and supports line input and session stop; it is not a PTY. Device-specific execution and the UI need real-device testing in addition to CI.
