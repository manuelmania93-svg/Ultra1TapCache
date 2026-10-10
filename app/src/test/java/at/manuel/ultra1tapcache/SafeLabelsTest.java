package at.manuel.ultra1tapcache;
import org.junit.Test;
import static org.junit.Assert.*;
public class SafeLabelsTest {
    @Test public void germanCache() { assertTrue(SafeLabels.cache("Cache leeren")); assertTrue(SafeLabels.cache("CACHE LÖSCHEN")); }
    @Test public void englishCache() { assertTrue(SafeLabels.cache("Clear cache")); }
    @Test public void exactOnly() { assertFalse(SafeLabels.cache("Clear cache and data")); assertFalse(SafeLabels.cache("Cache")); assertFalse(SafeLabels.cache("Clear storage")); }
    @Test public void nullSafe() { assertFalse(SafeLabels.cache(null)); assertFalse(SafeLabels.storage(null)); assertFalse(SafeLabels.danger(null)); }
    @Test public void whitespaceNormalized() { assertTrue(SafeLabels.cache("  clear   cache  ")); }
    @Test public void germanStorage() { assertTrue(SafeLabels.storage("Speicher & Cache")); assertTrue(SafeLabels.storage("Speichernutzung")); }
    @Test public void englishStorage() { assertTrue(SafeLabels.storage("Storage & cache")); }
    @Test public void destructiveLabelsNeverCache() { for(String s : new String[]{"Daten löschen","Clear data","Clear storage","Deinstallieren","Uninstall","Factory reset"}) { assertTrue(SafeLabels.danger(s)); assertFalse(SafeLabels.cache(s)); assertFalse(SafeLabels.storage(s)); } }
    @Test public void compositeDangerDetected() { assertTrue(SafeLabels.danger("Cache leeren / Daten löschen")); }
    @Test public void unknownLabelsFailClosed() { assertFalse(SafeLabels.cache("Purge")); assertFalse(SafeLabels.storage("Manage storage")); }
}
