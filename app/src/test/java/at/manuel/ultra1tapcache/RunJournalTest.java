package at.manuel.ultra1tapcache;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="de-w411dp-h891dp-420dpi") @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class RunJournalTest {
 @Test public void speedChangesWaitsNotSafetyOrProgress() {
  assertEquals(250,CleanerService.waitFor("turbo",false));assertEquals(400,CleanerService.waitFor("turbo",true));assertEquals(650,CleanerService.waitFor("normal",false));assertEquals(900,CleanerService.waitFor("normal",true));assertEquals(1300,CleanerService.waitFor("slow",false));assertEquals(1800,CleanerService.waitFor("slow",true));
 }
 @Test public void confirmedCheckpointSurvivesRereadAndSkipsOnlyVerified() {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("clean_run",0).edit().clear().commit();
  java.util.List<AppEntry> apps=java.util.Arrays.asList(new AppEntry("a","A",100L),new AppEntry("b","B",200L,true,true),new AppEntry("c","C",50L));
  RunJournal.begin(c,apps);RunJournal.state(c,"a","confirmed",5L,95);RunJournal.state(c,"b","unconfirmed",200L,0);RunJournal.reason(c,"service_destroyed");
  assertEquals(1,RunJournal.count(c,"confirmed"));assertEquals(95L,RunJournal.read(c).optLong("bytes"));assertEquals("service_destroyed",RunJournal.read(c).optString("reason"));
  assertEquals(2,RunJournal.remaining(c).size());assertEquals("b",RunJournal.remaining(c).get(0).packageName);assertTrue(RunJournal.remaining(c).get(0).system);
  RunJournal.state(c,"b","already_empty",0L,0);assertEquals(1,RunJournal.remaining(c).size());assertEquals("c",RunJournal.remaining(c).get(0).packageName);
 }
 @Test public void newRunResetsOldProgressAndBaselineCanRefresh() {
  Context c=RuntimeEnvironment.getApplication();java.util.List<AppEntry> apps=java.util.Arrays.asList(new AppEntry("x","X",500L));
  RunJournal.begin(c,apps);RunJournal.baseline(c,"x",700L);assertEquals(Long.valueOf(700),RunJournal.remaining(c).get(0).cacheBytes);
  RunJournal.state(c,"x","confirmed",10L,690);RunJournal.begin(c,apps);assertEquals(0,RunJournal.count(c,"confirmed"));assertEquals(0L,RunJournal.read(c).optLong("bytes"));assertEquals(1,RunJournal.remaining(c).size());
 }
 @Test public void clickedIsNotConfirmedAndNoBackgroundStart() {
  Context c=RuntimeEnvironment.getApplication();RunJournal.begin(c,java.util.Arrays.asList(new AppEntry("x","X",500L)));RunJournal.state(c,"x","clicked",null,0);
  assertEquals(1,RunJournal.remaining(c).size());assertFalse(RunJournal.done("clicked"));assertFalse(RunJournal.done("unconfirmed"));assertTrue(RunJournal.done("confirmed"));
 } @Test public void watchdogStopsAndKeepsUnconfirmedWork() throws Exception {
  Context c=RuntimeEnvironment.getApplication();RunJournal.begin(c,java.util.Arrays.asList(new AppEntry("x","X",500L)));CleanerService.running=true;
  CleanerService service=org.robolectric.Robolectric.buildService(CleanerService.class).create().get();
  java.lang.reflect.Method m=CleanerService.class.getDeclaredMethod("armWatchdog",String.class);m.setAccessible(true);m.invoke(service,"measurement_timeout");
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(11));
  assertFalse(CleanerService.running);assertEquals("measurement_timeout",RunJournal.read(c).optString("reason"));assertEquals(1,RunJournal.remaining(c).size());service.onDestroy();
 }
 @Test public void resumeScreenAndDialogDoNotStartWithoutExplicitConfirmation() throws Exception {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
  RunJournal.begin(c,java.util.Arrays.asList(new AppEntry("done","Done",100L),new AppEntry("remaining","Remaining",500L)));RunJournal.state(c,"done","confirmed",0L,100);RunJournal.reason(c,"service_destroyed");CleanerService.running=false;CleanerService.attempted=0;
  MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();
  java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("updateStatus");update.setAccessible(true);update.invoke(a);
  java.lang.reflect.Field f=MainActivity.class.getDeclaredField("resume");f.setAccessible(true);android.widget.Button b=(android.widget.Button)f.get(a);assertEquals(android.view.View.VISIBLE,b.getVisibility());
  save(a.getWindow().getDecorView(),"resume-main.png",1080,2340,true);
  CleanerService service=org.robolectric.Robolectric.buildService(CleanerService.class).create().get();CleanerService.connected=service;
  b.performClick();android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertFalse(CleanerService.running);save(d.getWindow().getDecorView(),"resume-confirm.png",1000,1900,false);d.dismiss();CleanerService.connected=null;a.finish();service.onDestroy();c.getSharedPreferences("clean_run",0).edit().clear().commit();
 }
 private static void save(android.view.View v,String name,int w,int h,boolean exact) throws Exception {
  v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(h,exact?android.view.View.MeasureSpec.EXACTLY:android.view.View.MeasureSpec.AT_MOST));v.layout(0,0,w,v.getMeasuredHeight());
  android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(v.getWidth(),v.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);v.draw(new android.graphics.Canvas(b));java.io.File f=new java.io.File("build/reports/ui/"+name);f.getParentFile().mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(f)){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
 }

}
