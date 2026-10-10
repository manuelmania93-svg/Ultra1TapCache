package at.manuel.ultra1tapcache;

import android.app.AppOpsManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
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

    /** Official StorageStatsManager only; tries package query, then default volume, then single-package UID query. Null = genuinely not measurable. */
    static Long cache(Context c, ApplicationInfo info) {
        StorageStatsManager ssm = c.getSystemService(StorageStatsManager.class);
        if (ssm == null) { if (lastError == null) lastError = "StorageStatsManager fehlt"; return null; }
        UUID own = info.storageUuid == null ? StorageManager.UUID_DEFAULT : info.storageUuid;
        try { return ssm.queryStatsForPackage(own, info.packageName, Process.myUserHandle()).getCacheBytes(); } catch (Exception e) { note(e); }
        if (!StorageManager.UUID_DEFAULT.equals(own)) {
            try { return ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, Process.myUserHandle()).getCacheBytes(); } catch (Exception e) { note(e); }
        }
        // A shared UID total is not a per-app value and would double-count other packages.
        String[] packages = c.getPackageManager().getPackagesForUid(info.uid);
        if (packages != null && packages.length == 1 && info.packageName.equals(packages[0])) {
            try { return ssm.queryStatsForUid(own, info.uid).getCacheBytes(); } catch (Exception e) { note(e); }
        }
        return null;
    }

    static Long cache(Context c, String pkg) {
        try { return cache(c, c.getPackageManager().getApplicationInfo(pkg, 0)); }
        catch (Exception e) { note(e); return null; }
    }

    static boolean eligible(boolean system, boolean launcher) { return !system || launcher; }

    private static boolean hasLauncher(PackageManager pm, String pkg) {
        // Launch intents omit disabled components. Query with MATCH_DISABLED_COMPONENTS instead.
        for (String category : new String[]{Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(pkg);
            if (!pm.queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS).isEmpty()) return true;
        }
        return false;
    }

    // Display filters must never affect scan coverage. Hidden OS services are out of scope.
    static List<AppEntry> scan(Context c, boolean unusedDisplayFilter) {
        lastError = null;
        PackageManager pm = c.getPackageManager();
        List<AppEntry> result = new ArrayList<>();
        for (ApplicationInfo info : pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)) {
            if (Thread.currentThread().isInterrupted()) break;
            boolean system = (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (!eligible(system, hasLauncher(pm, info.packageName))) continue;
            Long bytes = cache(c, info);
            String label;
            try { label = info.loadLabel(pm).toString(); }
            catch (RuntimeException e) { label = info.packageName; }
            result.add(new AppEntry(info.packageName, label, bytes, system, info.enabled));
        }
        result.sort((a,b) -> {
            long ac = a.cacheBytes == null ? -1 : a.cacheBytes, bc = b.cacheBytes == null ? -1 : b.cacheBytes;
            int order = Long.compare(bc, ac);
            return order != 0 ? order : a.label.compareToIgnoreCase(b.label);
        });
        return result;
    }
}
