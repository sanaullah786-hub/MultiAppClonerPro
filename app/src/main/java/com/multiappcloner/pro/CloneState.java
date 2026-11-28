package com.multiappcloner.pro;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Helpers that turn a stored profile into what is really on the device right now. */
public final class CloneState {
    public static boolean isInstalled(Context c, String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Reconciles the stored status with the package manager (e.g. the user uninstalled the clone in Settings). */
    public static CloneProfile sync(Context c, CloneRepository repo, CloneProfile p) {
        if (p == null) return null;
        if (CloneRepository.METHOD_VIRTUAL.equals(p.method)) return p; // virtual clones live inside this app; the DB is the source of truth
        boolean installed = isInstalled(c, p.clonePackage);
        if (CloneRepository.STATUS_ACTIVE.equals(p.status) && !installed) {
            repo.setState(p.id, CloneRepository.STATUS_REMOVED, null, null);
            new LogRepository(c).add(p.id, "Clone app is no longer installed");
            p.status = CloneRepository.STATUS_REMOVED;
        } else if ((CloneRepository.STATUS_INSTALLING.equals(p.status) || CloneRepository.STATUS_FAILED.equals(p.status)
            || CloneRepository.STATUS_REMOVED.equals(p.status)) && installed) {
            repo.setState(p.id, CloneRepository.STATUS_ACTIVE, null, null);
            p.status = CloneRepository.STATUS_ACTIVE;
        }
        return p;
    }

    public static String label(CloneProfile p) {
        if (CloneRepository.STATUS_ACTIVE.equals(p.status))
            return CloneRepository.METHOD_VIRTUAL.equals(p.method) ? "Virtual clone: tap to open or manage" : "Installed clone: tap to open or manage";
        if (CloneRepository.STATUS_INSTALLING.equals(p.status)) return "Waiting for the install to finish";
        if (CloneRepository.STATUS_FAILED.equals(p.status)) return "Install failed: open details";
        if (CloneRepository.STATUS_REMOVED.equals(p.status)) return "Clone app was removed: you can reinstall it";
        return "Profile record only (no clone installed)";
    }

    /** Deletes a virtual clone's data area when its profile is deleted. Installed clones are left for the user to uninstall. */
    public static void cleanup(CloneProfile p) {
        if (p != null && CloneRepository.METHOD_VIRTUAL.equals(p.method) && p.virtualUser >= 0) VirtualEngine.remove(p.packageName, p.virtualUser);
    }

    /**
     * Opens a clone from a screen. Games and browsers save files to shared storage; on Android 11+ that needs
     * "All files access" for this app, so it is requested once before the first virtual launch.
     */
    public static void open(final android.app.Activity a, final CloneProfile p) {
        boolean virt = CloneRepository.METHOD_VIRTUAL.equals(p.method);
        boolean needs = virt && android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager();
        android.content.SharedPreferences sp = a.getSharedPreferences("clone_state", Context.MODE_PRIVATE);
        if (needs && !sp.getBoolean("asked_all_files", false)) {
            sp.edit().putBoolean("asked_all_files", true).apply();
            Sheet.actions(a, null, "Allow file access", "Needed by browsers, games and chat apps")
                .item("📂", "Allow all files access", "Opens Android settings, switch it on and come back", Ui.GREEN, new Runnable() {
                    public void run() {
                        try {
                            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                android.net.Uri.parse("package:" + a.getPackageName()));
                            a.startActivity(i);
                        } catch (Exception e) {
                            a.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    }
                })
                .item("▶", "Open without it", "Some file operations may fail", Ui.ORANGE, new Runnable() {
                    public void run() { launchOrExplain(a, p); }
                }).show();
            return;
        }
        launchOrExplain(a, p);
    }

    /** Stops every running virtual clone (frees RAM). */
    public static void stopAll(Context c) {
        try {
            for (CloneProfile q : new CloneRepository(c).getAll())
                if (CloneRepository.METHOD_VIRTUAL.equals(q.method)) VirtualEngine.stop(q.packageName, q.virtualUser);
        } catch (Throwable ignored) { }
    }

    private static boolean lowMemory(Context c) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.lowMemory || mi.availMem < 450L * 1024 * 1024;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void launchOrExplain(final android.app.Activity a, final CloneProfile p) {
        if (CloneRepository.METHOD_VIRTUAL.equals(p.method)) {
            int running = CloneBackup.runningCloneProcesses(a), limit = CloneBackup.cloneLimit(a);
            if (running >= limit) {
                Sheet.actions(a, null, "Clone limit reached", running + " clone processes are running (limit " + limit + ")")
                    .item("⏹", "Stop other clones, then open", "Frees memory for this one", Ui.GREEN, new Runnable() {
                        public void run() { stopAll(a); if (!launch(a, p)) EngineLog.launchFailed(a, p.cloneName); }
                    })
                    .item("▶", "Open anyway", "The phone may become slow", Ui.ORANGE, new Runnable() {
                        public void run() { if (!launch(a, p)) EngineLog.launchFailed(a, p.cloneName); }
                    }).show();
                return;
            }
        }
        if (CloneRepository.METHOD_VIRTUAL.equals(p.method) && lowMemory(a)) {
            Sheet.actions(a, null, "Low memory", "Clones run in this phone's RAM")
                .item("⏹", "Stop other clones, then open", "Frees memory for this one", Ui.GREEN, new Runnable() {
                    public void run() { stopAll(a); if (!launch(a, p)) EngineLog.launchFailed(a, p.cloneName); }
                })
                .item("▶", "Open anyway", "The app may hang or close", Ui.ORANGE, new Runnable() {
                    public void run() { if (!launch(a, p)) EngineLog.launchFailed(a, p.cloneName); }
                }).show();
            return;
        }
        if (!launch(a, p)) EngineLog.launchFailed(a, p.cloneName);
    }

    public static boolean launch(Context c, CloneProfile p) {
        if (CloneRepository.METHOD_VIRTUAL.equals(p.method)) return VirtualEngine.launch(p.packageName, p.virtualUser);
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(p.clonePackage);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
