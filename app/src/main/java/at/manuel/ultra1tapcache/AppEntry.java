package at.manuel.ultra1tapcache;

final class AppEntry {
    final String packageName, label;
    final boolean system, enabled;
    Long cacheBytes;
    boolean selected;
    AppEntry(String packageName, String label, Long cacheBytes) {
        this(packageName, label, cacheBytes, false, true);
    }
    AppEntry(String packageName, String label, Long cacheBytes, boolean system, boolean enabled) {
        this.packageName = packageName; this.label = label; this.cacheBytes = cacheBytes;
        this.system = system; this.enabled = enabled;
        selected = !system && enabled && cacheBytes != null && cacheBytes > 0;
    }
}
