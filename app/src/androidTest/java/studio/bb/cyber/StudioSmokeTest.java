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
    private boolean contains(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),text))return true;
        return false;
    }
    private void tap(ActivityScenario<MainActivity> scenario,String text){scenario.onActivity(a->{View b=find(a.getWindow().getDecorView(),text,true);assertNotNull(text,b);b.performClick();});}
    private void waitFor(ActivityScenario<MainActivity> scenario,String text) throws Exception {
        long deadline=System.currentTimeMillis()+10000;boolean[] found={false};
        while(System.currentTimeMillis()<deadline){scenario.onActivity(a->found[0]=contains(a.getWindow().getDecorView(),text));if(found[0])return;Thread.sleep(100);}
        fail("Timed out waiting for "+text);
    }
    private void screenshot(String name) throws Exception {
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        if(bitmap!=null){File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null);try(FileOutputStream output=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();}
    }
    @Test public void browserConsoleRepeaterAndPanelsWork() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor(scenario,"observed requests");screenshot("01-browser");
            tap(scenario,"Console");tap(scenario,"Evaluate");waitFor(scenario,"BB · Local lab");screenshot("02-console");
            tap(scenario,"Repeater");tap(scenario,"Send request");waitFor(scenario,"HTTP 200");screenshot("03-repeater");
            tap(scenario,"Network");screenshot("04-network");
            tap(scenario,"Elements");tap(scenario,"Inspect");waitFor(scenario,"<h1>");
            tap(scenario,"Terminal");waitFor(scenario,"Termux commands");screenshot("05-terminal");
            tap(scenario,"Workspace");tap(scenario,"Save request");tap(scenario,"Load request");waitFor(scenario,"Request workbench");
        }
    }
    @Test public void workspaceEncryptionRoundTripAndDelete() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkspaceStore.save(context,"{\"secret\":\"test-value\"}");
        assertEquals("{\"secret\":\"test-value\"}",WorkspaceStore.load(context));
        WorkspaceStore.delete(context);assertNull(WorkspaceStore.load(context));
    }
}
