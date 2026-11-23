package com.multiappcloner.pro;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Hands a signed APK to the system PackageInstaller. The user always confirms on the system dialog. */
public final class CloneInstaller {
    public static final String ACTION_RESULT = "com.multiappcloner.pro.INSTALL_RESULT";
    public static final String EXTRA_CLONE_ID = "clone_id";
    public static final String EXTRA_CLONE_PKG = "clone_pkg";

    public static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    /** Opens the system screen where the user allows this app to install apps. */
    public static Intent permissionIntent(Context c) {
        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** Streams the APK into an install session and commits it. Returns the session id. */
    public static int install(Context c, File apk, String clonePkg, long cloneId) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(clonePkg);
        int id = pi.createSession(params);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            OutputStream os = s.openWrite("base.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
                s.fsync(os);
            } finally {
                in.close();
                os.close();
            }
            Intent i = new Intent(c, InstallResultReceiver.class);
            i.setAction(ACTION_RESULT);
            i.putExtra(EXTRA_CLONE_ID, cloneId);
            i.putExtra(EXTRA_CLONE_PKG, clonePkg);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE; // system adds status extras
            PendingIntent pending = PendingIntent.getBroadcast(c, (int) cloneId, i, flags);
            s.commit(pending.getIntentSender());
        } catch (IOException e) {
            s.abandon();
            throw e;
        } catch (RuntimeException e) {
            s.abandon();
            throw e;
        } finally {
            s.close();
        }
        return id;
    }

    public static Intent uninstallIntent(String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }
}
