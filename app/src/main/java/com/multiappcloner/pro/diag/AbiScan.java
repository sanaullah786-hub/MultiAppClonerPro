package com.multiappcloner.pro.diag;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds which CPU ABIs an APK ships native libraries for, to explain "library not found" failures before launch. */
public final class AbiScan {

    /** ABI folder names under lib/ (e.g. arm64-v8a, armeabi-v7a, x86_64). Empty set = no native code at all. */
    public static Set<String> abis(File apk) throws IOException {
        Set<String> out = new LinkedHashSet<String>();
        ZipFile z = new ZipFile(apk);
        try {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.startsWith("lib/") && n.endsWith(".so")) {
                    int s = n.indexOf('/', 4);
                    if (s > 4) out.add(n.substring(4, s));
                }
            }
        } finally {
            z.close();
        }
        return out;
    }

    /**
     * Returns a plain-language problem, or null when the app's native code can run on a device that supports
     * the given ABIs (in preference order, as Build.SUPPORTED_ABIS lists them).
     */
    public static String incompatibility(Set<String> appAbis, String[] deviceAbis) {
        if (appAbis.isEmpty()) return null;
        for (String d : deviceAbis) if (appAbis.contains(d)) return null;
        return "The app only contains native code for " + appAbis + " but this phone runs " + java.util.Arrays.toString(deviceAbis) + ".";
    }

    /**
     * True when the app's manifest declares a static shared library dependency (Chrome's Trichrome library is
     * the well-known case). The binary manifest keeps element names in its string pool, so a byte search is enough.
     */
    public static boolean usesStaticLibrary(File apk) {
        try {
            ZipFile z = new ZipFile(apk);
            try {
                ZipEntry e = z.getEntry("AndroidManifest.xml");
                if (e == null || e.getSize() > 8 * 1024 * 1024) return false;
                java.io.InputStream in = z.getInputStream(e);
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[16384];
                int r;
                while ((r = in.read(b)) > 0) o.write(b, 0, r);
                in.close();
                byte[] m = o.toByteArray();
                return indexOf(m, "uses-static-library".getBytes("UTF-8")) >= 0
                    || indexOf(m, "uses-static-library".getBytes("UTF-16LE")) >= 0;
            } finally {
                z.close();
            }
        } catch (IOException ex) {
            return false;
        }
    }

    private static int indexOf(byte[] h, byte[] n) {
        outer:
        for (int i = 0; i + n.length <= h.length; i++) {
            for (int j = 0; j < n.length; j++) if (h[i + j] != n[j]) continue outer;
            return i;
        }
        return -1;
    }
}
