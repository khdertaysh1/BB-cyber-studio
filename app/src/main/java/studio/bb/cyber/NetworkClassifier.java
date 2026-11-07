package studio.bb.cyber;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort resource grouping for WebView request metadata.
 * This is NOT a Chrome DevTools resource-type detector: no response MIME
 * type, status, payload, initiator or timing is available in this callback.
 */
public final class NetworkClassifier {
    private NetworkClassifier() {}

    public static String classify(String url, boolean mainFrame, Map<String, String> headers) {
        if (mainFrame) return "Doc";
        String path;
        try {
            path = new URI(url).getPath();
        } catch (Exception e) {
            path = url;
        }
        if (path == null) path = "";
        path = path.toLowerCase(Locale.ROOT);
        if (matches(path, ".js", ".mjs", ".cjs")) return "JS";
        if (matches(path, ".css")) return "CSS";
        if (matches(path, ".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp", ".avif", ".ico", ".bmp")) return "Img";
        if (matches(path, ".woff", ".woff2", ".ttf", ".otf", ".eot")) return "Font";
        if (matches(path, ".json", ".graphql") || path.contains("/api/") || path.endsWith("/graphql")) return "API";
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (!"accept".equalsIgnoreCase(header.getKey())) continue;
                String accept = header.getValue().toLowerCase(Locale.ROOT);
                if (accept.contains("application/json") || accept.contains("application/graphql")) return "API";
            }
        }
        return "Other";
    }

    private static boolean matches(String path, String... suffixes) {
        for (String suffix : suffixes) if (path.endsWith(suffix)) return true;
        return false;
    }
}
