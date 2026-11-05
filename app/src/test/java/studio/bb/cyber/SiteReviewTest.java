package studio.bb.cyber;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SiteReviewTest {
    @Test public void cookieValuesNeverEnterReport(){
        Map<String,List<String>> headers=new HashMap<>();
        headers.put("set-cookie",Arrays.asList("session=secret123; Secure; HttpOnly; SameSite=Lax","theme=dark; Path=/"));
        String report=SiteReview.summarize("https://example.com/",200,headers);
        assertFalse(report.contains("secret123"));assertFalse(report.contains("theme=dark"));
        assertTrue(report.contains("session · Secure=true · HttpOnly=true · SameSite=Lax"));
    }
    @Test public void reportOnlyIsNotConfusedWithEnforcement(){
        Map<String,List<String>> headers=new HashMap<>();headers.put("Content-Type",Collections.singletonList("text/html"));
        headers.put("Content-Security-Policy-Report-Only",Collections.singletonList("default-src 'none'"));
        String report=SiteReview.summarize("https://example.com/",200,headers);
        assertTrue(report.contains("Enforced CSP header not observed"));assertTrue(report.contains("does not enforce blocking"));
    }
    @Test public void localHttpDoesNotClaimHttps(){String report=SiteReview.summarize("http://127.0.0.1/",200,Collections.emptyMap());assertTrue(report.contains("plaintext HTTP"));assertFalse(report.contains("validation succeeded"));}
}
