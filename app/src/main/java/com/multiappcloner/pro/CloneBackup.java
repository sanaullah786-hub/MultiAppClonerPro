package com.multiappcloner.pro;

import android.content.Context;

import com.multiappcloner.pro.backup.BackupEngine;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import top.niunaijun.blackbox.core.env.BEnvironment;

/** Backup, restore and size of a virtual clone's data (Android side of {@link BackupEngine}). */
public final class CloneBackup {

    public static final class Item {
        public File file; public BackupEngine.Manifest manifest; public String error;
    }

    public static File dir(Context c) {
        File d = c.getExternalFilesDir("clone_backups");
        if (d == null) d = new File(c.getFilesDir(), "clone_backups");
        d.mkdirs();
        return d;
    }

    /** The folders that hold one clone's data. */
    public static Map<String, File> roots(String pkg, int user) {
        Map<String, File> m = new LinkedHashMap<String, File>();
        m.put("data", BEnvironment.getDataDir(pkg, user));
        m.put("de", BEnvironment.getDeDataDir(pkg, user));
        m.put("ext", BEnvironment.getExternalDataDir(pkg, user));
        return m;
    }

    public static long sizeOf(CloneProfile p) {
        long t = 0;
        for (File f : roots(p.packageName, p.virtualUser).values()) t += BackupEngine.sizeOf(f);
        return t;
    }

    public static String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }

    public static File backup(Context c, CloneProfile p, BackupEngine.Progress pr) throws Exception {
        long need = sizeOf(p);
        File dir = dir(c);
        if (dir.getUsableSpace() < need + 20L * 1024 * 1024)
            throw new java.io.IOException("Not enough free space: the backup needs about " + human(need) + ".");
        VirtualEngine.stop(p.packageName, p.virtualUser); // never copy files an app is writing
        Thread.sleep(600);
        String ver = "";
        try { ver = c.getPackageManager().getPackageInfo(p.packageName, 0).versionName; } catch (Throwable ignored) { }
        File out = new File(dir, p.packageName + "_u" + p.virtualUser + "_" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".mbk");
        BackupEngine.create(out, p.packageName, p.virtualUser, ver, roots(p.packageName, p.virtualUser), pr);
        Diag.event(Diag.STORAGE, "backup created " + out.getName() + " (" + human(out.length()) + ")");
        return out;
    }

    public static void restore(Context c, File backup, CloneProfile target, BackupEngine.Progress pr) throws Exception {
        VirtualEngine.stop(target.packageName, target.virtualUser);
        Thread.sleep(600);
        try {
            BackupEngine.restore(backup, target.packageName, roots(target.packageName, target.virtualUser), pr);
            Diag.event(Diag.STORAGE, "restored " + backup.getName() + " into user " + target.virtualUser);
        } catch (Exception e) {
            Diag.event(Diag.STORAGE, "restore failed for " + backup.getName() + ": " + e.getMessage());
            throw e;
        }
    }

    public static List<File> list(Context c) {
        File[] f = dir(c).listFiles();
        List<File> out = new ArrayList<File>();
        if (f != null) for (File x : f) if (x.getName().endsWith(".mbk")) out.add(x);
        java.util.Collections.sort(out, new java.util.Comparator<File>() {
            public int compare(File a, File b) { return a.lastModified() < b.lastModified() ? 1 : -1; }
        });
        return out;
    }

    /** Clone processes running right now (processes named <this app>:pN). */
    public static int runningCloneProcesses(Context c) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            int n = 0;
            String prefix = c.getPackageName() + ":p";
            for (android.app.ActivityManager.RunningAppProcessInfo i : am.getRunningAppProcesses())
                if (i.processName != null && i.processName.startsWith(prefix)) n++;
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int cloneLimit(Context c) {
        int def = com.multiappcloner.pro.backup.CloneLimit.defaultFor(totalRamMb(c));
        return com.multiappcloner.pro.backup.CloneLimit.clamp(c.getSharedPreferences("clone_state", Context.MODE_PRIVATE).getInt("process_limit", def));
    }

    public static void setCloneLimit(Context c, int v) {
        c.getSharedPreferences("clone_state", Context.MODE_PRIVATE).edit().putInt("process_limit", com.multiappcloner.pro.backup.CloneLimit.clamp(v)).apply();
    }

    public static long totalRamMb(Context c) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.totalMem >> 20;
        } catch (Throwable t) {
            return 3000;
        }
    }
}
