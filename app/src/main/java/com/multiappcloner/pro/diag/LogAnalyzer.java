package com.multiappcloner.pro.diag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads log lines and names the known failure classes, so a report starts with a diagnosis, not a wall of text. */
public final class LogAnalyzer {

    public static final class Finding {
        public final String category;
        public final String summary;
        public final String hint;
        public final int count;
        public final String example;
        Finding(String category, String summary, String hint, int count, String example) {
            this.category = category; this.summary = summary; this.hint = hint; this.count = count; this.example = example;
        }
    }

    private static final class Rule {
        final String category, summary, hint; final String[] needles;
        Rule(String category, String summary, String hint, String... needles) {
            this.category = category; this.summary = summary; this.hint = hint; this.needles = needles;
        }
    }

    private static final Rule[] RULES = {
        new Rule("MISSING_LIBRARY", "A native library could not be loaded",
            "The app needs a .so that is not present for this phone's CPU (ABI), or a split APK was not merged.",
            "UnsatisfiedLinkError", "dlopen failed", "couldn't find \"lib", "library \"lib"),
        new Rule("SHARED_LIBRARY", "The app depends on a separate shared library package",
            "Chrome-style apps (Trichrome) load code from another package that the virtual space cannot provide.",
            "trichromelibrary", "Trichrome", "uses-static-library", "static shared library"),
        new Rule("MEMORY", "Out of memory",
            "Too many clones or a heavy app. Stop other clones and close background apps.",
            "OutOfMemoryError", "lowmemorykiller", "Out of memory", "Failed to allocate a", "Low on memory"),
        new Rule("INSTALL_SIGNATURE", "Signature problem during install",
            "The APK signature could not be verified or the app checks its own signature.",
            "INSTALL_PARSE_FAILED_NO_CERTIFICATES", "INSTALL_FAILED_INVALID_SIGNATURE", "signature mismatch", "Invalid signature"),
        new Rule("INSTALL_FAILED", "Package install failed",
            "See the install error line for the exact PackageInstaller / engine status.",
            "INSTALL_FAILED", "INSTALL_PARSE_FAILED", "Virtual install failed", "Install failed"),
        new Rule("ENGINE_API", "The virtual engine hit an Android API difference",
            "A hooked system call returned the wrong type or does not exist on this Android version.",
            "ClassCastException", "NoSuchMethodError", "NoSuchFieldError", "NoSuchMethodException", "hiddenapi"),
        new Rule("PERMISSION", "A permission was refused",
            "Check All files access, notifications and battery settings for this app.",
            "SecurityException", "Permission denied", "EACCES", "Permission Denial"),
        new Rule("STORAGE", "Storage problem",
            "No space left, or the SD card is missing/read-only.",
            "ENOSPC", "No space left", "Read-only file system", "EROFS"),
        new Rule("PROCESS_DIED", "A clone process was killed or exited",
            "The system killed the process (memory/battery policy) or the app quit itself.",
            "Process .* has died", "has died", "System.exit", "am_proc_died", "Sending signal. PID"),
        new Rule("APP_CRASH", "A clone crashed",
            "See the stack trace in this report.",
            "FATAL EXCEPTION", "AndroidRuntime: Shutting down VM"),
    };

    public static List<Finding> analyze(List<String> lines) {
        Map<String, int[]> counts = new LinkedHashMap<String, int[]>();
        Map<String, String> examples = new LinkedHashMap<String, String>();
        for (String line : lines) {
            if (line == null) continue;
            for (Rule r : RULES) {
                if (matches(r, line)) {
                    int[] c = counts.get(r.category);
                    if (c == null) { c = new int[1]; counts.put(r.category, c); examples.put(r.category, line.trim()); }
                    c[0]++;
                }
            }
        }
        List<Finding> out = new ArrayList<Finding>();
        for (Rule r : RULES) {
            int[] c = counts.get(r.category);
            if (c != null) out.add(new Finding(r.category, r.summary, r.hint, c[0], examples.get(r.category)));
        }
        return out;
    }

    private static boolean matches(Rule r, String line) {
        for (String n : r.needles) {
            if (n.indexOf('*') >= 0) {
                String[] parts = n.split("\\.\\*");
                int from = 0; boolean ok = true;
                for (String p : parts) { int i = line.indexOf(p, from); if (i < 0) { ok = false; break; } from = i + p.length(); }
                if (ok) return true;
            } else if (line.contains(n)) return true;
        }
        return false;
    }

    public static String format(List<Finding> f) {
        if (f.isEmpty()) return "No known problem pattern found in the log.\n";
        StringBuilder sb = new StringBuilder();
        for (Finding x : f) {
            sb.append("- [").append(x.category).append("] ").append(x.summary).append(" (x").append(x.count).append(")\n");
            sb.append("    why: ").append(x.hint).append('\n');
            String ex = x.example.length() > 220 ? x.example.substring(0, 220) + "..." : x.example;
            sb.append("    e.g.: ").append(ex).append('\n');
        }
        return sb.toString();
    }
}
