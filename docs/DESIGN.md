# BB Cyber Studio — Browser-first UI / Network review

## Reference patterns
- Kiwi Browser on Android: familiar address-and-tab affordances and DevTools on a phone.
- Chrome DevTools Network: filter chips, request table, row details, Headers/Payload/Response.
- Firefox Android: compact browser chrome, separate tab management.

Reference behavior and general navigation patterns only. All BB Cyber Studio layout/code/icons are original; no third-party assets, branding, screenshots or copyrighted source are copied.

## Navigation
The default Browser surface shows one compact address toolbar (back, URL/search, reload, tab count, menu) and the actual WebView filling the available content area. The horizontal tab strip is hidden until the tab counter is selected. Four fixed destinations remain at the bottom: Browser, Tools, Terminal, Workspace. DevTools appear only when Tools is selected; full-height by default, optional split mode. English UI remains LTR with Arabic device locale. Both landscape and portrait use the same underlying browser state.

## Network
The Network tab is a DevTools-inspired **metadata viewer**, not a full Chrome DevTools protocol connection or a TLS interception proxy. It provides:

- A recording pause/resume control and clear/reload.
- URL/method text search, and All/Doc/API/JS/CSS/Img/Font/Other type chips.
- Request rows with resource name, method/host, best-effort category and an honest **—** unavailable status.
- General, Headers, Payload and Response detail tabs; Response explains why the browser body is unavailable.
- User-initiated export of observed metadata to the independent Repeater (browser cookies/POST bodies are not copied).

Categories are **heuristics** inferred from request URL extensions, main-frame flag and the Accept header. They are not Chromium request initiator classifications. Unknown resource type is Other. WebView does not provide response status, bytes, waterfall timing, response headers/bodies or all navigation types through `shouldInterceptRequest`; we never manufacture them.

## Accessibility and limits
Touch targets use a minimum 40dp chrome size, network rows 67dp, truncated long URLs, and content descriptions for tab controls. The app has no JavaScript-to-native bridge, doesn't intercept credentials, and doesn't trust invalid TLS. The local Android shell remains pipe-based; Termux is an optional external app.

## Next milestone
A tested local proxy approach with carefully scoped TLS trust, plus real-device screenshots for small phone / large font / landscape, is required before claiming parity with Kiwi/Chrome DevTools.