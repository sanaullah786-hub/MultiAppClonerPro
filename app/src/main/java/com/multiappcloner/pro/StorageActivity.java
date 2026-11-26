package com.multiappcloner.pro;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import top.niunaijun.blackbox.core.env.BEnvironment;

/**
 * Where the files that virtual apps save to "shared storage" live (downloads, media, game data, .obb).
 * Everything stays inside this app's own folders, either on internal memory or on the SD card.
 * App code and private app data always stay on internal memory (Android cannot run them from an SD card).
 */
public class StorageActivity extends Screen {
    @Override protected String screenTitle() { return "Storage"; }
    @Override protected boolean rebuildOnResume() { return true; }

    private File sdDir() {
        try {
            File[] dirs = getExternalFilesDirs("blackbox");
            for (int i = 1; dirs != null && i < dirs.length; i++) {
                if (dirs[i] != null && Environment.isExternalStorageRemovable(dirs[i])) {
                    dirs[i].mkdirs();
                    if (dirs[i].canWrite()) return dirs[i];
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static String free(File f) {
        try {
            StatFs s = new StatFs(f.getAbsolutePath());
            return (s.getAvailableBytes() / (1024 * 1024)) + " MB free";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private boolean onSd() { return new File(getFilesDir(), BEnvironment.SD_MARKER).exists(); }

    @Override
    protected void build(LinearLayout body) {
        final File sd = sdDir();
        final File internal = getExternalFilesDir("blackbox");
        final boolean usingSd = onSd() && sd != null;

        LinearLayout c = Ui.card(this);
        c.addView(Ui.row(this, "Guest files location", usingSd ? "SD card" : "Internal memory"));
        if (internal != null) c.addView(Ui.row(this, "Internal memory", free(internal)));
        c.addView(Ui.row(this, "SD card", sd == null ? "not found" : free(sd)));
        c.addView(Ui.text(this, "All data of your clones stays inside Multi App Cloner Pro. This switch moves only the bulky shared-storage files "
            + "(game downloads, media, .obb). App code and private app data always stay on internal memory.", 12, Ui.TEXT2, false));
        Ui.add(body, c);

        if (sd == null) {
            Ui.add(body, Ui.text(this, "No removable SD card was found. Insert one and reopen this screen.", 13, Ui.ORANGE, false));
        } else if (!usingSd) {
            Ui.add(body, Ui.button(this, "Move guest files to the SD card", true, new View.OnClickListener() {
                public void onClick(View v) { move(internal, sd, true); }
            }));
        }
        if (usingSd && internal != null) {
            Ui.add(body, Ui.button(this, "Move guest files back to internal memory", false, new View.OnClickListener() {
                public void onClick(View v) { move(sd, internal, false); }
            }));
        }
        Ui.add(body, Ui.text(this, "Android runs apps from RAM like any other app: this phone cannot use storage as extra RAM without root. "
            + "Close clones you are not using to free memory.", 11, Ui.TEXT2, false));
    }

    private void move(final File from, final File to, final boolean toSd) {
        confirm("Move guest files?", "Close all clones first. The files are copied, the app restarts, and large games can take several minutes.", "Move", new Runnable() {
            public void run() {
                final Context app = getApplicationContext();
                Toast.makeText(app, "Moving files... keep the app open.", Toast.LENGTH_LONG).show();
                exec.execute(new Runnable() {
                    public void run() {
                        String err = null;
                        try {
                            copyTree(from, to);
                            File marker = new File(app.getFilesDir(), BEnvironment.SD_MARKER);
                            if (toSd) {
                                if (!marker.exists() && !marker.createNewFile()) throw new IOException("Could not save the setting.");
                            } else if (marker.exists() && !marker.delete()) {
                                throw new IOException("Could not save the setting.");
                            }
                            deleteTree(from);
                        } catch (Throwable t) {
                            err = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                        }
                        final String e = err;
                        ui(new Runnable() {
                            public void run() {
                                if (e != null) {
                                    Toast.makeText(app, "Move failed: " + e + ". Nothing was deleted.", Toast.LENGTH_LONG).show();
                                    rebuild();
                                    return;
                                }
                                confirm("Restart needed", "Files moved. The app now closes; open it again to use the new location.", "Close app", new Runnable() {
                                    public void run() { restartProcesses(app); }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private static void restartProcesses(Context app) {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            am.killBackgroundProcesses(app.getPackageName());
        } catch (Throwable ignored) { }
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private static void copyTree(File src, File dst) throws IOException {
        if (src == null || !src.exists()) return;
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("Cannot create " + dst);
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copyTree(k, new File(dst, k.getName()));
        } else {
            InputStream in = new FileInputStream(src);
            OutputStream os = new FileOutputStream(dst);
            try {
                byte[] b = new byte[128 * 1024];
                int r;
                while ((r = in.read(b)) > 0) os.write(b, 0, r);
            } finally {
                in.close();
                os.close();
            }
        }
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
