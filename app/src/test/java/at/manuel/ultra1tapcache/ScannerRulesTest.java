package at.manuel.ultra1tapcache;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScannerRulesTest {
    @Test public void selectAllIncludesMeasuredSystemAppsButNotUnavailableApps() {
        java.util.List<AppEntry> apps=java.util.Arrays.asList(new AppEntry("user","User",10L),new AppEntry("system","System",20L,true,true),new AppEntry("disabled","Disabled",10L,false,false),new AppEntry("unknown","Unknown",null),new AppEntry("zero","Zero",0L));
        MainActivity.selectAllMeasured(apps);
        assertTrue(apps.get(0).selected);assertTrue(apps.get(1).selected);
        assertFalse(apps.get(2).selected);assertFalse(apps.get(3).selected);assertFalse(apps.get(4).selected);
    }
    @Test public void crossAppProbeDoesNotMistakeUnknownOrDeniedForVerified() {
        assertEquals(AppScanner.UNKNOWN,AppScanner.probeState(0,0));
        assertEquals(AppScanner.DENIED,AppScanner.probeState(0,3));
        assertEquals(AppScanner.VERIFIED,AppScanner.probeState(1,2));
        assertEquals("DEFAULT",AppScanner.modeName(3));
    }

    @Test public void eligibilityKeepsUserAppsAndLauncherSystemAppsOnly() {
        assertTrue(AppScanner.eligible(false, false));
        assertTrue(AppScanner.eligible(false, true));
        assertTrue(AppScanner.eligible(true, true));
        assertFalse("hidden OS background packages stay out of the scan", AppScanner.eligible(true, false));
    }

    @Test public void systemDisabledZeroAndUnknownAppsStayUnselectedByDefault() {
        assertTrue(new AppEntry("a", "A", 100L, false, true).selected);
        assertFalse("system apps require explicit opt-in", new AppEntry("a", "A", 100L, true, true).selected);
        assertFalse(new AppEntry("a", "A", 100L, false, false).selected);
        assertFalse(new AppEntry("a", "A", null, false, true).selected);
        assertFalse(new AppEntry("a", "A", 0L, false, true).selected);
    }
}
