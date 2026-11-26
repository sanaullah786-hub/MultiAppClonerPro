package com.multiappcloner.pro;

import android.content.ContentValues;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Checks which cloning methods could work for an app and records the result in the compatibility registry. */
public class HybridCloneManager {
    public static class Analysis {
        public ApkInspector.Report report;
        public boolean realAvailable;
        public boolean virtualAvailable;
        public List<String> realReasons = new ArrayList<String>();
        public List<String> realWarnings = new ArrayList<String>();
        public String virtualReason;
        public List<String> methods = new ArrayList<String>();
    }

    public static Analysis analyze(Context c, String pkg) {
        Analysis a = new Analysis();
        a.report = ApkInspector.inspect(c, pkg);
        if (a.report.error == null) {
            a.realReasons = ApkCloningEngine.blockers(a.report);
            a.realAvailable = a.realReasons.isEmpty();
            a.realWarnings = ApkCloningEngine.warnings(a.report);
        } else {
            a.realReasons.add(a.report.error);
        }
        a.virtualAvailable = a.report.error == null && !pkg.equals(c.getPackageName()) && VirtualEngine.isAvailable();
        a.virtualReason = a.report.error != null ? a.report.error
            : a.report.system ? "System app: it can run in a virtual space, but system apps may behave differently there."
            : "Runs inside this app with separate data for every clone, like Parallel Space. Apps that use Google sign-in, Play Integrity or strict "
              + "anti-tamper checks may not work inside a virtual space.";
        if (a.realAvailable) a.methods.add("real_apk");
        if (a.virtualAvailable) a.methods.add("virtual");
        store(c, pkg, a);
        return a;
    }

    private static void store(Context c, String pkg, Analysis a) {
        try {
            ContentValues v = new ContentValues();
            v.put("package_name", pkg);
            v.put("real_status", a.realAvailable ? "available" : "unsupported");
            v.put("virtual_status", a.virtualAvailable ? "available" : "unavailable");
            StringBuilder sb = new StringBuilder();
            for (String s : a.realReasons) sb.append(s).append('\n');
            for (String s : a.realWarnings) sb.append(s).append('\n');
            v.put("reasons", sb.toString());
            v.put("checked_at", System.currentTimeMillis());
            DatabaseHelper.get(c).getWritableDatabase().insertWithOnConflict(DatabaseHelper.T_COMPAT, null, v,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
        } catch (Exception ignored) {
        }
    }

    /** Runs the chosen workflow. Never reports success unless a method really ran. */
    public static ApkCloningEngine.Result execute(Context c, long cloneId, String pkg, String cloneName, String method) {
        if ("real_apk".equals(method)) return ApkCloningEngine.cloneReal(c, cloneId, pkg, cloneName);
        if ("virtual".equals(method)) return VirtualEngine.install(c, pkg, (int) cloneId);
        return new ApkCloningEngine.Result(false, "No supported cloning method was selected.");
    }
}
