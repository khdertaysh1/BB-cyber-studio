package studio.bb.cyber;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Shared validation for native requests and explicit shell export. */
public final class RequestSpec {
    public final String method, url, body;
    public final LinkedHashMap<String, String> headers;
    public RequestSpec(String method, String url, String headerText, String body) {
        this.method = method.trim().toUpperCase(Locale.ROOT);
        if (!this.method.matches("GET|POST|PUT|DELETE|HEAD|OPTIONS"))
            throw new IllegalArgumentException("Supported methods: GET, POST, PUT, DELETE, HEAD, OPTIONS");
        this.url = validateUrl(url);
        this.body = body == null ? "" : body;
        if ((this.method.equals("GET") || this.method.equals("HEAD")) && !this.body.isEmpty())
            throw new IllegalArgumentException("GET / HEAD body is unsupported; clear the body or choose another method.");
        if (this.body.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024)
            throw new IllegalArgumentException("Request body exceeds the 1 MiB limit.");
        headers = parseHeaders(headerText);
    }
    public static String validateUrl(String value) {
        String clean = value.trim();
        if (clean.matches("(?s).*[\\x00-\\x20\\x7f].*"))
            throw new IllegalArgumentException("URL contains whitespace or control characters.");
        URI uri;
        try { uri = URI.create(clean); } catch (Exception e) {
            throw new IllegalArgumentException("Invalid URL.");
        }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getHost().isEmpty())
            throw new IllegalArgumentException("Use a complete http:// or https:// URL.");
        if (uri.getRawUserInfo() != null) throw new IllegalArgumentException("Put credentials in headers, not the URL.");
        if (uri.getRawFragment() != null) throw new IllegalArgumentException("Remove the #fragment from the request URL.");
        if (uri.getPort() > 65535 || uri.getPort() == 0) throw new IllegalArgumentException("Invalid port.");
        return clean;
    }
    public static LinkedHashMap<String, String> parseHeaders(String text) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (text == null || text.trim().isEmpty()) return result;
        for (String line : text.split("\n", -1)) {
            if (line.endsWith("\r")) line = line.substring(0, line.length()-1);
            if (line.trim().isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("Each header must be Name: value.");
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon+1).trim();
            if (!key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value.matches("(?s).*[\\x00-\\x08\\x0a-\\x1f\\x7f].*"))
                throw new IllegalArgumentException("Invalid header characters.");
            if (key.matches("(?i)host|content-length|transfer-encoding|connection|expect|upgrade"))
                throw new IllegalArgumentException(key + " is managed by the HTTP client. Remove it.");
            for (String existing : result.keySet())
                if (existing.equalsIgnoreCase(key)) throw new IllegalArgumentException("Duplicate header: " + key);
            result.put(key, value);
        }
        return result;
    }
    public static String quoteShell(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }
    public String toCurl() {
        StringBuilder out = new StringBuilder("curl --globoff --max-time 30 ");
        if (method.equals("HEAD")) out.append("--head ");
        else out.append("--request ").append(quoteShell(method)).append(' ');
        for (Map.Entry<String,String> h : headers.entrySet())
            out.append("\\\n  --header ").append(quoteShell(h.getKey()+": "+h.getValue())).append(' ');
        if (!body.isEmpty()) out.append("\\\n  --data-raw ").append(quoteShell(body)).append(' ');
        return out.append("\\\n  --url ").append(quoteShell(url)).toString();
    }
}
