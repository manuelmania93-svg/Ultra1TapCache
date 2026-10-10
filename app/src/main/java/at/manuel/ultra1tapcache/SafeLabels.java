package at.manuel.ultra1tapcache;

import java.util.Locale;
import java.util.Set;

/** Exact allowlist: never use a substring match for destructive controls. */
public final class SafeLabels {
    private SafeLabels() {}
    public static String normalize(CharSequence value) {
        return value == null ? "" : value.toString().trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static final Set<String> CACHE = Set.of("cache leeren", "cache löschen", "clear cache");
    private static final Set<String> STORAGE = Set.of("speicher", "speicher und cache", "speicher & cache", "speichernutzung", "storage", "storage & cache", "storage and cache", "storage usage");
    public static boolean cache(CharSequence value) { return CACHE.contains(normalize(value)); }
    public static boolean storage(CharSequence value) { return STORAGE.contains(normalize(value)); }
    public static boolean danger(CharSequence value) {
        String text = normalize(value);
        return text.contains("daten löschen") || text.contains("clear data") || text.contains("clear storage") || text.contains("speicherinhalt löschen") || text.contains("deinstallieren") || text.contains("uninstall") || text.contains("zurücksetzen") || text.contains("factory reset");
    }
}
