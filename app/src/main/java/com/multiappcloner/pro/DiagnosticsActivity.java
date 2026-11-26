package com.multiappcloner.pro;

import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

public class DiagnosticsActivity extends Screen {
    private int visibleApps = -1;

    @Override protected String screenTitle() { return "Diagnostics"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void onReady() { load(); }

    private void load() {
        visibleApps = -1;
        exec.execute(new Runnable() {
            public void run() {
                final int n = AppLoader.load(getApplicationContext()).size();
                ui(new Runnable() { public void run() { visibleApps = n; rebuild(); } });
            }
        });
    }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout c = Ui.card(this);
        String ver = "unknown";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        c.addView(Ui.row(this, "App version", ver));
        c.addView(Ui.row(this, "Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"));
        c.addView(Ui.row(this, "Database schema", "v" + DatabaseHelper.DB_VERSION));
        c.addView(Ui.row(this, "Visible launchable apps", visibleApps < 0 ? "Counting..." : String.valueOf(visibleApps)));
        c.addView(Ui.row(this, "Clone signing key", "Android Keystore (RSA 2048)"));
        c.addView(Ui.row(this, "Secure storage", SecureStorage.isSupported() ? "Available (Keystore)" : "Not available (Android below 6.0)"));
        c.addView(Ui.row(this, "Internet", NetUtil.isOnline(this) ? NetUtil.connectionType(this) : "Not connected"));
        c.addView(Ui.row(this, "VPN interface", NetUtil.isVpnActive() ? "Detected" : "Not detected"));
        c.addView(Ui.row(this, "Real APK cloning", CloneInstaller.canInstall(this) ? "Available" : "Needs \"Install unknown apps\" permission"));
        c.addView(Ui.row(this, "Virtual cloning", VirtualEngine.isAvailable() ? "Embedded engine loaded" : "Unavailable"));
        c.addView(Ui.row(this, "VPN tunnel", "Not implemented (reference profiles only)"));
        Ui.add(body, c);
        if (visibleApps == 0) {
            Ui.add(body, Ui.text(this, "No apps are visible. Android package-visibility rules may be limiting this app. On Android 11+ with a higher target SDK, the QUERY_ALL_PACKAGES permission is needed.", 12, Ui.ORANGE, false));
        }
        Ui.add(body, Ui.button(this, "Copy engine log (for support)", false, new View.OnClickListener() {
            public void onClick(View v) { EngineLog.copy(DiagnosticsActivity.this); }
        }));
        Ui.add(body, Ui.button(this, "Refresh", false, new View.OnClickListener() {
            public void onClick(View v) { rebuild(); load(); }
        }));
    }
}
