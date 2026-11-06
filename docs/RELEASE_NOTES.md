## 0.2 browser-first redesign
Compact address bar, up to 8 website tabs with separate console/request histories, full-page tools with optional split mode, and fixed bottom navigation. English controls remain LTR on Arabic devices. Cookies and website storage are shared across tabs.

The Terminal now includes a persistent local Android shell with live output, command history and working-directory state. It uses pipes, not a PTY: no bundled Linux packages, Python, or full-screen interactive programs. The separate Termux integration remains available. APK size stays small because Android supplies WebView and the system shell.

# BB Cyber Studio · Android alpha

Download **BB-cyber-studio.apk** below. Android 8.0+; no root required. No account or cloud backend. Termux is an optional separate installation.

This foundation includes a browser, JavaScript console, DOM/style inspector, observed request metadata, HTTP Repeater, response comparison, page analysis, Termux command results, encrypted request saving and a local demo lab. Landscape mode preserves the workspace.

**Try it:** Local lab → Send GET → Network → select the echo request → To Repeater → Send request.

## Current limits

- This is not full Burp Suite or Chrome DevTools. Full HTTPS interception, browser request/response body capture, interactive embedded PTY, WebSocket inspection and isolated profiles are not implemented.
- Network lists available WebView request metadata. Repeater performs separate HTTP requests; browser cookies are not automatically shared.
- Header analysis is a separate unauthenticated GET. Missing headers are observations, not proof of a vulnerability.
- Some websites and login providers reject embedded WebViews. No universal website compatibility is claimed.
- Termux integration requires `allow-external-apps=true` and its Android permission; output arrives when commands finish. It still needs testing on physical Termux installations.
- UI is currently English; Arabic localization is planned.

This **development APK** uses a CI debug signing key. Updates from another build may require uninstalling the old app, deleting its local saved data. A stable release signing setup is not included yet. JVM tests, Android lint, APK compilation and Android 15 emulator smoke tests gate this prerelease; they do not establish compatibility with every device or website.

بالعربي: نزّل **BB-cyber-studio.apk** وثبّته. جرّب المختبر المحلي أولاً. هذه نسخة أولية حقيقية، لكن اعتراض HTTPS الكامل والطرفية التفاعلية المدمجة لم يُنجزا بعد. نتائج تحليل الترويسات ملاحظات تحتاج مراجعة، وليست إثباتاً لثغرات.
