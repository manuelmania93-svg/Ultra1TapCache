package at.manuel.ultra1tapcache;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.widget.Button;
import android.widget.ScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Runs on a real Android emulator, including actual touch scrolling and tap dispatch. */
@RunWith(AndroidJUnit4.class)
public class ScrollSmokeTest {
    @Test public void launchSwipeAndTapStart() throws Exception {
        Instrumentation i=InstrumentationRegistry.getInstrumentation();
        Context c=i.getTargetContext();
        c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
        MainActivity a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        UiDevice device=UiDevice.getInstance(i);
        assertTrue(device.wait(Until.hasObject(By.pkg(c.getPackageName()).depth(0)),10000));
        // Dismiss optional guided setup, never grant Accessibility or run a real clean.
        device.pressBack();
        if(a.isFinishing()) a=(MainActivity)i.startActivitySync(new Intent(c,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        final MainActivity activity=a;
        Field scan=MainActivity.class.getDeclaredField("scanning");scan.setAccessible(true);
        long deadline=System.currentTimeMillis()+20000;
        while(scan.getBoolean(a)&&System.currentTimeMillis()<deadline) Thread.sleep(100);
        assertFalse("Initial app scan completed",scan.getBoolean(a));
        Field apps=MainActivity.class.getDeclaredField("apps");apps.setAccessible(true);
        Method render=MainActivity.class.getDeclaredMethod("renderList");render.setAccessible(true);
        Method status=MainActivity.class.getDeclaredMethod("updateStatus");status.setAccessible(true);
        i.runOnMainSync(()->{
            try {
                List<AppEntry> entries=new ArrayList<>();
                for(int n=0;n<60;n++)entries.add(new AppEntry("example.app"+n,"Test app "+n,2097152L));
                apps.set(activity,entries);render.invoke(activity);status.invoke(activity);
                // Match the long completed-run summary that exposed the original bug.
                Field progress=MainActivity.class.getDeclaredField("progress");progress.setAccessible(true);
                ((android.widget.TextView)progress.get(activity)).append("\nReinigung beendet\n39 bestaetigt und gespeichert · 5 versucht, unveraendert · 0 Apps uebrig");
            }catch(Exception e){throw new RuntimeException(e);}
        });
        i.waitForIdleSync();
        ScrollView scroll=a.findViewById(R.id.content_scroll);
        Button start=a.findViewById(R.id.clean_button);
        Rect startBefore=new Rect();assertTrue(start.getGlobalVisibleRect(startBefore));
        assertTrue("Full start button visible",startBefore.height()>=start.getHeight()-1);
        assertTrue("Real scroll range",scroll.canScrollVertically(1));
        String suffix=InstrumentationRegistry.getArguments().getString("capture","device");
        device.takeScreenshot(new File("/sdcard/Download/scroll12-"+suffix+"-top.png"));
        Rect viewport=new Rect();assertTrue(scroll.getGlobalVisibleRect(viewport));
        int x=viewport.centerX();
        assertTrue(device.swipe(x,viewport.bottom-30,x,viewport.top+30,30));
        i.waitForIdleSync();
        assertTrue("Touch swipe moved the entire content",scroll.getScrollY()>0);
        // Traverse all apps: no nested viewport and no unreachable bottom row.
        i.runOnMainSync(()->scroll.scrollTo(0,scroll.getChildAt(0).getHeight()));
        i.waitForIdleSync();
        assertFalse("Bottom of content reachable",scroll.canScrollVertically(1));
        assertTrue(device.hasObject(By.textContains("Test app 59")));
        Rect startAfter=new Rect();assertTrue(start.getGlobalVisibleRect(startAfter));
        assertEquals("Start remains pinned during scrolling",startBefore,startAfter);
        device.takeScreenshot(new File("/sdcard/Download/scroll12-"+suffix+"-bottom.png"));
        assertTrue(device.click(startAfter.centerX(),startAfter.centerY()));
        boolean setupVisible=device.wait(Until.hasObject(By.text(c.getString(R.string.ui_021))),5000);
        assertTrue("Tap reaches setup handler",setupVisible);
        assertFalse("Smoke test never starts cleaning",CleanerService.running);
        device.takeScreenshot(new File("/sdcard/Download/scroll12-"+suffix+"-tap.png"));
        device.pressBack();i.runOnMainSync(activity::finish);
    }
}
