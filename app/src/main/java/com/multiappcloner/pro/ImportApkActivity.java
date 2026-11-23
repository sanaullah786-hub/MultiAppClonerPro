package com.multiappcloner.pro;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Installs an APK file (from storage) straight into the virtual space as a new clone. Nothing is installed into Android. */
public class ImportApkActivity extends Screen {
    private static final int REQ_PICK = 2001;
    private static final long MAX_BYTES = 1500L * 1024 * 1024;
    private TextView status;
    private boolean busy;

    @Override protected String screenTitle() { return "Install APK in virtual space"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout c = Ui.card(this);
        c.addView(Ui.text(this, "Pick an .apk file from your phone. It is installed only inside Multi App Cloner Pro, in its own private data area, "
            + "without touching the apps installed in Android.", 13, Ui.TEXT, false));
        c.addView(Ui.text(this, "Only single .apk files work. Bundles (.xapk, .apks, .apkm) and system apps are not supported. "
            + "Install only apps you trust and have the right to use.", 12, Ui.ORANGE, false));
        Ui.add(body, c);
        Ui.add(body, Ui.button(this, "Choose APK file", true, new View.OnClickListener() {
            public void onClick(View v) {
                if (busy) return;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_PICK);
            }
        }));
        status = Ui.text(this, "", 13, Ui.TEXT2, false);
        Ui.add(body, status);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        final android.content.Context app = getApplicationContext();
        busy = true;
        status.setText("Reading the APK...");
        exec.execute(new Runnable() {
            public void run() {
                final String msg = doImport(app, uri);
                ui(new Runnable() {
                    public void run() {
                        busy = false;
                        status.setText(msg);
                        Toast.makeText(ImportApkActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private String doImport(android.content.Context app, Uri uri) {
        File dir = new File(app.getCacheDir(), "import");
        dir.mkdirs();
        File apk = new File(dir, "import.apk");
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return "Could not open that file.";
            long total = 0;
            OutputStream os = new FileOutputStream(apk);
            try {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    total += r;
                    if (total > MAX_BYTES) return "That file is too large.";
                    os.write(buf, 0, r);
                }
            } finally {
                in.close();
                os.close();
            }
            PackageManager pm = app.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (pi == null || pi.packageName == null) return "This is not a valid single APK. Bundles (.xapk / .apks) are not supported.";
            if (pi.packageName.equals(app.getPackageName())) return "This app cannot clone itself.";
            ApplicationInfo ai = pi.applicationInfo;
            ai.sourceDir = apk.getAbsolutePath();
            ai.publicSourceDir = apk.getAbsolutePath();
            CharSequence lab = ai.loadLabel(pm);
            String label = lab == null || lab.length() == 0 ? pi.packageName : lab.toString();

            CloneRepository repo = new CloneRepository(app);
            CloneProfile p = new CloneProfile();
            p.appName = label;
            p.packageName = pi.packageName;
            p.clonePackage = pi.packageName;
            p.method = CloneRepository.METHOD_VIRTUAL;
            p.status = CloneRepository.STATUS_INSTALLING;
            p.virtualUser = repo.nextVirtualUser();
            p.createdAt = System.currentTimeMillis();
            String name = label.length() > 12 ? label.substring(0, 12) : label;
            long id = CloneRepository.DUPLICATE;
            for (int n = 1; n < 50 && id == CloneRepository.DUPLICATE; n++) {
                p.cloneName = name + " - APK " + n;
                id = repo.insert(p);
            }
            if (id < 0) return "Could not save the clone profile.";
            CloneProfile saved = repo.getById(id);
            if (saved == null) return "Could not save the clone profile.";
            int user = p.virtualUser;
            ApkCloningEngine.Result r = VirtualEngine.installApk(app, apk, user);
            if (!r.success) {
                repo.delete(saved.id);
                return r.message;
            }
            repo.setState(saved.id, CloneRepository.STATUS_ACTIVE, CloneRepository.METHOD_VIRTUAL, pi.packageName);
            new LogRepository(app).add(saved.id, "Imported from an APK file into virtual user " + user);
            return saved.cloneName + " is ready. Open it from Clone Manager.";
        } catch (Throwable t) {
            return "Import failed: " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        } finally {
            apk.delete();
        }
    }
}
