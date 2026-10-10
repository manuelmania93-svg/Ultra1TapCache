package at.manuel.ultra1tapcache;

import android.app.AppOpsManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.os.storage.StorageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class AppScanner {
    /** First failure seen during the last scan, shown in the UI so a failed measurement is never silent. */
    static volatile String lastError;

    static boolean hasUsage(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        else mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        if (mode == AppOpsManager.MODE_ALLOWED) return true;
        // Some ROMs (MIUI) report MODE_DEFAULT while the permission is granted.
        return mode == AppOpsManager.MODE_DEFAULT
            && c.checkSelfPermission("android.permission.PACKAGE_USAGE_STATS") == PackageManager.PERMISSION_GRANTED;
    }

    private static void note(Exception e) {
        if (lastError == null) lastError = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
    }

    /** Official StorageStatsManager only; tries package query, then default volume, then UID query. Null = genuinely not measurable. */
    static Long cache(Context c, ApplicationInfo info) {
        StorageStatsManager ssm = c.getSystemService(StorageStatsManager.class);
        if (ssm == null) { if (lastError == null) lastError = "StorageStatsManager fehlt"; return null; }
        UUID own = info.storageUuid == null ? StorageManager.UUID_DEFAULT : info.storageUuid;
        try { return ssm.queryStatsForPackage(own, info.packageName, Process.myUserHandle()).getCacheBytes(); } catch (Exception e) { note(e); }
        if (!StorageManager.UUID_DEFAULT.equals(own)) {
            try { return ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, Process.myUserHandle()).getCacheBytes(); } catch (Exception e) { note(e); }
        }
        try { return ssm.queryStatsForUid(own, info.uid).getCacheBytes(); } catch (Exception e) { note(e); }
        return null;
    }

    static List<AppEntry> scan(Context c, boolean includeSystem) {
        lastError = null;
        PackageManager pm = c.getPackageManager();
        List<AppEntry> result = new ArrayList<>();
        for (ApplicationInfo info : pm.getInstalledApplications(0)) {
            if (Thread.currentThread().isInterrupted()) break;
            if (info.packageName.equals(c.getPackageName()) || info.packageName.equals("com.android.settings") || !info.enabled) continue;
            if (!includeSystem && (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            result.add(new AppEntry(info.packageName, info.loadLabel(pm).toString(), cache(c, info)));
        }
        result.sort((a,b) -> {
            long ac = a.cacheBytes == null ? -1 : a.cacheBytes, bc = b.cacheBytes == null ? -1 : b.cacheBytes;
            int order = Long.compare(bc, ac);
            return order != 0 ? order : a.label.compareToIgnoreCase(b.label);
        });
        return result;
    }
}
