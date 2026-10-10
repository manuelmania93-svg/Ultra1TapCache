package at.manuel.ultra1tapcache;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class RunJournalTest {
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
 }
}
