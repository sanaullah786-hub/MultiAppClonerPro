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
            InstallResult r = core.installPackageAsUser(pkg, userId);
            if (r != null && r.success) return new ApkCloningEngine.Result(true, "Virtual clone ready.");
            return new ApkCloningEngine.Result(false, "Virtual install failed: " + (r == null ? "no result" : r.msg));
        } catch (Throwable t) {
            Log.e(TAG, "install", t);
            return new ApkCloningEngine.Result(false, "Virtual install failed: " + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
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
