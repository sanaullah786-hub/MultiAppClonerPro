package com.multiappcloner.pro;

import android.content.Context;
import android.util.Log;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

/**
 * Parallel-Space style cloning. The app is NOT installed into Android: its APK is loaded by the embedded
 * BlackBox engine and runs inside this app's processes, with its own private storage per virtual user.
 * Every virtual clone gets its own virtual user id, so each clone has a separate data area (its "database").
 */
public final class VirtualEngine {
    private static final String TAG = "VirtualEngine";

    public static boolean isAvailable() {
        try {
            return BlackBoxCore.get() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static String unavailableReason() {
        return "The embedded virtual engine could not start on this device.";
    }

    /** Copies the installed app into the virtual space of the given virtual user. Blocking. */
    public static ApkCloningEngine.Result install(Context c, String pkg, int userId) {
        try {
            if (pkg.equals(c.getPackageName())) return new ApkCloningEngine.Result(false, "This app cannot clone itself.");
            BlackBoxCore core = BlackBoxCore.get();
            try { core.createUser(userId); } catch (Throwable t) { Log.w(TAG, "createUser: " + t); }
            android.content.pm.ApplicationInfo ai = null;
            try { ai = c.getPackageManager().getApplicationInfo(pkg, android.content.pm.PackageManager.GET_META_DATA); } catch (Throwable ignored) { }
            java.io.File mergedApk = null;
            InstallResult r;
            if (ai != null && ai.splitSourceDirs != null && ai.splitSourceDirs.length > 0) {
                java.util.List<java.io.File> parts = new java.util.ArrayList<java.io.File>();
                parts.add(new java.io.File(ai.sourceDir));
                for (String s : ai.splitSourceDirs) parts.add(new java.io.File(s));
                java.io.File work = new java.io.File(c.getCacheDir(), "split_merge");
                work.mkdirs();
                mergedApk = SplitMerger.merge(parts, work, pkg);
            }
            if (mergedApk != null) {
                r = core.installPackageAsUser(mergedApk, userId);
                mergedApk.delete();
            } else {
                r = core.installPackageAsUser(pkg, userId);
            }
            if (r != null && r.success) {
                String extra = "";
                if (ai != null) {
                    if (mergedApk == null) copySplitLibs(ai, pkg);
                    if (usesGoogleServices(ai) && isGmsSupported() && !isGmsInstalled(userId)) {
                        ApkCloningEngine.Result g = installGms(userId);
                        extra = g.success ? " Google services were added for sign-in." : " Google services could not be added: " + g.message;
                    }
                }
                return new ApkCloningEngine.Result(true, "Virtual clone ready." + extra);
            }
            return new ApkCloningEngine.Result(false, "Virtual install failed: " + (r == null ? "no result" : r.msg));
        } catch (Throwable t) {
            Log.e(TAG, "install", t);
            return new ApkCloningEngine.Result(false, "Virtual install failed: " + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    /** Installs an APK file (not necessarily installed in Android) into the virtual space. Blocking. */
    public static ApkCloningEngine.Result installApk(Context c, java.io.File apk, int userId) {
        try {
            BlackBoxCore core = BlackBoxCore.get();
            try { core.createUser(userId); } catch (Throwable t) { Log.w(TAG, "createUser: " + t); }
            InstallResult r = core.installPackageAsUser(apk, userId);
            if (r != null && r.success) return new ApkCloningEngine.Result(true, "Installed in the virtual space.");
            return new ApkCloningEngine.Result(false, "Virtual install failed: " + (r == null ? "no result" : r.msg));
        } catch (Throwable t) {
            Log.e(TAG, "installApk", t);
            return new ApkCloningEngine.Result(false, "Virtual install failed: " + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    /** Native libraries of split APKs (modern apps ship them separately) must sit next to the base APK. */
    private static void copySplitLibs(android.content.pm.ApplicationInfo ai, String pkg) {
        if (ai.splitSourceDirs == null) return;
        for (String s : ai.splitSourceDirs) {
            try {
                top.niunaijun.blackbox.utils.NativeUtils.copyNativeLib(new java.io.File(s),
                    top.niunaijun.blackbox.core.env.BEnvironment.getAppLibDir(pkg));
            } catch (Throwable t) {
                Log.w(TAG, "split libs: " + t);
            }
        }
    }

    /** True when the app depends on Google Play services (it declares the GMS version meta-data). */
    public static boolean usesGoogleServices(android.content.pm.ApplicationInfo ai) {
        try {
            return ai != null && ai.metaData != null && ai.metaData.containsKey("com.google.android.gms.version");
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isGmsSupported() {
        try { return BlackBoxCore.get().isSupportGms(); } catch (Throwable t) { return false; }
    }

    public static boolean isGmsInstalled(int userId) {
        try { return BlackBoxCore.get().isInstallGms(userId); } catch (Throwable t) { return false; }
    }

    /** Adds this phone's own Google Play services, Play Store and Google Services Framework to the clone's space. */
    public static ApkCloningEngine.Result installGms(int userId) {
        try {
            InstallResult r = BlackBoxCore.get().installGms(userId);
            return r != null && r.success ? new ApkCloningEngine.Result(true, "Google services installed.")
                : new ApkCloningEngine.Result(false, r == null ? "no result" : String.valueOf(r.msg));
        } catch (Throwable t) {
            return new ApkCloningEngine.Result(false, t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    public static boolean uninstallGms(int userId) {
        try { return BlackBoxCore.get().uninstallGms(userId); } catch (Throwable t) { return false; }
    }

    public static boolean launch(String pkg, int userId) {
        try {
            return BlackBoxCore.get().launchApk(pkg, userId);
        } catch (Throwable t) {
            Log.e(TAG, "launch", t);
            return false;
        }
    }

    public static void stop(String pkg, int userId) {
        try { BlackBoxCore.get().stopPackage(pkg, userId); } catch (Throwable ignored) { }
    }

    /** Wipes the clone's data but keeps the clone. */
    public static boolean clearData(String pkg, int userId) {
        try { BlackBoxCore.get().clearPackage(pkg, userId); return true; } catch (Throwable t) { return false; }
    }

    /** Removes the clone and its data. */
    public static boolean remove(String pkg, int userId) {
        try {
            BlackBoxCore.get().stopPackage(pkg, userId);
            BlackBoxCore.get().uninstallPackageAsUser(pkg, userId);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "remove", t);
            return false;
        }
    }

    public static boolean isInstalled(String pkg, int userId) {
        try { return BlackBoxCore.get().isInstalled(pkg, userId); } catch (Throwable t) { return false; }
    }
}
