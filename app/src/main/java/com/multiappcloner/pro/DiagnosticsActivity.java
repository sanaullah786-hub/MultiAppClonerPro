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
        LinearLayout d = Ui.card(this);
        d.addView(Ui.row(this, "Memory", Diag.memoryLine()));
        d.addView(Ui.row(this, "Device CPU", java.util.Arrays.toString(Build.SUPPORTED_ABIS)));
        if (Build.VERSION.SDK_INT >= 30)
            d.addView(Ui.row(this, "All files access", android.os.Environment.isExternalStorageManager() ? "Allowed" : "Not allowed (games/browsers may fail)"));
        d.addView(Ui.row(this, "Last crash", Diag.hasUnseenCrash(this) ? "Recorded (not yet reviewed)" : "None new"));
        Ui.add(body, d);
        Ui.add(body, Ui.text(this, "Reports remove emails, phone numbers, passwords and tokens. Nothing is uploaded; you copy the report and paste it yourself.", 12, Ui.ORANGE, false));
        Ui.add(body, Ui.button(this, "Copy diagnostics report", true, new View.OnClickListener() {
            public void onClick(View v) { Diag.markCrashSeen(DiagnosticsActivity.this); Diag.copyReport(DiagnosticsActivity.this); }
        }));
        Ui.add(body, Ui.button(this, "Clone limit: " + CloneBackup.cloneLimit(this) + " running at once (change)", false, new View.OnClickListener() {
            public void onClick(View v) {
                Sheet.input(DiagnosticsActivity.this, "Clone processes at once (1-12)", String.valueOf(CloneBackup.cloneLimit(DiagnosticsActivity.this)), "Save", new Sheet.TextResult() {
                    public void onText(String t) {
                        try { CloneBackup.setCloneLimit(DiagnosticsActivity.this, Integer.parseInt(t.trim())); rebuild(); }
                        catch (NumberFormatException e) { Toast.makeText(DiagnosticsActivity.this, "Enter a number.", Toast.LENGTH_SHORT).show(); }
                    }
                });
            }
        }));
        Ui.add(body, Ui.text(this, "Default comes from this phone's RAM (" + CloneBackup.totalRamMb(this) + " MB). It is an estimate, not a measured value.", 12, Ui.TEXT2, false));
        Ui.add(body, Ui.button(this, "Clear diagnostics history", false, new View.OnClickListener() {
            public void onClick(View v) {
                Diag.clear(DiagnosticsActivity.this);
                Toast.makeText(DiagnosticsActivity.this, "Diagnostics history cleared.", Toast.LENGTH_SHORT).show();
                rebuild();
            }
        }));
        Ui.add(body, Ui.button(this, "Copy raw engine log", false, new View.OnClickListener() {
            public void onClick(View v) { EngineLog.copy(DiagnosticsActivity.this); }
        }));
        Ui.add(body, Ui.button(this, "Stop all running clones", false, new View.OnClickListener() {
            public void onClick(View v) {
                CloneState.stopAll(DiagnosticsActivity.this);
                Toast.makeText(DiagnosticsActivity.this, "All clones stopped.", Toast.LENGTH_SHORT).show();
            }
        }));
        Ui.add(body, Ui.button(this, "Refresh", false, new View.OnClickListener() {
            public void onClick(View v) { rebuild(); load(); }
        }));
    }
}
