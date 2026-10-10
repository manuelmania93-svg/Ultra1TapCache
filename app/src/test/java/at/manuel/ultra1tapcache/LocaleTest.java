package at.manuel.ultra1tapcache;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.LocaleList;
import android.view.View;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="en-w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LocaleTest {
 @Test public void resourcesFollowGermanEnglishAndFallback() {
  Context c=RuntimeEnvironment.getApplication();
  for(String code:new String[]{"de","en","fr"}) {
   Configuration cfg=new Configuration(c.getResources().getConfiguration());cfg.setLocales(new LocaleList(Locale.forLanguageTag(code)));
   Context localized=c.createConfigurationContext(cfg);
   assertEquals("Ultra1TapCache",localized.getString(R.string.app_name));
   assertEquals(code.equals("de")?"Neu scannen":"Scan again",localized.getString(R.string.ui_010));
   assertTrue(localized.getString(R.string.service_description).contains(code.equals("de")?"Cache leeren":"Clear cache"));
  }
 }
 @Test public void englishMainAndSetupAreReadable() throws Exception {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
  CleanerService.running=false;CleanerService.attempted=0;CleanerService.connected=null;AppScanner.readState=AppScanner.DENIED;
  AppScanner.probePackage="com.example.browser";AppScanner.probeError="SecurityException: Caller does not have android.permission.PACKAGE_USAGE_STATS; callingPackage=at.manuel.ultra1tapcache, callingUid=10890";
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("updateStatus");m.setAccessible(true);m.invoke(a);
  java.lang.reflect.Field f=MainActivity.class.getDeclaredField("usage");f.setAccessible(true);
  Button b=(Button)f.get(a);assertEquals("! Allow cache access",b.getText().toString());
  View v=a.getWindow().getDecorView();render(v,"english-main.png",1080,2340,true);
  b.performClick();AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertEquals("Check cache access",org.robolectric.Shadows.shadowOf(d).getTitle().toString());
  render(d.getWindow().getDecorView(),"english-usage-dialog.png",1000,2200,false);d.dismiss();
  f=MainActivity.class.getDeclaredField("access");f.setAccessible(true);((Button)f.get(a)).performClick();
  d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertEquals("Set up cleaning",org.robolectric.Shadows.shadowOf(d).getTitle().toString());
  render(d.getWindow().getDecorView(),"english-cleaning-setup.png",1000,2200,false);d.dismiss();a.finish();AppScanner.readState=AppScanner.UNCHECKED;
 }
 @Test public void setupHelpOpensOnlyOurAppInfoWithoutCleaning() throws Exception {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();
  CleanerService.running=false;
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("showAndroidSetupHelp");m.setAccessible(true);m.invoke(a);
  AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
  assertTrue(org.robolectric.Shadows.shadowOf(d).getMessage().toString().contains("if Android offers it"));
  render(d.getWindow().getDecorView(),"english-restricted-help.png",1000,2200,false);
  d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  android.content.Intent i=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();
  assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,i.getAction());assertEquals("package:"+a.getPackageName(),i.getDataString());assertFalse(CleanerService.running);a.finish();
 }
 @Test public void allSystemSelectionDialogIsReadable() throws Exception {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("settings",0).edit().putBoolean("disclosure_seen",true).commit();CleanerService.running=false;
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("confirmSelectAll");m.setAccessible(true);m.invoke(a);
  AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(org.robolectric.Shadows.shadowOf(d).getMessage().toString().contains("does not start cleaning"));render(d.getWindow().getDecorView(),"english-select-all.png",1000,2200,false);d.dismiss();a.finish();
 }
 private static void render(View v,String name,int width,int height,boolean exact) throws Exception {
  v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,exact?View.MeasureSpec.EXACTLY:View.MeasureSpec.AT_MOST));v.layout(0,0,width,v.getMeasuredHeight());
  Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));File f=new File("build/reports/ui/"+name);f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}
 }
               }
