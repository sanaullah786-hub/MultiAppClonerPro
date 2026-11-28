package com.multiappcloner.pro;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedList;

/** Copies this app's own log (engine + every clone run under this uid) to the clipboard for support. */
public final class EngineLog {
    public static void copy(final Activity a) {
        new Thread(new Runnable() {
            public void run() {
                final StringBuilder sb = new StringBuilder();
                sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append("), ")
                    .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", abi ").append(Build.SUPPORTED_ABIS[0]).append('\n');
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "-t", "6000"});
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    LinkedList<String> keep = new LinkedList<String>();
                    String l;
                    while ((l = r.readLine()) != null) {
                        boolean hit = l.contains(" E ") || l.contains(" W ") || l.contains("FATAL") || l.contains("AndroidRuntime")
                            || l.contains("BlackBox") || l.contains("VirtualEngine") || l.contains("CloneApp") || l.contains("Slog")
                            || l.contains("Exception") || l.contains("at top.niunaijun");
                        if (hit) { keep.add(l); if (keep.size() > 400) keep.removeFirst(); }
                    }
                    for (String k : keep) sb.append(k).append('\n');
                } catch (Throwable t) {
                    sb.append("Could not read log: ").append(t).append('\n');
                }
                String out = sb.toString();
                if (out.length() > 90000) out = out.substring(out.length() - 90000);
                final String text = out;
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("engine log", text));
                        Toast.makeText(a, "Log copied. Paste it into the chat.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "log-copy").start();
    }

    /** Explains a failed launch and offers the log. */
    public static void launchFailed(final Activity a, String name) {
        Sheet.actions(a, null, "Could not open " + name, "The clone did not start")
            .item("📋", "Copy diagnostics report", "Paste it to support to find the cause", Ui.CYAN, new Runnable() {
                public void run() { Diag.copyReport(a); }
            })
            .item("🔄", "Try again later", "Close other apps to free memory, then retry", Ui.ORANGE, null)
            .show();
    }
}
