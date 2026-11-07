package studio.bb.cyber;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Persistent Android shell with streamed output. Pipe transport, not a PTY. */
final class LocalShell implements AutoCloseable {
    interface Output { void accept(String text); }
    private final Process process;
    private final Writer input;
    private volatile boolean closed;
    LocalShell(File directory, Output output) throws IOException {
        ProcessBuilder builder=new ProcessBuilder("/system/bin/sh").directory(directory).redirectErrorStream(true);
        builder.environment().put("HOME",directory.getAbsolutePath());
        builder.environment().put("TERM","dumb");
        process=builder.start();input=new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8);
        Thread reader=new Thread(()->{
            try(Reader stream=new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8)){
                char[] buffer=new char[2048];int n;
                while((n=stream.read(buffer))!=-1)output.accept(new String(buffer,0,n));
                if(!closed)output.accept("\n[Shell exited. Tap New session to restart.]\n");
            }catch(IOException e){if(!closed)output.accept("\n[Shell disconnected]\n");}
        },"bb-shell-output");reader.setDaemon(true);reader.start();
    }
    synchronized void send(String command) throws IOException {
        if(closed||!process.isAlive())throw new IOException("Session ended. Tap New session.");
        input.write(command);input.write("\n");input.flush();
    }
    @Override public synchronized void close(){
        closed=true;try{input.close();}catch(IOException ignored){}process.destroy();
    }
}
