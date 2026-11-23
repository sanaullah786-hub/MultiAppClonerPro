package com.multiappcloner.pro;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BackupActivity extends Screen {
    private static final int REQ_EXPORT = 1001, REQ_IMPORT = 1002;
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    @Override protected String screenTitle() { return "Backup & Restore"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "1. Profile backup: clone profile records (supported)", 13, Ui.TEXT, false));
        c.addView(Ui.text(this, "2. Configuration backup: network profile metadata and IP-check settings (supported). Passwords and credentials are never included.", 13, Ui.TEXT, false));
        c.addView(Ui.text(this, "3. Application-data backup: NOT supported. Other apps' private data is protected by Android and this app cannot access it.", 13, Ui.ORANGE, false));
        Ui.add(body, c);

        if (Build.VERSION.SDK_INT < 19) {
            Ui.add(body, Ui.text(this, "Backup files need Android 4.4 or newer (system file picker).", 13, Ui.RED, false));
            return;
        }
        Ui.add(body, Ui.button(this, "Export backup file", true, new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "multi-app-cloner-backup.json");
                startActivityForResult(i, REQ_EXPORT);
            }
        }));
        Ui.add(body, Ui.button(this, "Restore from backup file", false, new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            }
        }));
        Ui.add(body, Ui.text(this, "Restore adds missing records and skips duplicates. It never overwrites or deletes existing profiles.", 11, Ui.TEXT2, false));
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final Context c = getApplicationContext();
        if (req == REQ_EXPORT) {
            exec.execute(new Runnable() {
                public void run() {
                    String msg;
                    try {
                        String json = BackupManager.export(c);
                        if (json == null) throw new Exception("could not build the backup");
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        if (os == null) throw new Exception("could not open the destination");
                        try { os.write(json.getBytes("UTF-8")); } finally { os.close(); }
                        msg = "Backup saved. Passwords were not included.";
                    } catch (Exception e) {
                        msg = "Backup failed: " + e.getMessage() + ". Nothing was changed.";
                    }
                    final String m = msg;
                    ui(new Runnable() { public void run() { info("Export", m); } });
                }
            });
        } else if (req == REQ_IMPORT) {
            exec.execute(new Runnable() {
                public void run() {
                    String msg;
                    try {
                        InputStream in = getContentResolver().openInputStream(uri);
                        if (in == null) throw new Exception("could not open the file");
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        try {
                            while ((n = in.read(buf)) != -1) {
                                bo.write(buf, 0, n);
                                if (bo.size() > MAX_BYTES) throw new Exception("file is larger than 2 MB");
                            }
                        } finally { in.close(); }
                        BackupManager.Summary s = BackupManager.restore(c, bo.toString("UTF-8"));
                        msg = s.message;
                    } catch (Exception e) {
                        msg = "Restore failed: " + e.getMessage() + ". Nothing was changed.";
                    }
                    final String m = msg;
                    ui(new Runnable() { public void run() { info("Restore", m); } });
                }
            });
        }
    }
}
