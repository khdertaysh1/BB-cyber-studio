# BB Cyber Studio 0.3 — Android alpha (preview)

## Browser-first usability
- Website takes the full browser area until Tools is opened.
- Live DevTools appear as a compact, expandable bottom sheet instead of replacing the whole page.
- Bottom navigation reduced to Browser, Tools and Terminal.
- Saved Request and Page Analysis moved to the browser menu.

## Tools
- Console: Run + Copy output; Android text selection also available.
- Network: compact observed-request table, resource grouping, quick filters and batched rendering on busy websites.
- Repeater: method/URL, collapsible Headers and Body, Send and Copy response. Extras live under ⋮.
- The metadata-import notice is a quiet in-panel explanation rather than an alarming toast.
- Local Android shell retains streaming output, working-directory state and optional Termux integration.

## Limits
This is **not** Burp Suite or complete Chrome DevTools. WebView does not expose the full browser HTTP response, original POST body, Cookie header, WebSocket frames, detailed initiator or timings through its request callback. Repeater makes its own HTTP requests; it does not reuse the browser's authenticated cookie jar. Do not treat missing response data as evidence of a vulnerability.

No root, hosted service, paid backend, telemetry or unsafe TLS override. The built-in shell uses Android sandbox commands, not a full Linux PTY. Termux must be separately installed and authorized.

The CI APK uses a debug signing identity. An install over a different CI build may fail due to signing mismatch; uninstalling deletes the app's local data. Only releases from the main branch are published, after CI passes.
