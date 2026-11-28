package com.multiappcloner.pro;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.widget.Toast;

import com.multiappcloner.pro.diag.DiagStore;
import com.multiappcloner.pro.diag.LogAnalyzer;
import com.multiappcloner.pro.diag.Redactor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Diagnostics: persistent event log (all processes), uncaught-crash recorder and the "Copy diagnostics report"
 * builder. Everything stored or copied passes through {@link Redactor}, and no log is ever uploaded.
 */
public final class Diag {
    public static final String CRASH = "CRASH", INSTALL = "INSTALL", LAUNCH = "LAUNCH", ENGINE = "ENGINE",
        MEMORY = "MEMORY", COMPAT = "COMPAT", STORAGE = "STORAGE";

    private static volatile DiagStore store;
    private static volatile Context appContext;

    /** Call once per process (CloneApp.onCreate). Installs the crash recorder. */
    public static void init(Application app) {
        try {
            appContext = app;
            store = new DiagStore(new File(app.getFilesDir(), "diag"));
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    try {
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        String s = sw.toString();
                        if (s.length() > 3500) s = s.substring(0, 3500);
                        event(CRASH, "process=" + processName() + " thread=" + t.getName() + " " + memoryLine() + "\n" + s);
                    } catch (Throwable ignored) { }
                    if (prev != null) prev.uncaughtException(t, e);
                }
            });
        } catch (Throwable ignored) { }
    }

    public static void event(String category, String message) {
        DiagStore s = store;
        if (s != null) s.record(category, message);
    }

    /** True when the last recorded crash happened after the last time the user looked at it. */
    public static boolean hasUnseenCrash(Context c) {
        try {
            List<String> l = new DiagStore(new File(c.getFilesDir(), "diag")).read(CRASH, 1);
            if (l.isEmpty()) return false;
            return !l.get(0).equals(c.getSharedPreferences("diag", Context.MODE_PRIVATE).getString("seen_crash", ""));
        } catch (Throwable t) {
            return false;
        }
    }

    public static void markCrashSeen(Context c) {
        try {
            List<String> l = new DiagStore(new File(c.getFilesDir(), "diag")).read(CRASH, 1);
            if (!l.isEmpty()) c.getSharedPreferences("diag", Context.MODE_PRIVATE).edit().putString("seen_crash", l.get(0)).apply();
        } catch (Throwable ignored) { }
    }

    public static void clear(Context c) {
        new DiagStore(new File(c.getFilesDir(), "diag")).clear();
    }

    static String processName() {
        try {
            if (Build.VERSION.SDK_INT >= 28) return Application.getProcessName();
        } catch (Throwable ignored) { }
        return "?";
    }

    static String memoryLine() {
        try {
            Context c = appContext;
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            Runtime rt = Runtime.getRuntime();
            return "ram_free=" + (mi.availMem >> 20) + "MB/" + (mi.totalMem >> 20) + "MB low=" + mi.lowMemory
                + " heap=" + ((rt.totalMemory() - rt.freeMemory()) >> 20) + "/" + (rt.maxMemory() >> 20) + "MB";
        } catch (Throwable t) {
            return "ram=?";
        }
    }

    // ------------------------------------------------------------------ report

    public static String buildReport(Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Multi App Cloner Pro diagnostics report ===\n");
        sb.append("Personal data (emails, phone numbers, tokens, passwords) is removed from this report.\n\n");
        header(c, sb);

        List<String> events = new DiagStore(new File(c.getFilesDir(), "diag")).read(null, 200);
        List<String> log = readLogcat();
        List<String> forAnalysis = new ArrayList<String>(log);
        forAnalysis.addAll(events);

        sb.append("\n--- Diagnosis ---\n").append(LogAnalyzer.format(LogAnalyzer.analyze(forAnalysis)));

        sb.append("\n--- Clones (").append("package, method, status, virtual user, engine state) ---\n");
        try {
            for (CloneProfile p : new CloneRepository(c).getAll()) {
                boolean virt = CloneRepository.METHOD_VIRTUAL.equals(p.method);
                sb.append("#").append(p.id).append(' ').append(p.packageName).append(' ').append(p.method).append(' ').append(p.status);
                if (virt) sb.append(" user=").append(p.virtualUser).append(" inEngine=").append(VirtualEngine.isInstalled(p.packageName, p.virtualUser));
                else sb.append(" clonePackage=").append(p.clonePackage);
                sb.append('\n');
            }
        } catch (Throwable t) {
            sb.append("could not read clones: ").append(t).append('\n');
        }

        sb.append("\n--- Recorded crashes ---\n");
        List<String> crashes = new DiagStore(new File(c.getFilesDir(), "diag")).read(CRASH, 5);
        if (crashes.isEmpty()) sb.append("none\n");
        for (String l : crashes) sb.append(l).append('\n');

        sb.append("\n--- Recorded install / launch / compatibility events ---\n");
        int shown = 0;
        for (String l : events) {
            if (l.length() > 15 && !l.substring(15).startsWith(CRASH + " ")) { sb.append(l).append('\n'); shown++; }
        }
        if (shown == 0) sb.append("none\n");

        sb.append("\n--- Filtered system log (engine, errors, exceptions) ---\n");
        int from = Math.max(0, log.size() - 300);
        for (int i = from; i < log.size(); i++) sb.append(log.get(i)).append('\n');

        String out = Redactor.redact(sb.toString());
        if (out.length() > 100000) out = out.substring(out.length() - 100000);
        return out;
    }

    private static void header(Context c, StringBuilder sb) {
        String ver = "?";
        try { PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0); ver = pi.versionName + " (" + pi.versionCode + ")"; } catch (Throwable ignored) { }
        sb.append("App version: ").append(ver).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append("), patch ").append(Build.VERSION.SECURITY_PATCH).append('\n');
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", ABIs ").append(java.util.Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        sb.append("Memory: ").append(memoryLineFor(c)).append('\n');
        sb.append("Engine loaded: ").append(VirtualEngine.isAvailable()).append(", Google services supported: ").append(VirtualEngine.isGmsSupported()).append('\n');
        if (Build.VERSION.SDK_INT >= 30) sb.append("All files access: ").append(Environment.isExternalStorageManager()).append('\n');
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            sb.append("Battery optimisation ignored for this app: ").append(pm.isIgnoringBatteryOptimizations(c.getPackageName())).append('\n');
        } catch (Throwable ignored) { }
        sb.append("Install-unknown-apps allowed: ").append(CloneInstaller.canInstall(c)).append('\n');
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                PackageInfo w = android.webkit.WebView.getCurrentWebViewPackage();
                sb.append("WebView provider: ").append(w == null ? "none" : w.packageName + " " + w.versionName).append('\n');
            }
        } catch (Throwable t) {
            sb.append("WebView provider: unknown\n");
        }
        sb.append("Guest storage on SD card: ").append(new File(c.getFilesDir(), "guest_storage_sd").exists()).append('\n');
    }

    private static String memoryLineFor(Context c) {
        Context old = appContext;
        if (old == null) appContext = c;
        String s = memoryLine();
        if (old == null) appContext = null;
        return s;
    }

    private static List<String> readLogcat() {
        LinkedList<String> keep = new LinkedList<String>();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "threadtime", "-t", "8000"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            while ((l = r.readLine()) != null) {
                boolean hit = l.contains(" E ") || l.contains(" W ") || l.contains("FATAL") || l.contains("AndroidRuntime")
                    || l.contains("BlackBox") || l.contains("VirtualEngine") || l.contains("CloneApp") || l.contains("SplitMerger")
                    || l.contains("Exception") || l.contains("at top.niunaijun") || l.contains("dlopen");
                if (hit) { keep.add(l.length() > 600 ? l.substring(0, 600) : l); if (keep.size() > 600) keep.removeFirst(); }
            }
        } catch (Throwable t) {
            keep.add("Could not read the system log: " + t);
        }
        return new ArrayList<String>(keep);
    }

    public static void copyReport(final Activity a) {
        Toast.makeText(a, "Building report...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                final String text = buildReport(a.getApplicationContext());
                a.runOnUiThread(new Runnable() {
                    public void run() {
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("diagnostics report", text));
                        Toast.makeText(a, "Report copied (" + (text.length() / 1024 + 1) + " KB). Paste it into the chat.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "diag-report").start();
    }
}
