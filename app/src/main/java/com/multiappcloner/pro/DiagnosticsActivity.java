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
            public void onClick(View v) { copyLog(); }
        }));
        Ui.add(body, Ui.button(this, "Refresh", false, new View.OnClickListener() {
            public void onClick(View v) { rebuild(); load(); }
        }));
    }

    /** Reads this app's own log (the virtual engine and every clone run under this app's uid) and copies it. */
    private void copyLog() {
        exec.execute(new Runnable() {
            public void run() {
                final StringBuilder sb = new StringBuilder();
                sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append("), ")
                    .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", abi ").append(Build.SUPPORTED_ABIS[0]).append('\n');
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "-t", "4000"});
                    java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                    java.util.LinkedList<String> keep = new java.util.LinkedList<String>();
                    String line;
                    while ((line = r.readLine()) != null) {
                        String l = line;
                        boolean interesting = l.contains(" E ") || l.contains(" W ") || l.contains("FATAL") || l.contains("AndroidRuntime")
                            || l.contains("BlackBox") || l.contains("VirtualEngine") || l.contains("CloneApp") || l.contains("Slog")
                            || l.contains("Exception") || l.contains("at top.niunaijun");
                        if (interesting) { keep.add(l); if (keep.size() > 350) keep.removeFirst(); }
                    }
                    for (String k : keep) sb.append(k).append('\n');
                } catch (Throwable t) {
                    sb.append("Could not read log: ").append(t).append('\n');
                }
                String out = sb.toString();
                if (out.length() > 90000) out = out.substring(out.length() - 90000);
                final String text = out;
                ui(new Runnable() {
                    public void run() {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("engine log", text));
                        Toast.makeText(DiagnosticsActivity.this, "Log copied. Paste it into the chat.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
