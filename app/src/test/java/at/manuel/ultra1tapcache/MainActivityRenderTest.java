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
@Config(sdk=35, qualifiers="w411dp-h891dp-420dpi")
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
}
