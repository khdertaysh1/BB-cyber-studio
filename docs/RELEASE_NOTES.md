# BB Cyber Studio v0.4.0 Alpha — Google, Elements and Console UX

Browser-first Android application for no-root research. New tab and address bar search with Google, and the public Local Lab/Example shortcuts are gone.

Console offers only Run and Copy for core actions; Copy grabs the last evaluated JavaScript result, and Tools ⋮ can copy all logs. Elements now focuses on inspecting HTML with explicit Copy HTML and expandable temporary CSS editing. Terminal mode is explicitly Android or external Termux; package commands such as pkg update require Termux and are not run in the Android app shell.

The UI retains its expandable DevTools sheet and independent HTTP Repeater. Original browser request/response body, TLS interception, HAR, embedded Linux PTY and complete Chrome DevTools features are NOT available.

The build pipeline runs JVM unit tests, Android lint, APK compile and Android 15 emulator smoke tests. A green CI build does not guarantee real-device Termux integration; validate on the intended phone. This is debug-signed and may not install over an older build with a different certificate.
