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

    static final int UNCHECKED=0, VERIFIED=1, DENIED=2, UNKNOWN=3;
    static volatile int readState=UNCHECKED;
    static volatile String probePackage, probeError;

    static int usageMode(Context c) {
        try {
            AppOpsManager ops=c.getSystemService(AppOpsManager.class);
            if(ops==null) return -1;
            return Build.VERSION.SDK_INT>=29
                ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.getPackageName())
                : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.getPackageName());
        } catch(RuntimeException e) { return -1; }
    }
    static boolean hasUsage(Context c) {
        int mode=usageMode(c);
        return mode==AppOpsManager.MODE_ALLOWED || (mode==AppOpsManager.MODE_DEFAULT
            && c.checkSelfPermission("android.permission.PACKAGE_USAGE_STATS")==PackageManager.PERMISSION_GRANTED);
    }
    static int probeState(int successes,int denials) {
        return successes>0 ? VERIFIED : denials>0 ? DENIED : UNKNOWN;
    }
    static String modeName(int mode) {
        switch(mode) {
            case AppOpsManager.MODE_ALLOWED:return "ALLOWED";
            case AppOpsManager.MODE_DEFAULT:return "DEFAULT";
            case AppOpsManager.MODE_IGNORED:return "IGNORED";
            case AppOpsManager.MODE_ERRORED:return "ERRORED";
            case AppOpsManager.MODE_FOREGROUND:return "FOREGROUND";
            default:return "UNBEKANNT ("+mode+")";
        }
    }
    // Own-UID stats need no grant. Probe only OTHER installed apps on the scan worker.
    private static void probe(Context c,List<ApplicationInfo> candidates) {
        readState=UNCHECKED; probePackage=null; probeError=null;
        int successes=0,denials=0,attempts=0;
        StorageStatsManager ssm=c.getSystemService(StorageStatsManager.class);
        if(ssm!=null) for(ApplicationInfo info:candidates) {
            if(info.uid==Process.myUid() || !info.enabled) continue;
            if(Thread.currentThread().isInterrupted() || attempts++>=3) break;
            probePackage=info.packageName;
            UUID volume=info.storageUuid==null?StorageManager.UUID_DEFAULT:info.storageUuid;
            try {
                ssm.queryStatsForPackage(volume,info.packageName,Process.myUserHandle());
                successes++; probeError=null; break;
            } catch(SecurityException e) { denials++; probeError=e.getClass().getSimpleName()+": "+e.getMessage(); }
            catch(Exception e) { if(probeError==null) probeError=e.getClass().getSimpleName()+": "+e.getMessage(); }
        }
        readState=probeState(successes,denials);
        if(readState==DENIED) lastError=probeError;
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
        List<ApplicationInfo> candidates=new ArrayList<>();
        for(ApplicationInfo info:pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)) {
            boolean system=(info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
            if(eligible(system,hasLauncher(pm,info.packageName))) candidates.add(info);
        }
        probe(c,candidates);
        for (ApplicationInfo info : candidates) {
            if (Thread.currentThread().isInterrupted()) break;
            boolean system = (info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (!eligible(system, hasLauncher(pm, info.packageName))) continue;
            Long bytes = readState==DENIED && info.uid!=Process.myUid() ? null : cache(c, info);
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
