# BB Cyber Studio v0.4 design

The browser is always the main surface. A compact dark toolbar and three-icon bottom navigation leave room for the live page. Tools open as a rounded, expandable bottom sheet. The user asked for reduced button clutter and mobile-first controls.

The start page now has a single clear Google search field and no demonstration shortcuts. The Console exposes Run and Copy only; the copy action targets the latest *evaluated result* for the active tab, not the entire event history. The overflow provides Copy all logs and Clear. Elements has a CSS selector, Inspect HTML, Copy HTML, and an optional CSS editor in a collapsed area. The terminal uses an explicit Android/Termux mode, avoiding the false impression that Android's system shell contains Termux packages.

Colors are restrained navy/blue, with unified rounded controls, compact headers and readable text. These UI components are original, not copies of another browser's assets.

Security model is unchanged: only explicit user actions evaluate JavaScript or execute shell commands. The application does not expose a JavaScript-native bridge and does not relax TLS certificate verification. Request metadata observation is not an HTTPS proxy.
