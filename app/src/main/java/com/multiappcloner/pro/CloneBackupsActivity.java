package com.multiappcloner.pro;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.multiappcloner.pro.backup.BackupEngine;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Backs up, verifies, restores and deletes the data of virtual clones. */
public class CloneBackupsActivity extends Screen {
    public static final String EXTRA_ID = "id";
    private CloneRepository repo;
    private volatile boolean busy;

    @Override protected String screenTitle() { return "Clone Data Backups"; }
    @Override protected void onReady() { repo = new CloneRepository(this); }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Saves the logins, settings and files of a virtual clone into one backup file, with a checksum so damage is detected. Restoring replaces the clone's current data.", 13, Ui.TEXT, false));
        c.addView(Ui.text(this, "A backup contains the app's saved sessions. Keep it private. It is stored in this app's own folder and is deleted if you uninstall the cloner.", 12, Ui.ORANGE, false));
        Ui.add(body, c);

        final List<CloneProfile> virt = new ArrayList<CloneProfile>();
        for (CloneProfile p : repo.getAll())
            if (CloneRepository.METHOD_VIRTUAL.equals(p.method) && CloneRepository.STATUS_ACTIVE.equals(p.status)) virt.add(p);

        Ui.add(body, Ui.section(this, "Back up a clone"));
        if (virt.isEmpty()) Ui.add(body, Ui.text(this, "No virtual clones yet.", 13, Ui.TEXT2, false));
        for (final CloneProfile p : virt) {
            Ui.add(body, Ui.button(this, p.cloneName, false, new View.OnClickListener() {
                public void onClick(View v) { runBackup(p); }
            }));
        }

        Ui.add(body, Ui.section(this, "Saved backups"));
        List<File> files = CloneBackup.list(this);
        if (files.isEmpty()) Ui.add(body, Ui.text(this, "No backups yet.", 13, Ui.TEXT2, false));
        for (final File f : files) {
            LinearLayout row = Ui.card(this);
            row.addView(Ui.text(this, f.getName(), 12, Ui.TEXT, true));
            row.addView(Ui.text(this, CloneBackup.human(f.length()) + "  -  " + new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(f.lastModified())), 12, Ui.TEXT2, false));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { openItem(f, virt); }
            });
            Ui.add(body, row);
        }
    }

    private void openItem(final File f, final List<CloneProfile> virt) {
        Sheet.actions(this, null, f.getName(), "Tap an action")
            .item("🔍", "Verify", "Checks every file against its checksum", Ui.CYAN, new Runnable() {
                public void run() { runVerify(f); }
            })
            .item("♻", "Restore", "Replaces a clone's current data", Ui.GREEN, new Runnable() {
                public void run() { chooseTarget(f, virt); }
            })
            .item("🗑", "Delete backup", "Removes only this backup file", Ui.RED, new Runnable() {
                public void run() {
                    confirm("Delete this backup?", f.getName(), "Delete", new Runnable() {
                        public void run() { f.delete(); rebuild(); }
                    });
                }
            }).show();
    }

    private void runBackup(final CloneProfile p) {
        if (busy) { Toast.makeText(this, "Another backup or restore is running.", Toast.LENGTH_SHORT).show(); return; }
        busy = true;
        Toast.makeText(this, "Backing up " + p.cloneName + "...", Toast.LENGTH_SHORT).show();
        exec.execute(new Runnable() {
            public void run() {
                String msg;
                try {
                    File out = CloneBackup.backup(CloneBackupsActivity.this, p, null);
                    BackupEngine.verify(out, null);
                    msg = "Backup saved and verified (" + CloneBackup.human(out.length()) + ").";
                } catch (Throwable t) {
                    msg = "Backup failed: " + t.getMessage();
                }
                busy = false;
                final String m = msg;
                ui(new Runnable() { public void run() { Sheet.message(CloneBackupsActivity.this, "Backup", m); rebuild(); } });
            }
        });
    }

    private void runVerify(final File f) {
        Toast.makeText(this, "Checking...", Toast.LENGTH_SHORT).show();
        exec.execute(new Runnable() {
            public void run() {
                String msg;
                try {
                    BackupEngine.Manifest m = BackupEngine.verify(f, null);
                    msg = "Backup is intact.\nApp: " + m.packageName + (m.appVersion.length() > 0 ? " " + m.appVersion : "")
                        + "\nFiles: " + m.entries.size() + "  Data: " + CloneBackup.human(m.totalBytes())
                        + "\nFormat version: " + m.format;
                } catch (Throwable t) {
                    msg = "This backup is damaged or invalid:\n" + t.getMessage();
                }
                final String m2 = msg;
                ui(new Runnable() { public void run() { Sheet.message(CloneBackupsActivity.this, "Verify", m2); } });
            }
        });
    }

    private void chooseTarget(final File f, List<CloneProfile> virt) {
        final BackupEngine.Manifest m;
        try {
            m = BackupEngine.verify(f, null);
        } catch (Throwable t) {
            Sheet.message(this, "Cannot restore", "This backup is damaged or invalid:\n" + t.getMessage());
            return;
        }
        List<CloneProfile> targets = new ArrayList<CloneProfile>();
        for (CloneProfile p : virt) if (p.packageName.equals(m.packageName)) targets.add(p);
        if (targets.isEmpty()) {
            Sheet.message(this, "No clone to restore into", "This backup is for " + m.packageName + ". Create a virtual clone of that app first, then restore into it.");
            return;
        }
        Sheet s = Sheet.actions(this, null, "Restore into which clone?", "Its current data will be replaced");
        for (final CloneProfile p : targets) {
            s.item("♻", p.cloneName, "Virtual user " + p.virtualUser, Ui.GREEN, new Runnable() {
                public void run() {
                    confirm("Replace data of " + p.cloneName + "?",
                        "Everything this clone has now (logins, files, databases) is replaced by the backup. This cannot be undone. If the restore fails, the current data is put back.",
                        "Replace", new Runnable() { public void run() { runRestore(f, p); } });
                }
            });
        }
        s.show();
    }

    private void runRestore(final File f, final CloneProfile p) {
        if (busy) { Toast.makeText(this, "Another backup or restore is running.", Toast.LENGTH_SHORT).show(); return; }
        busy = true;
        Toast.makeText(this, "Restoring...", Toast.LENGTH_SHORT).show();
        exec.execute(new Runnable() {
            public void run() {
                String msg;
                try {
                    CloneBackup.restore(CloneBackupsActivity.this, f, p, null);
                    msg = "Restored. Open the clone to continue.";
                } catch (Throwable t) {
                    msg = "Restore failed: " + t.getMessage() + "\nThe clone's previous data was kept.";
                }
                busy = false;
                final String m = msg;
                ui(new Runnable() { public void run() { Sheet.message(CloneBackupsActivity.this, "Restore", m); } });
            }
        });
    }
}
