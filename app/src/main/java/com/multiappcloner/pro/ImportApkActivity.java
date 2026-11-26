package com.multiappcloner.pro;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Installs APK / XAPK / APKS / APKM files, picked or downloaded, into the virtual space. Nothing touches Android's own app list. */
public class ImportApkActivity extends Screen {
    private static final int REQ_PICK = 2001;
    private TextView status;
    private EditText link;
    private volatile boolean busy;
    private Downloader downloader;

    @Override protected String screenTitle() { return "Install apps in virtual space"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Install an app file straight into Multi App Cloner Pro. It runs here with its own private data and is not installed in Android.", 13, Ui.TEXT, false));
        c.addView(Ui.text(this, "Supported: .apk and bundles (.xapk, .apks, .apkm) including their native libraries and .obb game data. "
            + "Install only apps you trust and have the right to use.", 12, Ui.ORANGE, false));
        Ui.add(body, c);

        Ui.add(body, Ui.section(this, "From your phone or SD card"));
        Ui.add(body, Ui.button(this, "Choose file", true, new View.OnClickListener() {
            public void onClick(View v) {
                if (busy) return;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_PICK);
            }
        }));

        Ui.add(body, Ui.section(this, "From a download link"));
        link = Ui.input(this, "https://example.com/app.apk", "", false);
        Ui.add(body, link);
        Ui.add(body, Ui.button(this, "Download and install", true, new View.OnClickListener() {
            public void onClick(View v) { startDownload(); }
        }));
        Ui.add(body, Ui.button(this, "Cancel download", false, new View.OnClickListener() {
            public void onClick(View v) { if (downloader != null) downloader.cancel(); }
        }));
        status = Ui.text(this, "", 13, Ui.TEXT2, false);
        Ui.add(body, status);
    }

    private void setStatus(final String s) {
        ui(new Runnable() { public void run() { if (status != null) status.setText(s); } });
    }

    private void finished(final String msg) {
        ui(new Runnable() {
            public void run() {
                busy = false;
                if (status != null) status.setText(msg);
                Toast.makeText(ImportApkActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startDownload() {
        if (busy) return;
        final String url = link.getText().toString().trim();
        if (url.length() == 0) { status.setText("Paste a download link first."); return; }
        busy = true;
        final android.content.Context app = getApplicationContext();
        status.setText("Starting download...");
        downloader = new Downloader();
        final Downloader d = downloader;
        exec.execute(new Runnable() {
            public void run() {
                File in = new File(new File(app.getCacheDir(), "import"), "download.bin");
                try {
                    d.download(url, in, new Downloader.Progress() {
                        public void onProgress(long done, long total) {
                            setStatus("Downloading: " + mb(done) + (total > 0 ? " / " + mb(total) : ""));
                        }
                    });
                } catch (Throwable t) {
                    in.delete();
                    finished("Download failed: " + t.getMessage());
                    return;
                }
                setStatus("Installing...");
                finished(importFile(app, in));
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null || busy) return;
        final Uri uri = data.getData();
        final android.content.Context app = getApplicationContext();
        busy = true;
        status.setText("Reading the file...");
        exec.execute(new Runnable() {
            public void run() {
                File in = new File(new File(app.getCacheDir(), "import"), "picked.bin");
                try {
                    in.getParentFile().mkdirs();
                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) { finished("Could not open that file."); return; }
                    OutputStream os = new FileOutputStream(in);
                    try {
                        byte[] buf = new byte[64 * 1024];
                        long total = 0;
                        int r;
                        while ((r = is.read(buf)) > 0) {
                            total += r;
                            if (total > Downloader.MAX_BYTES) { finished("That file is larger than 3 GB."); in.delete(); return; }
                            os.write(buf, 0, r);
                        }
                    } finally {
                        is.close();
                        os.close();
                    }
                } catch (Throwable t) {
                    in.delete();
                    finished("Could not read the file: " + t.getMessage());
                    return;
                }
                setStatus("Installing...");
                finished(importFile(app, in));
            }
        });
    }

    private static String mb(long b) { return (b / (1024 * 1024)) + " MB"; }

    /** Blocking. Returns a message for the user. Deletes the input and all temporary files. */
    private String importFile(android.content.Context app, File input) {
        File work = new File(app.getCacheDir(), "import/work");
        PackageImporter.Prepared p = null;
        try {
            p = PackageImporter.prepare(app, input, work);
            if (p.packageName.equals(app.getPackageName())) return "This app cannot clone itself.";

            CloneRepository repo = new CloneRepository(app);
            CloneProfile cp = new CloneProfile();
            cp.appName = p.label;
            cp.packageName = p.packageName;
            cp.clonePackage = p.packageName;
            cp.method = CloneRepository.METHOD_VIRTUAL;
            cp.status = CloneRepository.STATUS_INSTALLING;
            cp.virtualUser = repo.nextVirtualUser();
            cp.createdAt = System.currentTimeMillis();
            String name = p.label.length() > 12 ? p.label.substring(0, 12) : p.label;
            long id = CloneRepository.DUPLICATE;
            for (int n = 1; n < 50 && id == CloneRepository.DUPLICATE; n++) {
                cp.cloneName = name + " - File " + n;
                id = repo.insert(cp);
            }
            if (id < 0) return "Could not save the clone profile.";

            ApkCloningEngine.Result r = PackageImporter.install(app, p, cp.virtualUser);
            if (!r.success) {
                repo.delete(id);
                return r.message;
            }
            repo.setState(id, CloneRepository.STATUS_ACTIVE, CloneRepository.METHOD_VIRTUAL, p.packageName);
            new LogRepository(app).add(id, "Imported from " + (p.bundle ? "a bundle" : "an APK") + " into virtual user " + cp.virtualUser);
            return cp.cloneName + " is ready. Open it from Clone Manager. " + r.message;
        } catch (Throwable t) {
            return "Install failed: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        } finally {
            PackageImporter.deleteTemp(p, input);
            File[] left = work.listFiles();
            if (left != null) for (File f : left) f.delete();
            work.delete();
        }
    }
}
