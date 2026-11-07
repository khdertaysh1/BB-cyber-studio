# BB Cyber Studio roadmap

## 0.3 mobile-first Android workspace — implemented
- [x] Browser-first WebView fills the available viewport
- [x] DevTools bottom sheet above the live page, with expand/collapse
- [x] Three top-level destinations: Browser, Tools, Terminal
- [x] JavaScript Console Run/Copy output, selectable text; Clear under ⋮
- [x] Network resource grouping, filtering, details, batching for busy websites
- [x] HTTP Repeater with collapsible headers/body and response copying
- [x] Saved encrypted request moved from bottom navigation into browser menu
- [x] Android 15 emulator test coverage and GitHub APK CI

## Existing foundation
- [x] Up to eight website tabs, WebView session cookies shared across tabs
- [x] Separate per-tab request metadata and console histories
- [x] Local HTTP echo lab; independent native GET/POST/PUT/DELETE/HEAD/OPTIONS client
- [x] DOM selector/style inspector; page map and contextual header review
- [x] Pipe-based local Android shell with command history
- [x] Optional explicit Termux RUN_COMMAND handoff
- [x] AES-GCM saved-request storage using Android Keystore

## Unimplemented and requiring architectural work
- [ ] Verified HTTP(S) interception proxy and scoped CA trust
- [ ] Actual browser response headers/status/body/timing capture and replay
- [ ] WebSocket frames and HAR import/export
- [ ] Interactive embedded PTY, completion and full Linux package environment
- [ ] Per-tab isolated cookie profiles and session persistence across process death
- [ ] Stable release signing and in-place upgrade compatibility
- [ ] Arabic localization and tablet-specific adaptive layouts

Do not mistake network observation for Chrome DevTools protocol capture, or an independent HTTP replay for the original browser request.
