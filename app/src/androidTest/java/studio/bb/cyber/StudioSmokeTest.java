package studio.bb.cyber;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StudioSmokeTest {
    private View find(View view,String text,boolean button) {
        if(view instanceof TextView && (!button||view instanceof Button) && ((TextView)view).getText().toString().equals(text))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),text,button);if(found!=null)return found;}
        return null;
    }
    private View findDescription(View view,String text){
        if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;
    }
    private boolean contains(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
    private void tap(ActivityScenario<MainActivity> scenario,String text){scenario.onActivity(a->{if(java.util.Arrays.asList("Console","Network","Elements","Repeater","Analyze").contains(text)){View tools=find(a.getWindow().getDecorView(),"Tools",true);if(tools!=null)tools.performClick();}View b=find(a.getWindow().getDecorView(),text,true);assertNotNull(text,b);b.performClick();});}
    private void waitFor(ActivityScenario<MainActivity> scenario,String text) throws Exception {
        long deadline=System.currentTimeMillis()+10000;boolean[] found={false};
        while(System.currentTimeMillis()<deadline){scenario.onActivity(a->found[0]=contains(a.getWindow().getDecorView(),text));if(found[0])return;Thread.sleep(100);}
        fail("Timed out waiting for "+text);
    }
    private void shellCommand(String command) throws Exception {
        android.os.ParcelFileDescriptor pipe=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe)){byte[] data=new byte[512];while(in.read(data)!=-1){}}
    }
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(150);
        shellCommand("mkdir -p /data/local/tmp/bb-shots");
        shellCommand("screencap -p /data/local/tmp/bb-shots/"+name+".png");
    }
    @Test public void browserConsoleRepeaterAndPanelsWork() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor(scenario,"observed requests");screenshot("00-start");
            scenario.onActivity(a->a.openLab());Thread.sleep(700);waitFor(scenario,"observed requests");screenshot("01-browser");
            tap(scenario,"Console");tap(scenario,"Evaluate");waitFor(scenario,"BB · Local lab");screenshot("02-console");
            scenario.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            Thread.sleep(1200);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            tap(scenario,"Evaluate");waitFor(scenario,"BB · Local lab");screenshot("02-console-landscape");
            scenario.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            Thread.sleep(1200);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            tap(scenario,"Repeater");tap(scenario,"Send request");waitFor(scenario,"HTTP 200");screenshot("03-repeater");
            tap(scenario,"Network");screenshot("04-network");
            tap(scenario,"Elements");tap(scenario,"Inspect");waitFor(scenario,"<h1>");
            tap(scenario,"Analyze");tap(scenario,"Page map");waitFor(scenario,"secureContext");screenshot("05-analysis");
            tap(scenario,"Review headers");waitFor(scenario,"HEADER REVIEW");
            tap(scenario,"Terminal");waitFor(scenario,"LOCAL SHELL");
            scenario.onActivity(a->{EditText input=(EditText)findDescription(a.getWindow().getDecorView(),"Enter shell command");input.setText("printf 'BB_%s_OK\\n' SHELL");});
            tap(scenario,"Run");waitFor(scenario,"BB_SHELL_OK");screenshot("06-terminal");
            scenario.onActivity(a->{EditText input=(EditText)findDescription(a.getWindow().getDecorView(),"Enter shell command");input.setText("cd /; pwd");});
            tap(scenario,"Run");Thread.sleep(300);
            scenario.onActivity(a->{EditText input=(EditText)findDescription(a.getWindow().getDecorView(),"Enter shell command");input.setText("printf 'CWD=%s\\n' \"$PWD\"");});
            tap(scenario,"Run");waitFor(scenario,"CWD=/");
            tap(scenario,"Browser");
            scenario.onActivity(a->{View plus=findDescription(a.getWindow().getDecorView(),"New tab");assertNotNull(plus);plus.performClick();});
            Thread.sleep(500);tap(scenario,"Console");tap(scenario,"Evaluate");waitFor(scenario,"New tab");Thread.sleep(300);
            scenario.onActivity(a->{TextView out=(TextView)findDescription(a.getWindow().getDecorView(),"Console output");assertTrue(out.getText().toString().contains("New tab"));assertFalse(out.getText().toString().contains("BB · Local lab"));});screenshot("07-tabs");
            scenario.onActivity(a->{View old=findDescription(a.getWindow().getDecorView(),"Switch tab BB · Local lab");assertNotNull(old);old.performClick();});
            tap(scenario,"Evaluate");waitFor(scenario,"BB · Local lab");
            tap(scenario,"Workspace");tap(scenario,"Save request");tap(scenario,"Load request");waitFor(scenario,"Request workbench");
        }
    }
    @Test public void englishChromeRemainsLtrWithArabicConfiguration() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.content.res.Configuration previous=new android.content.res.Configuration(context.getResources().getConfiguration());
        android.content.res.Configuration arabic=new android.content.res.Configuration(previous);arabic.setLocale(new java.util.Locale("ar"));arabic.setLayoutDirection(new java.util.Locale("ar"));
        context.getResources().updateConfiguration(arabic,context.getResources().getDisplayMetrics());
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor(scenario,"observed requests");
            scenario.onActivity(a->{ViewGroup content=a.findViewById(android.R.id.content);assertEquals(View.LAYOUT_DIRECTION_LTR,content.getChildAt(0).getLayoutDirection());assertTrue(find(a.getWindow().getDecorView(),"Browser",true).isShown());});
            screenshot("08-arabic-device");
        }finally{context.getResources().updateConfiguration(previous,context.getResources().getDisplayMetrics());}
    }
    @Test public void workspaceEncryptionRoundTripAndDelete() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkspaceStore.save(context,"{\"secret\":\"test-value\"}");
        assertEquals("{\"secret\":\"test-value\"}",WorkspaceStore.load(context));
        WorkspaceStore.delete(context);assertNull(WorkspaceStore.load(context));
    }
}
