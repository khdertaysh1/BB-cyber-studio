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
    private void waitForConsoleResult(ActivityScenario<MainActivity> scenario,String text) throws Exception {
        long deadline=System.currentTimeMillis()+10000;boolean[] ready={false};
        while(System.currentTimeMillis()<deadline){
            scenario.onActivity(a->{
                View output=findDescription(a.getWindow().getDecorView(),"Console output");
                ready[0]=output instanceof TextView &&
                    ((TextView)output).getText().toString().contains(text);
            });
            if(ready[0])return;
            Thread.sleep(100);
        }
        fail("Timed out waiting for Console output: "+text);
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
            tap(scenario,"Console");tap(scenario,"Run");waitForConsoleResult(scenario,"BB · Local lab");
            tap(scenario,"Copy");
            scenario.onActivity(activity->{
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData data=clipboard.getPrimaryClip();
                assertNotNull("Copy output must populate the clipboard",data);
                assertTrue(data.getItemAt(0).coerceToText(activity).toString().contains("BB · Local lab"));
            });
            screenshot("02-console");
            scenario.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            Thread.sleep(1200);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            tap(scenario,"Run");waitForConsoleResult(scenario,"BB · Local lab");screenshot("02-console-landscape");
            scenario.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            Thread.sleep(1200);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            tap(scenario,"Repeater");tap(scenario,"Send");waitFor(scenario,"HTTP 200");screenshot("03-repeater");
            tap(scenario,"Network");screenshot("04-network");
            tap(scenario,"Elements");tap(scenario,"Inspect HTML");waitFor(scenario,"<h1>");
            tap(scenario,"Copy HTML");
            scenario.onActivity(activity->{
                android.content.ClipData copied=((android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip();
                assertNotNull(copied);assertTrue(copied.getItemAt(0).coerceToText(activity).toString().contains("<h1"));
            });
            scenario.onActivity(a->a.openAnalyze());tap(scenario,"Page map");waitFor(scenario,"secureContext");screenshot("05-analysis");
            tap(scenario,"Review headers");waitFor(scenario,"HEADER REVIEW");
            tap(scenario,"Terminal");waitFor(scenario,"ANDROID SHELL / TERMUX CONNECTION");
            scenario.onActivity(a->{EditText input=(EditText)findDescription(a.getWindow().getDecorView(),"Enter shell command");input.setText("printf 'BB_%s_OK\\n' SHELL");});
            tap(scenario,"Run");waitFor(scenario,"BB_SHELL_OK");screenshot("06-terminal");
            scenario.onActivity(a->{EditText input=(EditText)findDescription(a.getWindow().getDecorView(),"Enter shell command");input.setText("cd /; pwd");});
            tap(scenario,"Run");Thread.sleep(300);
            scenario.onActivity(a->{EditText input=(EditText)findDescription(a.getWindow().getDecorView(),"Enter shell command");input.setText("printf 'CWD=%s\\n' \"$PWD\"");});
            tap(scenario,"Run");waitFor(scenario,"CWD=/");
            tap(scenario,"Browser");
            scenario.onActivity(a->{View toggle=findDescription(a.getWindow().getDecorView(),"Show tabs");assertNotNull(toggle);toggle.performClick();View plus=findDescription(a.getWindow().getDecorView(),"New tab");assertNotNull(plus);assertTrue(plus.isShown());plus.performClick();});
            Thread.sleep(500);tap(scenario,"Console");tap(scenario,"Run");waitForConsoleResult(scenario,"New tab");Thread.sleep(300);
            scenario.onActivity(a->{TextView out=(TextView)findDescription(a.getWindow().getDecorView(),"Console output");assertTrue(out.getText().toString().contains("New tab"));assertFalse(out.getText().toString().contains("BB · Local lab"));});screenshot("07-tabs");
            scenario.onActivity(a->{View old=findDescription(a.getWindow().getDecorView(),"Switch tab BB · Local lab");assertNotNull(old);old.performClick();});
            tap(scenario,"Run");waitForConsoleResult(scenario,"BB · Local lab");
            scenario.onActivity(a->a.openWorkspace());tap(scenario,"Save");tap(scenario,"Load into Repeater");waitFor(scenario,"HTTP Repeater");
        }
    }
    @Test public void compactConsoleCopiesAndBrowserStaysAliveBehindTools() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{
                ViewGroup tree=a.findViewById(android.R.id.content);
                View tools=find(tree,"Tools",true);
                assertNotNull(tools);tools.performClick();
                View console=find(tree,"Console",true);
                assertNotNull(console);console.performClick();
                View run=find(tree,"Run",true);View copy=find(tree,"Copy",true);
                assertNotNull(run);assertNotNull(copy);
                assertTrue(run.isShown());assertTrue(copy.isShown());
                assertNull("Verbose console controls should be in overflow",find(tree,"Clear logs",true));
                assertNull("Verbose console controls should be in overflow",find(tree,"Reload page",true));
                View browser=find(tree,"Browser",true);
                assertNotNull(browser);assertTrue(browser.isShown());
                View sheet=find(tree,"BLUE DRAGON · DEVTOOLS",false);
                assertNotNull(sheet);assertTrue(sheet.isShown());
            });
            scenario.onActivity(a->a.openWorkspace());
            scenario.onActivity(a->{
                View saved=find(a.getWindow().getDecorView(),"Saved Request",false);
                assertNotNull(saved);assertTrue(saved.isShown());
                assertNull("Workspace must no longer be a bottom navigation destination",
                    find(a.getWindow().getDecorView(),"Workspace",true));
            });
        }
    }

    @Test public void elementsCssIsCollapsedAndTerminalShowsCorrectEnvironment() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            tap(scenario,"Elements");
            scenario.onActivity(a->{
                ViewGroup tree=a.findViewById(android.R.id.content);
                View apply=find(tree,"Apply CSS",true);
                assertNotNull(apply);assertFalse("CSS editing should be hidden until requested",apply.isShown());
                View copy=find(tree,"Copy HTML",true);assertNotNull(copy);assertTrue(copy.isShown());
                View toggle=find(tree,"Edit CSS ▾",true);assertNotNull(toggle);toggle.performClick();
                assertTrue("CSS editor must expand on demand",apply.isShown());
            });
            tap(scenario,"Terminal");
            scenario.onActivity(a->{
                ViewGroup tree=a.findViewById(android.R.id.content);
                View android=find(tree,"Android ▾",true);assertNotNull(android);android.performClick();
                View termux=find(tree,"Termux ▾",true);assertNotNull(termux);assertTrue(termux.isShown());
                termux.performClick();
                assertTrue(find(tree,"Android ▾",true).isShown());
            });
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
    @Test public void installationInfoReportsBuildAndSigningCertificate() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String report=AppDiagnostics.describe(context);
        assertTrue(report.contains("Version: 0.5.1-alpha (6)"));
        assertTrue(report.contains("DEBUG / testing only"));
        assertTrue(report.contains("Signing certificate SHA-256:"));
        assertTrue(report.matches("(?s).*([0-9A-F]{2}:){31}[0-9A-F]{2}.*"));
        assertFalse(report.contains("Some build details are unavailable"));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{android.app.AlertDialog dialog=a.showAppInfo();assertTrue(dialog.isShowing());assertTrue(contains(dialog.getWindow().getDecorView(),"INSTALLATION HELP"));});screenshot("09-installation-info");
        }
    }

    @Test public void clearingBrowserDataRemovesCopyableConsoleResult() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor(scenario,"observed requests");tap(scenario,"Console");
            scenario.onActivity(a->((EditText)findDescription(a.getWindow().getDecorView(),"JavaScript input")).setText("'PRIVATE_CLEAR_TEST'"));
            tap(scenario,"Run");waitForConsoleResult(scenario,"\n\nPRIVATE_CLEAR_TEST");
            scenario.onActivity(a->{assertTrue(find(a.getWindow().getDecorView(),"Copy",true).isEnabled());a.clearBrowserData();
                assertFalse(find(a.getWindow().getDecorView(),"Copy",true).isEnabled());});
            tap(scenario,"Browser");tap(scenario,"Console");
            scenario.onActivity(a->{
                assertFalse(find(a.getWindow().getDecorView(),"Copy",true).isEnabled());
                assertFalse(((TextView)findDescription(a.getWindow().getDecorView(),"Console output")).getText().toString().contains("PRIVATE_CLEAR_TEST"));
            });
        }
    }

    @Test public void workspaceEncryptionRoundTripAndDelete() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkspaceStore.save(context,"{\"secret\":\"test-value\"}");
        assertEquals("{\"secret\":\"test-value\"}",WorkspaceStore.load(context));
        WorkspaceStore.delete(context);assertNull(WorkspaceStore.load(context));
    }
}
