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
            prepareUser(core, pkg, userId);
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
                mergedApk = SplitMerger.merge(c, parts, work, pkg);
            }
            java.util.List<java.io.File> parts2 = new java.util.ArrayList<java.io.File>();
            if (mergedApk != null) parts2.add(mergedApk);
            else if (ai != null) {
                parts2.add(new java.io.File(ai.sourceDir));
                if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) parts2.add(new java.io.File(s));
            }
            String notes = compatNotes(pkg, parts2, ai != null && ai.sharedLibraryFiles != null && ai.sharedLibraryFiles.length > 0,
                ai != null && ai.splitSourceDirs != null && ai.splitSourceDirs.length > 0 && mergedApk == null);
            recordSharedLibraries(pkg, ai);
            if (mergedApk != null) {
                r = core.installPackageAsUser(mergedApk, userId);
                for (int tries = 0; r == null && tries < 2; tries++) { retryPause("install", pkg, userId); r = core.installPackageAsUser(mergedApk, userId); }
                mergedApk.delete();
            } else {
                r = core.installPackageAsUser(pkg, userId);
                for (int tries = 0; r == null && tries < 2; tries++) { retryPause("install", pkg, userId); r = core.installPackageAsUser(pkg, userId); }
            }
            if (r != null && r.success) {
                String extra = notes;
                if (ai != null) {
                    if (mergedApk == null) copySplitLibs(ai, pkg);
                    if (usesGoogleServices(ai) && isGmsSupported() && !isGmsInstalled(userId)) {
                        ApkCloningEngine.Result g = installGms(userId);
                        extra = g.success ? " Google services were added for sign-in." : " Google services could not be added: " + g.message;
                    }
                }
                Diag.event(Diag.INSTALL, "virtual install ok: " + pkg + " user " + userId + (mergedApk != null ? " (splits merged)" : ""));
                return new ApkCloningEngine.Result(true, "Virtual clone ready." + extra);
            }
            String why = "Virtual install failed: " + (r == null ? "the engine returned no result" : r.msg);
            Diag.event(Diag.INSTALL, why + " [" + pkg + " user " + userId + "]" + (notes.length() > 0 ? " notes:" + notes : ""));
            return new ApkCloningEngine.Result(false, why + notes);
        } catch (Throwable t) {
            Log.e(TAG, "install", t);
            Diag.event(Diag.INSTALL, "virtual install exception for " + pkg + ": " + t);
            return new ApkCloningEngine.Result(false, "Virtual install failed: " + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    /** Installs an APK file (not necessarily installed in Android) into the virtual space. Blocking. */
    public static ApkCloningEngine.Result installApk(Context c, java.io.File apk, int userId) {
        try {
            BlackBoxCore core = BlackBoxCore.get();
            InstallResult r = null;
            String pkgName = packageOf(c, apk);
            prepareUser(core, pkgName, userId);
            String notes = compatNotes(pkgName, java.util.Collections.singletonList(apk), false, false);
            r = core.installPackageAsUser(apk, userId);
            for (int tries = 0; r == null && tries < 2; tries++) { retryPause("install", pkgName, userId); r = core.installPackageAsUser(apk, userId); }
            if (r != null && r.success) {
                Diag.event(Diag.INSTALL, "virtual apk install ok: " + pkgName + " user " + userId);
                return new ApkCloningEngine.Result(true, "Installed in the virtual space." + notes);
            }
            String why = "Virtual install failed: " + (r == null ? "the engine returned no result" : r.msg);
            Diag.event(Diag.INSTALL, why + " [" + pkgName + " user " + userId + "]" + (notes.length() > 0 ? " notes:" + notes : ""));
            return new ApkCloningEngine.Result(false, why + notes);
        } catch (Throwable t) {
            Log.e(TAG, "installApk", t);
            Diag.event(Diag.INSTALL, "virtual apk install exception: " + t);
            return new ApkCloningEngine.Result(false, "Virtual install failed: " + t.getClass().getSimpleName()
                + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    /** The engine's service process may still be starting or recovering; wait briefly before asking again. */
    private static void retryPause(String what, String pkg, int userId) {
        Diag.event(Diag.ENGINE, "engine gave no answer for " + what + " of " + pkg + " user " + userId + ", retrying in 1.5 s");
        try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    /** What the engine knows about a package right now, to explain a refused launch. */
    private static String launchProbe(String pkg, int userId) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("installed=").append(BlackBoxCore.get().isInstalled(pkg, userId));
        } catch (Throwable t) { sb.append("installed=?(").append(t.getClass().getSimpleName()).append(')'); }
        String[] cats = {android.content.Intent.CATEGORY_LAUNCHER, android.content.Intent.CATEGORY_INFO};
        for (String cat : cats) {
            try {
                android.content.Intent q = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                q.addCategory(cat);
                q.setPackage(pkg);
                java.util.List<android.content.pm.ResolveInfo> l = BlackBoxCore.getBPackageManager().queryIntentActivities(q, 0, null, userId);
                sb.append(' ').append(cat.substring(cat.lastIndexOf('.') + 1)).append("_activities=").append(l == null ? "null" : String.valueOf(l.size()));
            } catch (Throwable t) { sb.append(' ').append(cat.substring(cat.lastIndexOf('.') + 1)).append("=?(").append(t.getClass().getSimpleName()).append(')'); }
        }
        return sb.toString();
    }

    private static String packageOf(Context c, java.io.File apk) {
        try {
            android.content.pm.PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return pi == null ? null : pi.packageName;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Makes sure the virtual user exists and holds no data left over from an earlier clone that used the same id.
     * User ids are never reused (see CloneRepository.nextVirtualUser), so a new clone always starts empty.
     */
    private static void prepareUser(BlackBoxCore core, String pkg, int userId) {
        try { core.createUser(userId); } catch (Throwable t) { Log.w(TAG, "createUser: " + t); }
        if (pkg != null && !isInstalled(pkg, userId)) wipeData(pkg, userId);
    }

    /** Deletes this clone's private and shared-storage data folders. Only this package and this user id are touched. */
    public static void wipeData(String pkg, int userId) {
        if (pkg == null || pkg.length() == 0 || userId < 0 || pkg.contains("/") || pkg.contains("..")) return;
        try {
            deleteTree(top.niunaijun.blackbox.core.env.BEnvironment.getDataDir(pkg, userId));
            deleteTree(top.niunaijun.blackbox.core.env.BEnvironment.getDeDataDir(pkg, userId));
            deleteTree(top.niunaijun.blackbox.core.env.BEnvironment.getExternalDataDir(pkg, userId));
        } catch (Throwable t) {
            Diag.event(Diag.STORAGE, "wipe failed for " + pkg + " user " + userId + ": " + t);
        }
    }

    private static void deleteTree(java.io.File f) {
        if (f == null || !f.exists()) return;
        java.io.File[] kids = f.listFiles();
        if (kids != null) for (java.io.File k : kids) deleteTree(k);
        f.delete();
    }

    /** Plain-language warnings found before installing. Also written to the diagnostics log. */
    private static String compatNotes(String pkg, java.util.List<java.io.File> apks, boolean hasSharedLibs, boolean splitsNotMerged) {
        StringBuilder sb = new StringBuilder();
        try {
            boolean staticLib = hasSharedLibs;
            java.util.Set<String> abis = new java.util.LinkedHashSet<String>();
            for (java.io.File f : apks) {
                if (f == null || !f.exists()) continue;
                if (com.multiappcloner.pro.diag.AbiScan.usesStaticLibrary(f)) staticLib = true;
                abis.addAll(com.multiappcloner.pro.diag.AbiScan.abis(f));
            }
            if (staticLib) sb.append(" Note: this app loads code from a separate library package (like Chrome's Trichrome), which the virtual space cannot provide, so it may not start.");
            if (!splitsNotMerged) {
                String abi = com.multiappcloner.pro.diag.AbiScan.incompatibility(abis, android.os.Build.SUPPORTED_ABIS);
                if (abi != null) sb.append(" Note: ").append(abi);
            } else {
                sb.append(" Note: its split APKs could not be merged, so parts of the app (language, graphics, native code) may be missing.");
            }
        } catch (Throwable t) {
            Log.w(TAG, "compat check: " + t);
        }
        String n = sb.toString();
        if (n.length() > 0) Diag.event(Diag.COMPAT, (pkg == null ? "?" : pkg) + n);
        return n;
    }

    /**
     * Apps such as Chrome load code, resources and native libraries from a separate library package (Trichrome).
     * Its path is saved for the engine, and its native libraries are copied next to the app's own.
     * EXPERIMENTAL: not verified on a device.
     */
    private static void recordSharedLibraries(String pkg, android.content.pm.ApplicationInfo ai) {
        try {
            java.io.File dir = new java.io.File(BlackBoxCore.getContext().getFilesDir(), "guest_shared_libs");
            java.io.File list = new java.io.File(dir, pkg);
            list.delete();
            if (ai == null || ai.sharedLibraryFiles == null) return;
            StringBuilder sb = new StringBuilder();
            for (String s : ai.sharedLibraryFiles) {
                if (s == null || !s.startsWith("/data/app/") || !s.endsWith(".apk")) continue; // system jars are already handled
                sb.append(s).append('\n');
                try {
                    top.niunaijun.blackbox.utils.NativeUtils.copyNativeLib(new java.io.File(s),
                        top.niunaijun.blackbox.core.env.BEnvironment.getAppLibDir(pkg));
                } catch (Throwable t) {
                    Diag.event(Diag.COMPAT, "shared library native code not copied for " + pkg + ": " + t);
                }
            }
            if (sb.length() > 0) {
                dir.mkdirs();
                java.io.FileWriter w = new java.io.FileWriter(list);
                try { w.write(sb.toString()); } finally { w.close(); }
                Diag.event(Diag.COMPAT, "shared libraries linked for " + pkg + ": " + sb.toString().trim().replace('\n', ' '));
            }
        } catch (Throwable t) {
            Diag.event(Diag.COMPAT, "shared library setup failed for " + pkg + ": " + t);
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
            boolean ok = BlackBoxCore.get().launchApk(pkg, userId);
            for (int tries = 0; !ok && tries < 2; tries++) {
                Diag.event(Diag.LAUNCH, "launch refused for " + pkg + " user " + userId + "; engine state: " + launchProbe(pkg, userId));
                retryPause("launch", pkg, userId);
                ok = BlackBoxCore.get().launchApk(pkg, userId);
            }
            if (!ok) Diag.event(Diag.LAUNCH, "launch still refused after retries; engine state: " + launchProbe(pkg, userId));
            Diag.event(Diag.LAUNCH, (ok ? "launch requested " : "launch refused by the engine (no launch intent) ") + pkg + " user " + userId + " " + Diag.memoryLine());
            return ok;
        } catch (Throwable t) {
            Log.e(TAG, "launch", t);
            Diag.event(Diag.LAUNCH, "launch exception for " + pkg + " user " + userId + ": " + t);
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
            wipeData(pkg, userId);
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
