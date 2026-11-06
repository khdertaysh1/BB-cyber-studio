# Browser-first workspace (0.2)
Reference patterns: Firefox Android compact address toolbar and website tab management; Termux text-first terminal with an extra-key row. Original implementation and assets; no third-party branding or source copied.
- Website tabs stay separate from the developer tool selector. Up to eight live WebViews, independent histories, console logs and request lists; cookies/storage are shared.
- Browser owns the full content area. Tools default to full height; Split is explicit and survives rotation.
- Four fixed destinations: Browser, Tools, Terminal, Workspace.
- English controls/code are explicitly LTR on Arabic devices; page direction remains controlled by the website.
- Terminal uses a persistent Android shell with streamed output, working-directory state and command history. It is pipe-based, not a PTY; full-screen TUI programs and a Linux package environment still belong in the separate Termux app.
- New-tab page is bundled, offline and has no Java/native bridge. Only an explicit link opens the local lab.
References: https://support.mozilla.org/en-US/kb/toolbar-firefox-android ; https://support.mozilla.org/en-US/kb/manage-tabs-firefox-android ; https://github.com/termux/termux-tools/blob/master/termux.properties
