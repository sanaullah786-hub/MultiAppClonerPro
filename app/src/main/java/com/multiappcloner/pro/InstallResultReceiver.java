package com.multiappcloner.pro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Receives the PackageInstaller outcome and records it on the clone profile. */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        long id = intent.getLongExtra(CloneInstaller.EXTRA_CLONE_ID, -1);
        String pkg = intent.getStringExtra(CloneInstaller.EXTRA_CLONE_PKG);
        CloneRepository repo = new CloneRepository(c);
        LogRepository log = new LogRepository(c);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    c.startActivity(confirm);
                } catch (Exception e) {
                    repo.setState(id, CloneRepository.STATUS_FAILED, CloneRepository.METHOD_REAL, pkg);
                    log.add(id, "Could not open the system install dialog: " + e.getMessage());
                }
            }
            return;
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            repo.setState(id, CloneRepository.STATUS_ACTIVE, CloneRepository.METHOD_REAL, pkg);
            log.add(id, "Clone installed as " + pkg);
        } else {
            repo.setState(id, CloneRepository.STATUS_FAILED, CloneRepository.METHOD_REAL, pkg);
            log.add(id, "Install failed: " + describe(status, msg));
            Diag.event(Diag.INSTALL, "PackageInstaller status " + status + " for " + pkg + ": " + describe(status, msg));
        }
    }

    static String describe(int status, String msg) {
        String m = msg == null ? "" : " (" + msg + ")";
        switch (status) {
            case PackageInstaller.STATUS_FAILURE_ABORTED: return "cancelled on the system dialog" + m;
            case PackageInstaller.STATUS_FAILURE_BLOCKED: return "blocked by device policy or Play Protect" + m;
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                return "conflicts with an installed package, usually an older clone signed with a different key. Uninstall that clone first" + m;
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: return "not compatible with this device" + m;
            case PackageInstaller.STATUS_FAILURE_INVALID: return "the rewritten APK was rejected as invalid" + m;
            case PackageInstaller.STATUS_FAILURE_STORAGE: return "not enough storage" + m;
            case 8: return "the installer timed out" + m;
            default: return "unknown error (PackageInstaller status " + status + ")" + m;
        }
    }
}
