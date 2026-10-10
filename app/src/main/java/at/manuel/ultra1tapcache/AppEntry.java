package at.manuel.ultra1tapcache;

final class AppEntry {
    final String packageName, label;
    Long cacheBytes;
    boolean selected;
    AppEntry(String packageName, String label, Long cacheBytes) {
        this.packageName = packageName; this.label = label; this.cacheBytes = cacheBytes;
        selected = cacheBytes != null && cacheBytes > 0;
    }
}
