package com.multiappcloner.pro;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.utils.NativeUtils;

/**
 * Understands what a downloaded/picked file is and installs it into the virtual space:
 * a plain .apk, or a bundle (.xapk / .apks / .apkm: a zip of base + split APKs and optional .obb data).
 * The base APK is installed unmodified (so its signature stays valid); native libraries from split APKs
 * and OBB files are placed next to it afterwards.
 */
public final class PackageImporter {
    private static final String TAG = "PackageImporter";

    public static final class Prepared {
        public File base;
        public final List<File> splits = new ArrayList<File>();
        public final List<File> obbs = new ArrayList<File>();
        public String packageName;
        public String label;
        public boolean bundle;
        public boolean usesGms;
    }

    public static Prepared prepare(Context c, File input, File workDir) throws IOException {
        workDir.mkdirs();
        ZipFile zf;
        try {
            zf = new ZipFile(input);
        } catch (IOException e) {
            throw new IOException("This file is not an APK or an APK bundle (.xapk / .apks / .apkm).");
        }
        try {
            Prepared p = new Prepared();
            if (zf.getEntry("AndroidManifest.xml") != null) {
                p.base = input;
            } else {
                p.bundle = true;
                List<File> apks = new ArrayList<File>();
                Enumeration<? extends ZipEntry> en = zf.entries();
                int n = 0;
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String name = e.getName();
                    if (e.isDirectory() || name.contains("..")) continue;
                    String lower = name.toLowerCase(java.util.Locale.ROOT);
                    if (lower.endsWith(".apk")) {
                        File f = new File(workDir, "part" + (n++) + "_" + new File(name).getName());
                        extract(zf, e, f);
                        apks.add(f);
                    } else if (lower.endsWith(".obb")) {
                        File f = new File(workDir, "obb_" + new File(name).getName());
                        extract(zf, e, f);
                        p.obbs.add(f);
                    }
                }
                if (apks.isEmpty()) throw new IOException("No APK found inside this bundle.");
                p.base = pickBase(c, apks);
                for (File f : apks) if (!f.equals(p.base)) p.splits.add(f);
            }
            PackageManager pm = c.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(p.base.getAbsolutePath(), PackageManager.GET_META_DATA);
            if (pi == null || pi.packageName == null) throw new IOException("The main APK could not be read.");
            ApplicationInfo ai = pi.applicationInfo;
            ai.sourceDir = p.base.getAbsolutePath();
            ai.publicSourceDir = p.base.getAbsolutePath();
            CharSequence lab = ai.loadLabel(pm);
            p.usesGms = VirtualEngine.usesGoogleServices(ai);
            p.packageName = pi.packageName;
            p.label = lab == null || lab.length() == 0 ? pi.packageName : lab.toString();
            return p;
        } finally {
            zf.close();
        }
    }

    /** The base APK is the one that is a complete app (has a launcher label / is not a config split). */
    private static File pickBase(Context c, List<File> apks) throws IOException {
        File best = null;
        for (File f : apks) {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (n.endsWith("_base.apk") || n.equals("base.apk")) return f;
        }
        for (File f : apks) {
            String n = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (n.contains("config.") || n.contains("split_config") || n.contains("split.")) continue;
            if (best == null || f.length() > best.length()) best = f;
        }
        if (best == null) {
            for (File f : apks) if (best == null || f.length() > best.length()) best = f;
        }
        return best;
    }

    public static ApkCloningEngine.Result install(Context c, Prepared p, int userId) {
        ApkCloningEngine.Result r = VirtualEngine.installApk(c, p.base, userId);
        if (!r.success) return r;
        String warn = "";
        for (File s : p.splits) {
            try {
                NativeUtils.copyNativeLib(s, BEnvironment.getAppLibDir(p.packageName));
            } catch (Throwable t) {
                Log.w(TAG, "native libs from " + s.getName() + ": " + t);
            }
        }
        if (!p.obbs.isEmpty()) {
            try {
                File dir = BEnvironment.getExternalObbDir(p.packageName, userId);
                dir.mkdirs();
                for (File o : p.obbs) {
                    String n = o.getName().startsWith("obb_") ? o.getName().substring(4) : o.getName();
                    File dst = new File(dir, n);
                    if (!o.renameTo(dst)) copy(o, dst);
                }
            } catch (Throwable t) {
                warn = " Game data (.obb) could not be copied.";
                Log.w(TAG, "obb: " + t);
            }
        }
        if (p.usesGms && VirtualEngine.isGmsSupported() && !VirtualEngine.isGmsInstalled(userId)) {
            ApkCloningEngine.Result g = VirtualEngine.installGms(userId);
            warn += g.success ? " Google services were added for sign-in." : " Google services could not be added.";
        }
        return new ApkCloningEngine.Result(true, "Installed in the virtual space." + warn);
    }

    public static void deleteTemp(Prepared p, File input) {
        if (p == null) return;
        if (p.bundle) {
            if (p.base != null) p.base.delete();
            for (File f : p.splits) f.delete();
            for (File f : p.obbs) f.delete();
        }
        if (input != null) input.delete();
    }

    private static void extract(ZipFile zf, ZipEntry e, File dst) throws IOException {
        InputStream in = zf.getInputStream(e);
        try {
            copy(in, dst);
        } finally {
            in.close();
        }
    }

    private static void copy(File src, File dst) throws IOException {
        InputStream in = new java.io.FileInputStream(src);
        try { copy(in, dst); } finally { in.close(); }
    }

    private static void copy(InputStream in, File dst) throws IOException {
        OutputStream os = new FileOutputStream(dst);
        try {
            byte[] b = new byte[64 * 1024];
            int r;
            while ((r = in.read(b)) > 0) os.write(b, 0, r);
        } finally {
            os.close();
        }
    }
}
