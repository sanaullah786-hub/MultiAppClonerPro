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
