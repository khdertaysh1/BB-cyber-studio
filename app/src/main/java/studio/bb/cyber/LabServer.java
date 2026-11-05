package studio.bb.cyber;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small loopback-only demo, protected by an unguessable per-launch URL. No file serving. */
public final class LabServer implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService acceptor=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ThreadPoolExecutor clients=new java.util.concurrent.ThreadPoolExecutor(
        2,2,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(8));
    private final String prefix="/"+UUID.randomUUID()+"/";
    private final byte[] page;
    public LabServer(byte[] page) throws IOException {
        this.page=page;
        server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        acceptor.execute(()->{
            while(!server.isClosed()) {
                try {
                    Socket socket=server.accept();
                    try { clients.execute(()->serve(socket)); }
                    catch(java.util.concurrent.RejectedExecutionException e) { socket.close(); }
                } catch(IOException ignored) { break; }
            }
        });
    }
    public String url() { return "http://127.0.0.1:"+server.getLocalPort()+prefix; }
    private void serve(Socket socket) {
        try(Socket current=socket) {
            current.setSoTimeout(5000);
            InputStream input=current.getInputStream();
            String first=line(input);
            String[] request=first.split(" ");
            if(request.length!=3) { respond(current,400,"text/plain","Invalid request".getBytes(StandardCharsets.UTF_8)); return; }
            int length=0; String header; int total=first.length(); boolean chunked=false;
            while(!(header=line(input)).isEmpty()) {
                total+=header.length(); if(total>16384) throw new IOException("Headers too large");
                String lower=header.toLowerCase(Locale.ROOT);
                if(lower.startsWith("content-length:")) length=Integer.parseInt(header.substring(15).trim());
                if(lower.startsWith("transfer-encoding:")) chunked=true;
            }
            if(length<0||length>65536||chunked) { respond(current,400,"text/plain", "Unsupported body".getBytes(StandardCharsets.UTF_8)); return; }
            byte[] body=new byte[length]; int offset=0;
            while(offset<length) { int count=input.read(body,offset,length-offset); if(count<0) throw new EOFException(); offset+=count; }
            if(!request[1].startsWith(prefix)) { respond(current,404,"text/plain","Not found".getBytes(StandardCharsets.UTF_8)); return; }
            if(request[1].equals(prefix) && request[0].equals("GET")) { respond(current,200,"text/html; charset=utf-8",page); return; }
            if(request[1].equals(prefix+"echo") || request[1].startsWith(prefix+"echo?")) {
                String json="{\"method\":"+json(request[0])+",\"path\":"+json(request[1].substring(prefix.length()))
                    +",\"body\":"+json(new String(body,StandardCharsets.UTF_8))+",\"source\":\"BB local lab\"}";
                respond(current,200,"application/json; charset=utf-8",json.getBytes(StandardCharsets.UTF_8)); return;
            }
            respond(current,404,"text/plain","Not found".getBytes(StandardCharsets.UTF_8));
        } catch(Exception ignored) { /* Malformed demo requests close the connection. */ }
    }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(int c; (c=input.read())!=-1;) {
            if(c=='\n') return out.toString("UTF-8").replace("\r","");
            out.write(c); if(out.size()>8192) throw new IOException("Line too large");
        }
        throw new EOFException();
    }
    private static void respond(Socket socket,int status,String type,byte[] bytes) throws IOException {
        OutputStream output=socket.getOutputStream();
        output.write(("HTTP/1.1 "+status+" "+(status==200?"OK":status==404?"Not Found":"Bad Request")
            +"\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length
            +"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(bytes); output.flush();
    }
    private static String json(String text) {
        StringBuilder result=new StringBuilder("\"");
        for(char c:text.toCharArray()) {
            if(c=='"'||c=='\\') result.append('\\').append(c);
            else if(c<32) result.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
    @Override public void close() throws IOException { server.close(); acceptor.shutdownNow(); clients.shutdownNow(); }
}
