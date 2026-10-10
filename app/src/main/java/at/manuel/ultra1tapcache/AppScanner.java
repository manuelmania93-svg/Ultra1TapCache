package at.manuel.ultra1tapcache;

import android.app.AppOpsManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.storage.StorageManager;
import java.util.ArrayList;
import java.util.List;

final class AppScanner {
    static boolean hasUsage(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
    static Long cache(Context c, String pkg) {
        if (!hasUsage(c)) return null;
        try {
            ApplicationInfo info = c.getPackageManager().getApplicationInfo(pkg, 0);
            return c.getSystemService(StorageStatsManager.class).queryStatsForPackage(
                info.storageUuid == null ? StorageManager.UUID_DEFAULT : info.storageUuid, pkg, Process.myUserHandle()).getCacheBytes();
        } catch (Exception e) { return null; }
    }
    static List<AppEntry> scan(Context c, boolean includeSystem) {
        PackageManager pm = c.getPackageManager();
        List<AppEntry> result = new ArrayList<>();
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            if (Thread.currentThread().isInterrupted()) break;
            if (info.packageName.equals(c.getPackageName()) || info.packageName.equals("com.android.settings") || !info.enabled) continue;
            if (!includeSystem && (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            result.add(new AppEntry(info.packageName, info.loadLabel(pm).toString(), cache(c, info.packageName)));
        }
        result.sort((a,b) -> {
            long ac = a.cacheBytes == null ? -1 : a.cacheBytes, bc = b.cacheBytes == null ? -1 : b.cacheBytes;
            int order = Long.compare(bc, ac);
            return order != 0 ? order : a.label.compareToIgnoreCase(b.label);
        });
        return result;
    }
}
