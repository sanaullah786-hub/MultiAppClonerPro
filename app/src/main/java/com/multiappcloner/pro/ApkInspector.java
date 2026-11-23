package com.multiappcloner.pro;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Build;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read-only inspection of an installed app's package metadata. Nothing is modified. */
public class ApkInspector {
    public static class Report {
        public String pkg, label, versionName, sha256, sourceDir, error;
        public int versionCode, minSdk, targetSdk, signerCount;
        public int activities, services, receivers, providers, permissions, customPermissions;
        public boolean system, debuggable, sharedUserId, splitApks, nativeLibs, deviceAdmin, accessibility, vpnService, partial;
    }

    public static class Integrity {
        public boolean ok;
        public int entries;
        public String message;
    }

    @SuppressWarnings("deprecation")
    public static Report inspect(Context c, String pkg) {
        Report r = new Report();
        r.pkg = pkg;
        PackageManager pm = c.getPackageManager();
        int sig = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        int full = PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES | PackageManager.GET_SERVICES
            | PackageManager.GET_PROVIDERS | PackageManager.GET_RECEIVERS | sig;
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(pkg, full);
        } catch (PackageManager.NameNotFoundException e) {
            r.error = "App is not installed.";
            return r;
        } catch (Exception e) {
            r.partial = true;
        }
        if (pi == null) {
            try {
                pi = pm.getPackageInfo(pkg, sig);
            } catch (PackageManager.NameNotFoundException e) {
                r.error = "App is not installed.";
                return r;
            } catch (Exception e) {
                r.error = "Could not read package info: " + e.getMessage();
                return r;
            }
        }
        ApplicationInfo ai = pi.applicationInfo;
        r.label = ai == null ? pkg : String.valueOf(pm.getApplicationLabel(ai));
        r.versionName = pi.versionName;
        r.versionCode = pi.versionCode;
        r.sharedUserId = pi.sharedUserId != null;
        if (ai != null) {
            r.sourceDir = ai.sourceDir;
            r.targetSdk = ai.targetSdkVersion;
            if (Build.VERSION.SDK_INT >= 24) r.minSdk = ai.minSdkVersion;
            r.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            r.debuggable = (ai.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (Build.VERSION.SDK_INT >= 21) r.splitApks = ai.splitSourceDirs != null && ai.splitSourceDirs.length > 0;
            if (ai.nativeLibraryDir != null) {
                String[] l = new File(ai.nativeLibraryDir).list();
                r.nativeLibs = l != null && l.length > 0;
            }
        }
        Signature[] sigs = null;
        if (Build.VERSION.SDK_INT >= 28) {
            if (pi.signingInfo != null) sigs = pi.signingInfo.getApkContentsSigners();
        } else {
            sigs = pi.signatures;
        }
        if (sigs != null && sigs.length > 0) {
            r.signerCount = sigs.length;
            r.sha256 = sha256(sigs[0].toByteArray());
        }
        r.activities = pi.activities == null ? 0 : pi.activities.length;
        r.services = pi.services == null ? 0 : pi.services.length;
        r.receivers = pi.receivers == null ? 0 : pi.receivers.length;
        r.providers = pi.providers == null ? 0 : pi.providers.length;
        r.permissions = pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length;
        r.customPermissions = pi.permissions == null ? 0 : pi.permissions.length;
        if (pi.receivers != null) {
            for (ActivityInfo a : pi.receivers) {
                if ("android.permission.BIND_DEVICE_ADMIN".equals(a.permission)) r.deviceAdmin = true;
            }
        }
        if (pi.services != null) {
            for (ServiceInfo s : pi.services) {
                if ("android.permission.BIND_ACCESSIBILITY_SERVICE".equals(s.permission)) r.accessibility = true;
                if ("android.permission.BIND_VPN_SERVICE".equals(s.permission)) r.vpnService = true;
            }
        }
        return r;
    }

    private static String sha256(byte[] b) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < d.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks that the base APK is a readable ZIP whose entries all decompress.
     * This is NOT a signature or tamper verification.
     */
    public static Integrity verifyArchive(Context c, String pkg) {
        Integrity out = new Integrity();
        ZipFile zf = null;
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            zf = new ZipFile(new File(ai.sourceDir));
            byte[] buf = new byte[8192];
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                InputStream in = zf.getInputStream(e);
                try {
                    while (in.read(buf) != -1) { }
                } finally {
                    in.close();
                }
                out.entries++;
            }
            out.ok = true;
            out.message = "The base APK archive is readable and all " + out.entries
                + " entries decompressed without error. This is not a signature or tamper check.";
        } catch (Exception e) {
            out.ok = false;
            out.message = "Archive check failed: " + e.getMessage();
        } finally {
            if (zf != null) try { zf.close(); } catch (Exception ignored) { }
        }
        return out;
    }
}
