package studio.bb.cyber;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native HTTP client: platform TLS verification, no automatic redirect or cookie sharing. */
public final class HttpRunner {
    public static final int MAX_RESPONSE = 1024 * 1024;
    public static String execute(RequestSpec spec) throws Exception {
        long start = System.nanoTime();
        HttpURLConnection connection = (HttpURLConnection) new URL(spec.url).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod(spec.method);
            for (Map.Entry<String,String> h : spec.headers.entrySet()) connection.setRequestProperty(h.getKey(), h.getValue());
            if (!spec.body.isEmpty()) {
                connection.setDoOutput(true);
                byte[] data = spec.body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(data.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(data); }
            }
            int status = connection.getResponseCode();
            StringBuilder result = new StringBuilder("HTTP ").append(status).append(' ').append(connection.getResponseMessage()).append('\n');
            for (Map.Entry<String,List<String>> h : connection.getHeaderFields().entrySet())
                if (h.getKey() != null) for (String value : h.getValue()) result.append(h.getKey()).append(": ").append(value).append('\n');
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            boolean truncated = false;
            if (stream != null) try (InputStream input = stream) {
                byte[] buffer = new byte[8192];
                while (true) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Cancelled");
                    int count = input.read(buffer, 0, Math.min(buffer.length, MAX_RESPONSE + 1 - data.size()));
                    if (count < 0) break;
                    data.write(buffer,0,count);
                    if (data.size() > MAX_RESPONSE) { truncated = true; break; }
                }
            }
            result.append("\nElapsed: ").append((System.nanoTime()-start)/1000000).append(" ms | Read: ")
                    .append(Math.min(data.size(), MAX_RESPONSE)).append(" bytes\n\n");
            String type = connection.getContentType();
            if (type == null) type = "";
            if (type.startsWith("image/") || type.startsWith("audio/") || type.startsWith("video/") || type.contains("octet-stream") || type.contains("application/pdf")) {
                result.append("[Binary response; text preview unavailable]");
            } else {
                Charset charset = StandardCharsets.UTF_8;
                Matcher matcher = Pattern.compile("charset=\\\"?([^;\\\"\\s]+)", Pattern.CASE_INSENSITIVE).matcher(type);
                if (matcher.find()) try { charset = Charset.forName(matcher.group(1)); } catch (Exception ignored) { }
                result.append(new String(data.toByteArray(),0,Math.min(data.size(),MAX_RESPONSE),charset));
            }
            if (truncated) result.append("\n\n[Truncated at 1 MiB]");
            return result.toString();
        } finally { connection.disconnect(); }
    }
}
