package com.multiappcloner.pro;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;

public class DataManagementActivity extends Screen {
    @Override protected String screenTitle() { return "Data Management"; }

    @Override
    protected void build(LinearLayout body) {
        File db = getDatabasePath(DatabaseHelper.DB_NAME);
        LinearLayout c = Ui.card(this);
        c.addView(Ui.row(this, "Clone profiles", String.valueOf(new CloneRepository(this).count())));
        c.addView(Ui.row(this, "Network profiles", String.valueOf(new NetworkRepository(this).count())));
        c.addView(Ui.row(this, "Log entries", String.valueOf(new LogRepository(this).countAll())));
        c.addView(Ui.row(this, "Database size", db.exists() ? (db.length() / 1024) + " KB" : "0 KB"));
        c.addView(Ui.text(this, "This is only the data this app stores itself. Other apps' private data is not accessible and is never touched.", 11, Ui.TEXT2, false));
        Ui.add(body, c);

        Ui.add(body, Ui.button(this, "Backup & Restore", false, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(DataManagementActivity.this, BackupActivity.class)); }
        }));
        Ui.add(body, Ui.button(this, "Clear all log entries", false, new View.OnClickListener() {
            public void onClick(View v) {
                confirm("Clear logs?", "All profile activity logs will be deleted. Profiles are kept.", "Clear", new Runnable() {
                    public void run() { new LogRepository(DataManagementActivity.this).clearAll(); rebuild(); }
                });
            }
        }));
        Ui.add(body, Ui.button(this, "Delete ALL app data", false, new View.OnClickListener() {
            public void onClick(View v) {
                confirm("Delete all app data?", "This permanently deletes every clone profile, network profile, log, saved setting and the encryption key used for proxy passwords. "
                    + "It cannot be undone. Other apps are not affected.", "Delete everything", new Runnable() {
                        public void run() {
                            DatabaseHelper.wipe(DataManagementActivity.this);
                            SecureStorage.deleteKey();
                            AppPrefs.clear(DataManagementActivity.this);
                            Toast.makeText(DataManagementActivity.this, "All app data deleted.", Toast.LENGTH_LONG).show();
                            Intent i = new Intent(DataManagementActivity.this, SplashActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                        }
                    });
            }
        }));
    }
}
