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
 @Test public void historyUsesMeasuredStateGrowthTimeAndVersion() throws Exception{
  org.json.JSONObject h=new org.json.JSONObject();h.put("state","confirmed");h.put("after",20000000L);h.put("time",1000L);h.put("stamp","1:10:10");
  assertEquals(1,RunJournal.classify(h,21000000L,"1:10:10",2000));assertEquals(0,RunJournal.classify(h,21000001L,"1:10:10",2000));assertEquals(0,RunJournal.classify(h,20000000L,"2:10:20",2000));assertEquals(0,RunJournal.classify(h,20000000L,"1:10:10",1000+86400001L));assertEquals(0,RunJournal.classify(h,20000000L,"1:10:10",999));assertEquals(0,RunJournal.classify(h,null,"1:10:10",2000));assertEquals(0,RunJournal.classify(h,20000000L,"",2000));
  h.put("state","attempted_unchanged");assertEquals(2,RunJournal.classify(h,20000000L,"1:10:10",2000));assertEquals(0,RunJournal.classify(h,20000000L,"1:10:10",1000+21600001L));assertEquals(65536L,RunJournal.tolerance(0));assertEquals(1048576L,RunJournal.tolerance(100000000L));
 }
 @Test public void historyPersistsAcrossNewRunsWithoutCallingUnchangedCleaned() throws Exception{
  Context c=RuntimeEnvironment.getApplication();String pkg=c.getPackageName();c.getSharedPreferences("cache_history",0).edit().clear().commit();RunJournal.remember(c,pkg,"confirmed",2000000L);AppEntry a=new AppEntry(pkg,"Residual App",2000000L);a.selected=true;RunJournal.applyHistory(c,java.util.Arrays.asList(a));assertFalse(a.selected);assertEquals(1,RunJournal.historyStatus(c,a));RunJournal.begin(c,java.util.Arrays.asList(a));assertEquals(1,RunJournal.historyStatus(c,a));
  RunJournal.clickInfo(c,pkg,true,true,1);RunJournal.clickInfo(c,pkg,true,true,2);assertTrue(CleanerService.exhaustedAccepted(c,pkg,2000000L,2000000L,2));assertFalse(CleanerService.exhaustedAccepted(c,pkg,2000000L,2000000L,1));RunJournal.state(c,pkg,"attempted_unchanged",2000000L,0);RunJournal.remember(c,pkg,"attempted_unchanged",2000000L);assertEquals(0,RunJournal.count(c,"confirmed"));assertEquals(0,RunJournal.remaining(c).size());assertEquals(2,RunJournal.historyStatus(c,a));assertEquals(0L,RunJournal.read(c).optLong("bytes"));
  a.selected=true;assertTrue(a.selected);a.cacheBytes=4000000L;assertEquals(0,RunJournal.historyStatus(c,a));c.getSharedPreferences("cache_history",0).edit().clear().commit();c.getSharedPreferences("clean_run",0).edit().clear().commit();
 }
 @Test public void cacheDialogsNeedExplicitSafeContent(){assertTrue(SafeLabels.cacheDialog("Clear cache?"));assertTrue(SafeLabels.cacheDialog("Cache löschen?"));assertFalse(SafeLabels.cacheDialog("Are you sure? OK"));assertFalse(SafeLabels.cacheDialog("Clear cache and app data?"));assertFalse(SafeLabels.cacheDialog("Cache und Daten löschen?"));assertTrue(SafeLabels.affirmative("OK"));assertFalse(SafeLabels.affirmative("Cancel"));assertFalse(SafeLabels.affirmative("Clear data"));}
 @Test public void residualCacheIsCompletedAndDiagnosticTextIsBounded() throws Exception{Context c=RuntimeEnvironment.getApplication();RunJournal.begin(c,java.util.Arrays.asList(new AppEntry("x","Residual App",39170048L)));RunJournal.state(c,"x","confirmed",29024256L,10145792L);RunJournal.remember(c,c.getPackageName(),"confirmed",29024256L);RunJournal.detail(c,"x","screen_after","clear cache | residual cache");RunJournal.detail(c,"x","clicked_node","text=Cache leeren; path=Button#android:id/button1");assertTrue(RunJournal.residual(c,"x"));assertEquals(0,RunJournal.remaining(c).size());assertEquals(29024256L,RunJournal.read(c).getJSONArray("rows").getJSONObject(0).getLong("residual_baseline"));String report=RunJournal.diagnostics(c);assertTrue(report.contains("screen_after"));assertTrue(report.contains("29024256 B"));assertTrue(CleanerService.cut("x".repeat(2000),1200).length()<=1201);c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();CleanerService.running=false;MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();java.lang.reflect.Field f=MainActivity.class.getDeclaredField("apps");f.setAccessible(true);java.util.List<AppEntry> fresh=java.util.Arrays.asList(new AppEntry(c.getPackageName(),"Residual App",29024256L));RunJournal.applyHistory(c,fresh);f.set(a,fresh);for(String name:new String[]{"renderList","updateStatus"}){java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}save(a.getWindow().getDecorView(),"residual-main.png",1080,2340,true);a.finish();c.getSharedPreferences("cache_history",0).edit().clear().commit();c.getSharedPreferences("clean_run",0).edit().clear().commit();}
 @Test public void defaultSelectionFocusesAboveOneMbAndManualAllKeepsSmallApps() throws Exception {
  java.util.List<AppEntry> apps=java.util.Arrays.asList(new AppEntry("large","Large",2097152L),new AppEntry("edge","Edge",1048576L),new AppEntry("small","Small",24576L),new AppEntry("system","System",2097152L,true,true),new AppEntry("unknown","Unknown",null),new AppEntry("disabled","Disabled",2097152L,false,false));MainActivity.selectLargeByDefault(apps);assertTrue(apps.get(0).selected);for(int i=1;i<apps.size();i++)assertFalse(apps.get(i).selected);
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("clean_run",0).edit().clear().commit();c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();CleanerService.running=false;CleanerService.attempted=0;MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();java.lang.reflect.Field f=MainActivity.class.getDeclaredField("apps");f.setAccessible(true);f.set(a,apps);for(String name:new String[]{"renderList","updateStatus"}){java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}save(a.getWindow().getDecorView(),"large-selection.png",1080,2340,true);MainActivity.selectAllMeasured(apps);assertTrue(apps.get(1).selected);assertTrue(apps.get(2).selected);assertTrue(apps.get(3).selected);assertFalse(apps.get(4).selected);assertFalse(apps.get(5).selected);a.finish();
 }
 @Test public void clickOutcomeAndSmallCacheAreHonestDiagnostics(){Context c=RuntimeEnvironment.getApplication();RunJournal.begin(c,java.util.Arrays.asList(new AppEntry("x","Small",8192L)));RunJournal.clickInfo(c,"x",false,null,1);RunJournal.clickInfo(c,"x",true,false,2);RunJournal.state(c,"x","small_cache_skipped",8192L,0);assertEquals(0,RunJournal.remaining(c).size());assertEquals(0,RunJournal.count(c,"confirmed"));org.json.JSONObject row=RunJournal.read(c).optJSONArray("rows").optJSONObject(0);assertTrue(row.optBoolean("disabled_seen"));assertFalse(row.optBoolean("click_accepted"));assertEquals(2,row.optInt("clean_attempt"));assertTrue(RunJournal.diagnostics(c).contains("[small_cache_skipped]"));c.getSharedPreferences("clean_run",0).edit().clear().commit();}
 @Test public void meaningfulDecreaseAndFullRetryAreBounded(){assertFalse(CleanerService.measuredDecrease(10000L,5904L));assertTrue(CleanerService.measuredDecrease(10000L,5903L));assertTrue(CleanerService.shouldRetry(2000000L,2000000L,1));assertFalse(CleanerService.shouldRetry(2000000L,2000000L,2));assertFalse(CleanerService.shouldRetry(20000L,20000L,1));assertFalse(CleanerService.shouldRetry(2000000L,null,1));assertTrue(RunJournal.done("small_cache_skipped"));assertFalse(RunJournal.done("tiny_decrease"));}
 @Test public void diagnosticsSummarizeRowsAndShareThroughUserChooser() throws Exception {
  Context c=RuntimeEnvironment.getApplication();RunJournal.begin(c,java.util.Arrays.asList(new AppEntry("x","Example App",500L),new AppEntry("y","Unmeasurable",100L)));RunJournal.clickInfo(c,"x",false,null,1);RunJournal.clickInfo(c,"x",true,true,2);RunJournal.measurement(c,"x",500L,3,1200);RunJournal.state(c,"x","unconfirmed",500L,0);RunJournal.state(c,"y","cache_button_disabled",null,0);RunJournal.reason(c,"finished");String report=RunJournal.diagnostics(c);assertTrue(report.contains("[unconfirmed]"));assertTrue(report.contains("[cache_button_disabled]"));assertTrue(report.contains("500 B"));assertTrue(report.contains("1200 ms"));
  c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();CleanerService.running=false;MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("showRunDiagnostics");m.setAccessible(true);m.invoke(a);android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();save(d.getWindow().getDecorView(),"run-diagnostics.png",1000,2100,false);d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();android.content.Intent chooser=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();assertEquals(android.content.Intent.ACTION_CHOOSER,chooser.getAction());android.content.Intent send=chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT);assertEquals(android.content.Intent.ACTION_SEND,send.getAction());assertEquals("text/plain",send.getType());assertEquals(report,send.getStringExtra(android.content.Intent.EXTRA_TEXT));assertFalse(CleanerService.running);a.finish();c.getSharedPreferences("clean_run",0).edit().clear().commit();
 }
 @Test public void verificationRetriesAreBoundedAndNeverPromoteUnchangedBytes() {
  assertEquals(300,CleanerService.retryDelay(1));assertEquals(450,CleanerService.retryDelay(2));assertEquals(-1,CleanerService.retryDelay(3));assertFalse(CleanerService.measuredDecrease(100L,100L));assertFalse(CleanerService.measuredDecrease(100L,120L));assertFalse(CleanerService.measuredDecrease(100L,null));assertTrue(CleanerService.measuredDecrease(10000L,20L));
 }
 @Test public void measurementDiagnosticsSurviveJournalReread() {
  Context c=RuntimeEnvironment.getApplication();RunJournal.begin(c,java.util.Arrays.asList(new AppEntry("x","X",500L)));RunJournal.measurement(c,"x",500L,2,800);org.json.JSONObject row=RunJournal.read(c).optJSONArray("rows").optJSONObject(0);assertEquals(500,row.optLong("baseline"));assertEquals(500,row.optLong("after"));assertEquals(2,row.optInt("measurement_reads"));assertEquals(800,row.optLong("click_to_read_ms"));assertEquals(1,RunJournal.remaining(c).size());c.getSharedPreferences("clean_run",0).edit().clear().commit();
 }
 @Test public void freshScanDeselectsConfirmedButKeepsLiveBytesAndUnconfirmed() throws Exception {
  Context c=RuntimeEnvironment.getApplication();AppEntry done=new AppEntry("done","Done",100L);AppEntry pending=new AppEntry("pending","Pending",200L);java.util.List<AppEntry> original=java.util.Arrays.asList(done,pending);RunJournal.begin(c,original);RunJournal.state(c,"done","confirmed",0L,100);RunJournal.state(c,"pending","unconfirmed",200L,0);RunJournal.reason(c,"finished");
  AppEntry freshDone=new AppEntry("done","Done",20L);AppEntry freshPending=new AppEntry("pending","Pending",200L);freshDone.selected=freshPending.selected=true;RunJournal.apply(c,java.util.Arrays.asList(freshDone,freshPending));assertFalse(freshDone.selected);assertTrue(freshPending.selected);assertEquals(Long.valueOf(20),freshDone.cacheBytes);assertTrue(RunJournal.completed(c,"done"));assertFalse(RunJournal.completed(c,"pending"));
  c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();CleanerService.running=false;MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();java.lang.reflect.Field f=MainActivity.class.getDeclaredField("apps");f.setAccessible(true);f.set(a,java.util.Arrays.asList(freshDone,freshPending));for(String name:new String[]{"renderList","updateStatus"}){java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}save(a.getWindow().getDecorView(),"completed-selection.png",1080,2340,true);a.finish();
  c.getSharedPreferences("clean_run",0).edit().clear().commit();
 }
 @Test public void installerHintIsNotAConfirmedRestriction() {
  assertTrue(MainActivity.restrictedSetupPossible(35,null,false));assertTrue(MainActivity.restrictedSetupPossible(35,"com.android.packageinstaller",false));assertFalse(MainActivity.restrictedSetupPossible(35,"com.android.vending",false));assertFalse(MainActivity.restrictedSetupPossible(35,null,true));assertFalse(MainActivity.restrictedSetupPossible(32,null,false));
 }
 @Test public void settingsShortcutsOpenExactSystemPages() throws Exception {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
  MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();
  for(String name:new String[]{"openAllApps","openAppInfo"}){java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);android.content.Intent intent=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();assertEquals("openAllApps".equals(name)?android.provider.Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS:android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,intent.getAction());if("openAppInfo".equals(name))assertEquals("package:"+a.getPackageName(),intent.getDataString());}
  assertFalse(CleanerService.running);a.finish();
 }
 @Test public void setupChoicePrioritizesUsageAndStopsWhenReady() {
  assertEquals(1,MainActivity.missingSetupStep(false,false));assertEquals(1,MainActivity.missingSetupStep(false,true));assertEquals(2,MainActivity.missingSetupStep(true,false));assertEquals(0,MainActivity.missingSetupStep(true,true));
 }
 @Test public void autoSetupDismissalDoesNotLoopOrStartCleaning() throws Exception {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();CleanerService.running=false;AppScanner.readState=AppScanner.DENIED;
  MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();java.lang.reflect.Field r=MainActivity.class.getDeclaredField("resumed");r.setAccessible(true);r.setBoolean(a,true);
  java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("maybeGuideSetup");m.setAccessible(true);m.invoke(a);
  android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());save(d.getWindow().getDecorView(),"guided-setup.png",1000,1900,false);d.dismiss();m.invoke(a);assertFalse(d.isShowing());assertFalse(CleanerService.running);a.finish();AppScanner.readState=AppScanner.UNCHECKED;
 }
 @Test public void speedChangesWaitsNotSafetyOrProgress() {
  assertEquals(250,CleanerService.waitFor("turbo",false));assertEquals(400,CleanerService.waitFor("turbo",true));assertEquals(650,CleanerService.waitFor("normal",false));assertEquals(900,CleanerService.waitFor("normal",true));assertEquals(400,CleanerService.waitFor("fast",false));assertEquals(550,CleanerService.waitFor("fast",true));
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
