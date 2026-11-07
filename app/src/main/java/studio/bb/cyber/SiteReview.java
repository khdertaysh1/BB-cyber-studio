package studio.bb.cyber;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/** Observational checks, never an exploit verdict or a security score. */
public final class SiteReview {
    public static String inspect(String url) throws Exception {
        RequestSpec.validateUrl(url);
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
            connection.setUseCaches(false);connection.setRequestMethod("GET");connection.setRequestProperty("User-Agent","BB-Cyber-Studio/0.1 (explicit header review)");
            int status=connection.getResponseCode();
            return summarize(url,status,connection.getHeaderFields());
        } finally {connection.disconnect();}
    }
    private static String header(Map<String,List<String>> headers,String name){
        StringBuilder out=new StringBuilder();for(Map.Entry<String,List<String>> h:headers.entrySet())if(h.getKey()!=null&&h.getKey().equalsIgnoreCase(name))for(String value:h.getValue()){if(out.length()>0)out.append("; ");out.append(value);}return out.toString();
    }
    public static String summarize(String url,int status,Map<String,List<String>> headers){
        StringBuilder out=new StringBuilder("HEADER REVIEW\n").append(url).append("\nHTTP ").append(status)
            .append("\n\nSeparate unauthenticated GET. Redirects are not followed. Browser cookies are not sent. Missing headers are review items, not confirmed vulnerabilities.\n\n");
        boolean https=url.regionMatches(true,0,"https://",0,8);
        out.append(https?"✓ Transport uses HTTPS; platform certificate validation succeeded.\n":"! Transport uses plaintext HTTP. Local development endpoints may intentionally use HTTP.\n");
        if(status>=300&&status<400)out.append("→ Redirect response. Review its destination separately: ").append(header(headers,"Location")).append('\n');
        String type=header(headers,"Content-Type");boolean html=type.toLowerCase(java.util.Locale.ROOT).contains("text/html");
        out.append("\nContent-Type: ").append(type.isEmpty()?"not supplied":type).append('\n');
        String csp=header(headers,"Content-Security-Policy");
        out.append(csp.isEmpty()?(html?"! Enforced CSP header not observed for this HTML response. A meta policy may exist.\n":"· CSP header not observed; relevance depends on document type.\n"):"✓ CSP header present; policy strength has not been validated.\n");
        if(!header(headers,"Content-Security-Policy-Report-Only").isEmpty())out.append("· Report-only CSP is present; it does not enforce blocking.\n");
        if(https)out.append(header(headers,"Strict-Transport-Security").isEmpty()?"! HSTS header not observed on this response.\n":"✓ HSTS header present; review max-age and domain coverage.\n");
        String nosniff=header(headers,"X-Content-Type-Options");
        out.append("nosniff".equalsIgnoreCase(nosniff.trim())?"✓ MIME sniffing protection observed.\n":"! X-Content-Type-Options: nosniff not observed.\n");
        if(html)out.append(header(headers,"X-Frame-Options").isEmpty()&&!csp.toLowerCase(java.util.Locale.ROOT).contains("frame-ancestors")?"! No frame-embedding restriction observed in response headers. Embedding may be intentional.\n":"✓ A frame-embedding policy is present; effectiveness needs review.\n");
        String origin=header(headers,"Access-Control-Allow-Origin"),credentials=header(headers,"Access-Control-Allow-Credentials");
        if(!origin.isEmpty())out.append("\nCORS origin: ").append(origin).append("\nCORS credentials: ").append(credentials.isEmpty()?"not supplied":credentials).append("\nA permissive header alone does not establish data exposure. Browsers reject credentialed wildcard-origin responses.\n");
        out.append("\nSET-COOKIE ATTRIBUTES (values omitted)\n");int cookies=0;
        for(Map.Entry<String,List<String>> h:headers.entrySet())if(h.getKey()!=null&&h.getKey().equalsIgnoreCase("Set-Cookie"))for(String cookie:h.getValue()){
            cookies++;String name=cookie.split("=",2)[0];String[] parts=cookie.split(";");boolean secure=false,httpOnly=false;String sameSite="not specified";
            for(int i=1;i<parts.length;i++){String part=parts[i].trim();if(part.equalsIgnoreCase("Secure"))secure=true;if(part.equalsIgnoreCase("HttpOnly"))httpOnly=true;if(part.toLowerCase(java.util.Locale.ROOT).startsWith("samesite="))sameSite=part.substring(9);}
            out.append(name).append(" · Secure=").append(secure).append(" · HttpOnly=").append(httpOnly).append(" · SameSite=").append(sameSite).append('\n');
        }
        if(cookies==0)out.append("No Set-Cookie header in this response.\n");
        out.append("\nCookie requirements depend on purpose; JavaScript-readable non-session cookies can be intentional.\n\nSELECTED RESPONSE HEADERS\n");
        for(String name:new String[]{"Content-Type","Content-Security-Policy","Content-Security-Policy-Report-Only","Strict-Transport-Security","X-Frame-Options","X-Content-Type-Options","Referrer-Policy","Permissions-Policy","Server"}){String value=header(headers,name);if(!value.isEmpty())out.append(name).append(": ").append(value).append('\n');}
        return out.toString();
    }
}
