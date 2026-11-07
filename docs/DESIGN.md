# BB Cyber Studio 0.3 — mobile-first design

## Principle
The page is the primary content. The browser shows the address bar and three destinations. Network, Console, Repeater and Elements occupy a **bottom sheet**, with an Expand action. The page stays alive behind it. Analysis and saved requests remain available via the browser ⋮ menu, without taking permanent screen space.

Design patterns are informed by familiar browser chrome, Chrome DevTools Network tabs and Android navigation guidance, but all source code, layout and icons in BB Cyber Studio are original.

## Console
A short input followed by **Run** and **Copy output**. The output can also be selected and copied via Android text selection. Clear and reload are available only under ⋮ options.

## Repeater
A single method + URL row. Headers and Body start collapsed (their contents remain in the editor); **Send** and **Copy response** are the primary actions. Extra actions such as cURL export and comparison are under ⋮. An imported WebView request visibly indicates that cookies and POST bodies were not captured. Native requests do not follow redirects and validate normal TLS.

## Network
Request name / host / method, estimated resource type, unavailable status placeholder, live filter and compact detail tabs. The type is heuristic (path extension, main-frame flag, Accept). WebView request callbacks cannot supply complete response status, headers, bodies or timing; never render invented values. Redraw requests are batched on busy pages.

## Terminal and saved requests
Terminal retains a local Android sandbox shell and optional Termux integration; it is not a Linux PTY. Saved requests use AES-GCM and Android Keystore; the Browser menu provides access. The page cannot call native shell code; no JavaScript-native bridge is registered.

## Known platform constraints
Android WebView is not a Chromium browser fork. Login providers and sites requiring browser extensions, client certificates, popups or DRM may fail. Browser cookies/storage are shared across tabs, but are not automatically copied to the independent Repeater.
