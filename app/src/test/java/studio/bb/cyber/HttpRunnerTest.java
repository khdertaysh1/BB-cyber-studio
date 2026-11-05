package studio.bb.cyber;
import org.junit.Test;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class HttpRunnerTest {
    @Test public void localLabSupportsActualGetAndPost() throws Exception {
        try(LabServer lab=new LabServer("<h1>Lab</h1>".getBytes(StandardCharsets.UTF_8))){
            String get=HttpRunner.execute(new RequestSpec("GET",lab.url()+"echo?hello=1","",""));
            assertTrue(get.contains("HTTP 200"));assertTrue(get.contains("\"method\":\"GET\""));assertTrue(get.contains("echo?hello=1"));
            String post=HttpRunner.execute(new RequestSpec("POST",lab.url()+"echo","Content-Type: text/plain","hello \"BB\"\n"));
            assertTrue(post.contains("\"method\":\"POST\""));assertTrue(post.contains("hello \\\"BB\\\"\\u000a"));
            String denied=HttpRunner.execute(new RequestSpec("GET",lab.url().replaceAll("/[a-f0-9-]+/$","/wrong/"),"",""));
            assertTrue(denied.contains("HTTP 404"));
        }
    }
    private String response(String raw) throws Exception {
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<?> served=executor.submit(()->{try(Socket client=server.accept()){
                client.setSoTimeout(5000);BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));
                for(String line;(line=input.readLine())!=null&&!line.isEmpty();){}
                client.getOutputStream().write(raw.getBytes(StandardCharsets.UTF_8));
            }catch(IOException e){throw new RuntimeException(e);}});
            try{
                String result=HttpRunner.execute(new RequestSpec("GET","http://127.0.0.1:"+server.getLocalPort()+"/","",""));served.get(10,TimeUnit.SECONDS);return result;
            }finally{executor.shutdownNow();}
        }
    }
    @Test public void redirectsAreNotFollowed() throws Exception {
        String result=response("HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:1/never\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");assertTrue(result.contains("HTTP 302"));
    }
    @Test public void capsLargeResponses() throws Exception {
        String body=new String(new char[HttpRunner.MAX_RESPONSE+5]).replace('\0','x');
        String result=response("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: "+body.length()+"\r\nConnection: close\r\n\r\n"+body);
        assertTrue(result.contains("[Truncated at 1 MiB]"));
    }
    @Test public void binaryIsNotRenderedAsText() throws Exception {
        assertTrue(response("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: 3\r\n\r\nabc").contains("[Binary response"));
    }
}
