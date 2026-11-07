package studio.bb.cyber;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequestSpecTest {
    @Test public void rejectsNonHttpAndUrlCredentials(){
        for(String url:new String[]{"file:///etc/passwd","javascript:alert(1)","https://user:secret@example.com/","https://example.com/a\nb","https://example.com/#secret","https://example.com:70000"}){
            assertThrows(IllegalArgumentException.class,()->new RequestSpec("GET",url,"",""));
        }
    }
    @Test public void rejectsAmbiguousOrInjectedHeaders(){
        for(String headers:new String[]{"Host: other.example","Content-Length: 4","Transfer-Encoding: chunked","X: one\rInjected: two","X-Test: a\nx-test: b","not a header"})
            assertThrows(IllegalArgumentException.class,()->RequestSpec.parseHeaders(headers));
    }
    @Test public void preservesColonAndCrLf(){assertEquals("Bearer a:b",RequestSpec.parseHeaders("Authorization: Bearer a:b\r\nAccept: application/json\r\n").get("Authorization"));}
    @Test public void refusesBodiesForGetAndHead(){for(String method:new String[]{"GET","HEAD"})assertThrows(IllegalArgumentException.class,()->new RequestSpec(method,"http://127.0.0.1/","","body"));}
    @Test public void curlTreatsShellCharactersAsData(){
        RequestSpec request=new RequestSpec("POST","https://example.com/","X-Test: $(touch /tmp/no)","it's `not` a command; $HOME");
        assertTrue(request.toCurl().contains("--header 'X-Test: $(touch /tmp/no)'"));
        assertTrue(request.toCurl().contains("--data-raw 'it'\"'\"'s `not` a command; $HOME'"));
        assertFalse(request.toCurl().contains("--location"));
    }
    @Test public void headUsesHeadFlag(){assertTrue(new RequestSpec("HEAD","https://example.com/","","").toCurl().contains("--head"));}
    @Test public void capsRequestSize(){assertThrows(IllegalArgumentException.class,()->new RequestSpec("POST","https://example.com/","",new String(new char[1024*1024+1])));}
}
