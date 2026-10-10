package at.manuel.ultra1tapcache;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.content.Context;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="de-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MainActivityRenderTest {
 @Test public void rendersMainScreen() throws Exception {
  Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  java.lang.reflect.Field field=MainActivity.class.getDeclaredField("apps"); field.setAccessible(true);
  java.util.List<AppEntry> demo=new java.util.ArrayList<>(); demo.add(new AppEntry("com.example.browser","Beispiel: Browser",125829120L)); demo.add(new AppEntry("com.example.music","Beispiel: Musik",52428800L)); demo.add(new AppEntry("com.example.unknown","Beispiel: nicht messbar",null)); field.set(a,demo);
  java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("renderList");render.setAccessible(true);render.invoke(a);
  java.lang.reflect.Method status=MainActivity.class.getDeclaredMethod("updateStatus");status.setAccessible(true);status.invoke(a);
  View v=a.getWindow().getDecorView();
  v.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2340,View.MeasureSpec.EXACTLY));
  v.layout(0,0,1080,2340);
  Bitmap bitmap=Bitmap.createBitmap(1080,2340,Bitmap.Config.ARGB_8888); v.draw(new Canvas(bitmap));
  File f=new File("build/reports/ui/main.png"); f.getParentFile().mkdirs();
  try(FileOutputStream out=new FileOutputStream(f)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
  assertEquals(1080,bitmap.getWidth()); assertFalse(CleanerService.running); a.finish();
 }
 @Test public void missingAccessibilityButtonOpensSetup() throws Exception {
  Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
  CleanerService.connected=null; CleanerService.running=false;
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  java.lang.reflect.Method status=MainActivity.class.getDeclaredMethod("updateStatus"); status.setAccessible(true); status.invoke(a);
  java.lang.reflect.Field clean=MainActivity.class.getDeclaredField("clean"); clean.setAccessible(true);
  android.widget.Button button=(android.widget.Button)clean.get(a);
  assertTrue("Setup must remain reachable without service/selection",button.isEnabled());
  assertTrue(button.performClick());
  android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertNotNull(dialog); assertTrue(dialog.isShowing());
  assertEquals(a.getString(R.string.ui_019),org.robolectric.Shadows.shadowOf(dialog).getTitle().toString());
  assertFalse(CleanerService.running);
  View v=dialog.getWindow().getDecorView();
  v.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1900,View.MeasureSpec.AT_MOST));
  v.layout(0,0,1000,v.getMeasuredHeight());
  Bitmap bitmap=Bitmap.createBitmap(1000,v.getHeight(),Bitmap.Config.ARGB_8888); v.draw(new Canvas(bitmap));
  File f=new File("build/reports/ui/accessibility-setup.png"); f.getParentFile().mkdirs();
  try(FileOutputStream out=new FileOutputStream(f)) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); }
  dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  IntentCheck.assertAccessibilitySettings(a);
  a.finish();
 }
 @Test public void enabledButDisconnectedIsNotReportedAsMissingPermission() throws Exception {
  Context context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
  android.provider.Settings.Secure.putString(context.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
   new android.content.ComponentName(context,CleanerService.class).flattenToString());
  CleanerService.connected=null; CleanerService.running=false;
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  java.lang.reflect.Method status=MainActivity.class.getDeclaredMethod("updateStatus"); status.setAccessible(true); status.invoke(a);
  java.lang.reflect.Field access=MainActivity.class.getDeclaredField("access"); access.setAccessible(true);
  assertEquals(a.getString(R.string.ui_075),((android.widget.Button)access.get(a)).getText().toString());
  java.lang.reflect.Field clean=MainActivity.class.getDeclaredField("clean"); clean.setAccessible(true);
  ((android.widget.Button)clean.get(a)).performClick();
  android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertEquals(a.getString(R.string.ui_018),org.robolectric.Shadows.shadowOf(dialog).getTitle().toString());
  assertFalse(CleanerService.running); dialog.dismiss(); a.finish();
  android.provider.Settings.Secure.putString(context.getContentResolver(),android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,"");
 }
 @Test public void deniedUsageShowsIncompleteTotalAndRealProbeDetails() throws Exception {
  Context c=RuntimeEnvironment.getApplication();
  c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
  CleanerService.running=false; CleanerService.attempted=0;
  AppScanner.readState=AppScanner.DENIED; AppScanner.probePackage="com.example.other";
  AppScanner.probeError="SecurityException: PACKAGE_USAGE_STATS denied";
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  java.lang.reflect.Method status=MainActivity.class.getDeclaredMethod("updateStatus");status.setAccessible(true);status.invoke(a);
  java.lang.reflect.Field total=MainActivity.class.getDeclaredField("total");total.setAccessible(true);
  assertEquals(a.getString(R.string.ui_057),((android.widget.TextView)total.get(a)).getText().toString());
  java.lang.reflect.Field usage=MainActivity.class.getDeclaredField("usage");usage.setAccessible(true);
  android.widget.Button button=(android.widget.Button)usage.get(a);
  assertEquals(a.getString(R.string.ui_072),button.getText().toString()); button.performClick();
  android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertTrue(org.robolectric.Shadows.shadowOf(dialog).getMessage().toString().contains("com.example.other"));
  View v=a.getWindow().getDecorView();
  v.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2340,View.MeasureSpec.EXACTLY));v.layout(0,0,1080,2340);
  saveView(v,"usage-denied.png");
  v=dialog.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2200,View.MeasureSpec.AT_MOST));v.layout(0,0,1000,v.getMeasuredHeight());
  saveView(v,"usage-dialog.png");
  dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  android.content.Intent launched=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
  assertEquals(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS,launched.getAction());
  assertEquals("package:"+a.getPackageName(),launched.getDataString());
  java.lang.reflect.Field recheck=MainActivity.class.getDeclaredField("recheckUsage");recheck.setAccessible(true);assertTrue(recheck.getBoolean(a));
  assertFalse(CleanerService.running); a.finish(); AppScanner.readState=AppScanner.UNCHECKED;
 }
 private static void saveView(View v,String name) throws Exception {
  Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));
  File f=new File("build/reports/ui/"+name);f.getParentFile().mkdirs();
  try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}
 }
 @Test public void germanSetupHelpAndSelectAllAreReadable() throws Exception {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();CleanerService.running=false;
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  for(String method:new String[]{"showAndroidSetupHelp","confirmSelectAll"}) {
   java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(a);
   android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View v=d.getWindow().getDecorView();v.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2200,View.MeasureSpec.AT_MOST));v.layout(0,0,1000,v.getMeasuredHeight());saveView(v,"german-"+method+".png");d.dismiss();
  }a.finish();
 }
 private static class IntentCheck {
  static void assertAccessibilitySettings(MainActivity a) {
   org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  android.content.Intent launched=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
   assertNotNull(launched); assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,launched.getAction());
  }
 }
}

