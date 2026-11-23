package com.multiappcloner.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class CreateCloneActivity extends Activity {
    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_LABEL = "label";

    private CloneRepository repo;
    private EditText name;
    private TextView counter, error;
    private String pkg, label;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.applyDarkBars(this);
        setContentView(R.layout.activity_create_clone);
        repo = new CloneRepository(this);

        pkg = getIntent().getStringExtra(EXTRA_PKG);
        label = getIntent().getStringExtra(EXTRA_LABEL);
        if (pkg == null || label == null) {
            Toast.makeText(this, "No app selected.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.appName)).setText(label);
        ((TextView) findViewById(R.id.appPkg)).setText(pkg);
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            ((ImageView) findViewById(R.id.appIcon)).setImageDrawable(d);
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, "This app is no longer installed.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        name = (EditText) findViewById(R.id.cloneName);
        counter = (TextView) findViewById(R.id.counter);
        error = (TextView) findViewById(R.id.error);

        String def = label + " - Clone " + (repo.countForPackage(pkg) + 1);
        if (def.length() > CloneRepository.MAX_NAME) def = def.substring(0, CloneRepository.MAX_NAME);
        name.setText(def);
        name.setSelection(def.length());
        updateCounter();
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            public void onTextChanged(CharSequence s, int a, int c, int d) { updateCounter(); error.setVisibility(View.GONE); }
            public void afterTextChanged(Editable e) { }
        });

        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnSave).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });
    }

    private void updateCounter() {
        counter.setText(name.getText().length() + "/" + CloneRepository.MAX_NAME);
    }

    private void showError(String m) {
        error.setText(m);
        error.setVisibility(View.VISIBLE);
    }

    private void save() {
        final String n = name.getText().toString().trim();
        String err = CloneRepository.validateName(n);
        if (err != null) { showError(err); return; }
        if (!CloneInstaller.canInstall(this)) {
            new AlertDialog.Builder(this).setTitle("Allow installs")
                .setMessage("Android needs your permission before this app can install the clone. On the next screen switch on \"Allow from this source\", then come back and tap Create again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { startActivity(CloneInstaller.permissionIntent(CreateCloneActivity.this)); }
                }).show();
            return;
        }

        final CloneProfile p = new CloneProfile();
        p.appName = label;
        p.packageName = pkg;
        p.cloneName = n;
        p.method = CloneRepository.METHOD_REAL;
        p.status = CloneRepository.STATUS_INSTALLING;
        p.createdAt = System.currentTimeMillis();
        long dn = AppPrefs.defaultNetwork(this);
        if (dn > 0 && new NetworkRepository(this).get(dn) != null) p.networkProfileId = dn;

        final long id = repo.insert(p);
        if (id == CloneRepository.DUPLICATE) {
            showError("A clone with this name already exists for " + label + ".");
            return;
        } else if (id < 0) {
            showError("Could not save the clone profile (storage error). Nothing was created.");
            return;
        }
        final String clonePkg = ApkCloningEngine.clonePackageFor(pkg, id);
        repo.setState(id, CloneRepository.STATUS_INSTALLING, CloneRepository.METHOD_REAL, clonePkg);
        final android.content.Context app = getApplicationContext();
        final View btn = findViewById(R.id.btnSave);
        btn.setEnabled(false);
        error.setText("Building and signing the clone. This can take a few seconds for large apps...");
        error.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                final ApkCloningEngine.Result res = ApkCloningEngine.cloneReal(app, id, pkg, n);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!res.success) {
                            new CloneRepository(app).delete(id); // nothing was installed: keep the list clean
                            if (!isFinishing()) {
                                btn.setEnabled(true);
                                showError(res.message);
                            }
                            return;
                        }
                        new LogRepository(app).add(id, "Clone APK built and signed, sent to the system installer as " + clonePkg);
                        Toast.makeText(app, "Confirm the install on the system dialog.", Toast.LENGTH_LONG).show();
                        if (!isFinishing()) {
                            Intent i = new Intent(CreateCloneActivity.this, CloneDetailsActivity.class);
                            i.putExtra(CloneDetailsActivity.EXTRA_ID, id);
                            startActivity(i);
                            finish();
                        }
                    }
                });
            }
        }, "clone-build").start();
    }
}
