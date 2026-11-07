# BB Cyber Studio roadmap

## Completed in v0.4
- [x] Google as default web search in both address bar and clean new-tab page
- [x] Remove local lab and example shortcuts from public start/menu UI
- [x] Console Run and explicit Copy latest evaluated result; Copy all logs in overflow
- [x] Elements inspect and copy DOM HTML; CSS editor hidden by default
- [x] Selectable local Android vs external Termux command mode
- [x] Detect pkg/apt in Android shell and explain that they require Termux
- [x] Consistent rounded DevTools toolbar tabs and spacing
- [x] Automated tests for copy-to-clipboard and UI behaviours

## Existing capabilities
- [x] Live browser with eight WebView tabs and a DevTools bottom sheet
- [x] Observed request metadata list, filters and estimated categories
- [x] Native HTTP Repeater with TLS verification and limited response size
- [x] JavaScript evaluation and DOM/CSS inspector
- [x] Native Android pipe shell, explicit Termux RUN_COMMAND handoff
- [x] Encrypted saved HTTP request

## Not implemented
- [ ] Fully intercepted HTTP(S) proxy (upstream TLS verified, per-device CA trust)
- [ ] Original browser request/response bodies and complete response headers/status
- [ ] Interactive embedded PTY and Linux package environment
- [ ] WebSocket frame viewer, HAR export and isolated browser profiles
- [ ] Stable release signing, Arabic translation and real-device acceptance testing
