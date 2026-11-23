package com.multiappcloner.pro;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AppDetailsActivity extends Screen {
    public static final String EXTRA_PKG = "pkg", EXTRA_LABEL = "label";
    private String pkg, label;
    private HybridCloneManager.Analysis analysis;
    private boolean analyzing = true;
    private String integrity;

    @Override protected String screenTitle() { return "App Compatibility"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void onReady() {
        pkg = getIntent().getStringExtra(EXTRA_PKG);
        label = getIntent().getStringExtra(EXTRA_LABEL);
        if (pkg == null) { finish(); return; }
        exec.execute(new Runnable() {
            public void run() {
                final HybridCloneManager.Analysis a = HybridCloneManager.analyze(getApplicationContext(), pkg);
                ui(new Runnable() {
                    public void run() { analysis = a; analyzing = false; rebuild(); }
                });
            }
        });
    }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout head = Ui.card(this);
        ImageView icon = new ImageView(this);
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            icon.setImageDrawable(d);
        } catch (Exception ignored) { }
        head.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        head.addView(Ui.text(this, label == null ? pkg : label, 18, Ui.TEXT, true));
        head.addView(Ui.text(this, pkg, 12, Ui.TEXT2, false));
        Ui.add(body, head);

        if (analyzing) {
            Ui.add(body, Ui.text(this, "Analyzing...", 14, Ui.TEXT2, false));
            return;
        }
        final ApkInspector.Report r = analysis.report;
        if (r.error != null) {
            Ui.add(body, Ui.text(this, r.error, 14, Ui.RED, false));
            return;
        }

        if (r.system) {
            LinearLayout n = Ui.card(this);
            n.addView(Ui.badge(this, "UNSUPPORTED APP", Ui.RED));
            n.addView(Ui.text(this, "This is a system app. Android does not allow it to be cloned or reinstalled under a new package name. "
                + "Banking, payment, DRM-protected and device-administration apps are also unsuitable.", 13, Ui.TEXT2, false));
            Ui.add(body, n);
        }

        Ui.add(body, Ui.section(this, "Package details"));
        LinearLayout det = Ui.card(this);
        det.addView(Ui.row(this, "Version", r.versionName + " (" + r.versionCode + ")"));
        det.addView(Ui.row(this, "Target SDK", String.valueOf(r.targetSdk)));
        det.addView(Ui.row(this, "Min SDK", r.minSdk > 0 ? String.valueOf(r.minSdk) : "Not available on this Android version"));
        det.addView(Ui.row(this, "System app", r.system ? "Yes" : "No"));
        det.addView(Ui.row(this, "Signers", r.signerCount > 0 ? String.valueOf(r.signerCount) : "Not readable"));
        det.addView(Ui.row(this, "Signer SHA-256", r.sha256 == null ? "Not readable" : r.sha256));
        det.addView(Ui.row(this, "Split APKs", r.splitApks ? "Yes" : "No"));
        det.addView(Ui.row(this, "Native libraries", r.nativeLibs ? "Yes" : "No"));
        det.addView(Ui.row(this, "shared user id", r.sharedUserId ? "Yes" : "No"));
        det.addView(Ui.row(this, "Activities / services", r.activities + " / " + r.services));
        det.addView(Ui.row(this, "Receivers / providers", r.receivers + " / " + r.providers));
        det.addView(Ui.row(this, "Permissions requested", String.valueOf(r.permissions)));
        if (r.partial) det.addView(Ui.text(this, "Partial inspection: component lists were too large to read.", 11, Ui.ORANGE, false));
        Ui.add(body, det);

        Ui.add(body, Ui.section(this, "Cloning methods"));
        LinearLayout real = Ui.card(this);
        real.addView(Ui.badge(this, analysis.realAvailable ? "APK REPACKAGING: AVAILABLE" : "APK REPACKAGING: UNSUPPORTED",
            analysis.realAvailable ? Ui.GREEN : Ui.RED));
        StringBuilder sb = new StringBuilder();
        for (String s : analysis.realReasons) sb.append("• ").append(s).append('\n');
        for (String s : analysis.realWarnings) sb.append("• ").append(s).append('\n');
        TextView rt = Ui.text(this, sb.toString().trim(), 12, Ui.TEXT2, false);
        rt.setPadding(0, Ui.dp(this, 8), 0, 0);
        real.addView(rt);
        Ui.add(body, real);

        LinearLayout virt = Ui.card(this);
        virt.addView(Ui.badge(this, "VIRTUAL CLONING: NOT INCLUDED", Ui.ORANGE));
        TextView vt = Ui.text(this, analysis.virtualReason, 12, Ui.TEXT2, false);
        vt.setPadding(0, Ui.dp(this, 8), 0, 0);
        virt.addView(vt);
        Ui.add(body, virt);

        Ui.add(body, Ui.text(this, analysis.methods.isEmpty()
            ? "Available methods: none. This result was saved to the compatibility registry."
            : "Available methods: " + analysis.methods, 13, analysis.methods.isEmpty() ? Ui.ORANGE : Ui.GREEN, false));

        Ui.add(body, Ui.button(this, "Verify APK archive", false, new View.OnClickListener() {
            public void onClick(View v) {
                integrity = "Checking...";
                rebuild();
                exec.execute(new Runnable() {
                    public void run() {
                        final ApkInspector.Integrity i = ApkInspector.verifyArchive(getApplicationContext(), pkg);
                        ui(new Runnable() {
                            public void run() { integrity = i.message; rebuild(); }
                        });
                    }
                });
            }
        }));
        if (integrity != null) Ui.add(body, Ui.text(this, integrity, 12, Ui.TEXT2, false));

        if (analysis.realAvailable) {
            Ui.add(body, Ui.button(this, "Create clone", true, new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(AppDetailsActivity.this, CreateCloneActivity.class);
                    i.putExtra(CreateCloneActivity.EXTRA_PKG, pkg);
                    i.putExtra(CreateCloneActivity.EXTRA_LABEL, label == null ? pkg : label);
                    startActivity(i);
                }
            }));
        }
    }
}
