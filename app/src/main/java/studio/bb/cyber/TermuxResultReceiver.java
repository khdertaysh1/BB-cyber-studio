package studio.bb.cyber;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Explicit, non-exported PendingIntent destination. Results stay in memory. */
public final class TermuxResultReceiver extends BroadcastReceiver {
    public interface Listener { void onResult(String result); }
    static volatile Listener listener;
    static volatile String lastResult = "No command result yet.";
    @Override public void onReceive(Context context, Intent intent) {
        Bundle result=intent.getBundleExtra("result");
        if(result==null) { lastResult="Termux returned no result bundle."; }
        else {
            lastResult="Exit: "+result.getInt("exitCode",-1)+"\n"+clip(result.getString("stdout",""))
                +"\n"+clip(result.getString("stderr",""));
            if(result.getInt("err",-1)!=-1) lastResult+="\nTermux error: "+result.getInt("err")+" "+clip(result.getString("errmsg",""));
        }
        Listener current=listener; if(current!=null) current.onResult(lastResult);
    }
    private static String clip(String s) { if(s==null)return "";return s.length()>60000?s.substring(0,60000)+"\n[Output truncated]":s; }
}
