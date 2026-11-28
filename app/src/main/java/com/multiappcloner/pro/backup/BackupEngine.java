package com.multiappcloner.pro.backup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Backs up and restores one virtual clone's data folders.
 * A backup is a zip file holding the files plus a MANIFEST with the package, virtual user, app version and the
 * SHA-256 and size of every file. Restore first verifies the whole file, extracts to a temporary folder, and only
 * then swaps it in; if the swap fails the old data is put back.
 */
public final class BackupEngine {
    public static final int FORMAT = 1;
    private static final String MANIFEST = "MANIFEST";

    public interface Progress { void onProgress(long doneBytes, long totalBytes); }

    public static final class Entry {
        public final String name, sha256; public final long size;
        Entry(String name, String sha256, long size) { this.name = name; this.sha256 = sha256; this.size = size; }
    }

    public static final class Manifest {
        public int format;
        public String packageName = "";
        public int userId = -1;
        public long createdAt;
        public String appVersion = "";
        public final List<String> roots = new ArrayList<String>();
        public final List<Entry> entries = new ArrayList<Entry>();
        public long totalBytes() { long t = 0; for (Entry e : entries) t += e.size; return t; }
    }

    // ------------------------------------------------------------------ create

    public static Manifest create(File out, String pkg, int userId, String appVersion, Map<String, File> roots, Progress progress) throws IOException {
        if (pkg == null || pkg.length() == 0) throw new IOException("No package name.");
        Manifest m = new Manifest();
        m.format = FORMAT; m.packageName = pkg; m.userId = userId; m.createdAt = System.currentTimeMillis(); m.appVersion = appVersion == null ? "" : appVersion;
        long total = 0;
        for (File r : roots.values()) total += sizeOf(r);
        File part = new File(out.getPath() + ".part");
        long[] done = {0};
        boolean ok = false;
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(part), 64 * 1024));
            for (Map.Entry<String, File> r : roots.entrySet()) {
                m.roots.add(r.getKey());
                List<File> files = new ArrayList<File>();
                collect(r.getValue(), files);
                for (File f : files) {
                    String rel = relative(r.getValue(), f);
                    String name = r.getKey() + "/" + rel;
                    ZipEntry ze = new ZipEntry(name);
                    ze.setTime(f.lastModified());
                    zos.putNextEntry(ze);
                    MessageDigest md = sha();
                    long size = 0;
                    InputStream in = new FileInputStream(f);
                    try {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            zos.write(buf, 0, n);
                            md.update(buf, 0, n);
                            size += n; done[0] += n;
                            if (progress != null) progress.onProgress(done[0], total);
                        }
                    } finally {
                        in.close();
                    }
                    zos.closeEntry();
                    m.entries.add(new Entry(name, hex(md.digest()), size));
                }
            }
            ZipEntry me = new ZipEntry(MANIFEST);
            zos.putNextEntry(me);
            zos.write(formatManifest(m).getBytes("UTF-8"));
            zos.closeEntry();
            zos.finish();
            zos.close();
            zos = null;
            if (out.exists() && !out.delete()) throw new IOException("Could not replace the existing backup file.");
            if (!part.renameTo(out)) throw new IOException("Could not finish the backup file.");
            ok = true;
            return m;
        } finally {
            if (zos != null) try { zos.close(); } catch (IOException ignored) { }
            if (!ok) part.delete();
        }
    }

    // ------------------------------------------------------------------ verify

    /** Reads the whole backup and checks every file against the manifest. Throws a readable IOException when it is damaged. */
    public static Manifest verify(File zip, Progress progress) throws IOException {
        ZipFile z;
        try {
            z = new ZipFile(zip);
        } catch (IOException e) {
            throw new IOException("This is not a valid backup file (cannot be opened).");
        }
        try {
            ZipEntry me = z.getEntry(MANIFEST);
            if (me == null) throw new IOException("This is not a clone backup (no manifest).");
            Manifest m = parseManifest(readAll(z.getInputStream(me)));
            if (m.format != FORMAT) throw new IOException("Unsupported backup version " + m.format + ".");
            Map<String, Entry> expected = new HashMap<String, Entry>();
            for (Entry e : m.entries) {
                checkName(e.name);
                if (!rootOf(e.name, m)) throw new IOException("Backup contains a file outside its folders: " + e.name);
                expected.put(e.name, e);
            }
            long total = m.totalBytes(), done = 0;
            Enumeration<? extends ZipEntry> en = z.entries();
            int seen = 0;
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (ze.getName().equals(MANIFEST)) continue;
                Entry e = expected.get(ze.getName());
                if (e == null) throw new IOException("Backup contains an unexpected file: " + ze.getName());
                MessageDigest md = sha();
                long size = 0;
                InputStream in = z.getInputStream(ze);
                try {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) { md.update(buf, 0, n); size += n; done += n; if (progress != null) progress.onProgress(done, total); }
                } finally {
                    in.close();
                }
                if (size != e.size || !hex(md.digest()).equals(e.sha256)) throw new IOException("Backup is damaged: " + e.name);
                seen++;
            }
            if (seen != m.entries.size()) throw new IOException("Backup is incomplete: " + (m.entries.size() - seen) + " file(s) missing.");
            return m;
        } finally {
            z.close();
        }
    }

    // ------------------------------------------------------------------ restore

    /**
     * @param targetRoots where each backed-up folder goes (same keys as when the backup was made)
     * @return the verified manifest
     */
    public static Manifest restore(File zip, String expectedPackage, Map<String, File> targetRoots, Progress progress) throws IOException {
        Manifest m = verify(zip, null);
        if (!m.packageName.equals(expectedPackage)) throw new IOException("This backup is for " + m.packageName + ", not " + expectedPackage + ".");
        for (String r : m.roots) if (!targetRoots.containsKey(r)) throw new IOException("No place to restore the '" + r + "' folder.");

        Map<String, File> tmp = new LinkedHashMap<String, File>();
        Map<String, File> old = new LinkedHashMap<String, File>();
        for (String r : m.roots) {
            File t = targetRoots.get(r);
            tmp.put(r, new File(t.getParentFile(), t.getName() + ".restore_tmp"));
            old.put(r, new File(t.getParentFile(), t.getName() + ".restore_old"));
            deleteTree(tmp.get(r));
            deleteTree(old.get(r));
            if (!tmp.get(r).mkdirs() && !tmp.get(r).isDirectory()) throw new IOException("Cannot create a temporary folder next to " + t.getName());
        }
        ZipFile z = new ZipFile(zip);
        List<String> swapped = new ArrayList<String>();
        try {
            long total = m.totalBytes(), done = 0;
            for (Entry e : m.entries) {
                String root = e.name.substring(0, e.name.indexOf('/'));
                String rel = e.name.substring(root.length() + 1);
                File base = tmp.get(root);
                File dst = new File(base, rel);
                String bc = base.getCanonicalPath() + File.separator;
                if (!dst.getCanonicalPath().startsWith(bc)) throw new IOException("Unsafe path in backup: " + e.name);
                dst.getParentFile().mkdirs();
                InputStream in = z.getInputStream(z.getEntry(e.name));
                OutputStream os = new FileOutputStream(dst);
                try {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) { os.write(buf, 0, n); done += n; if (progress != null) progress.onProgress(done, total); }
                } finally {
                    in.close();
                    os.close();
                }
            }
            for (String r : m.roots) {
                File target = targetRoots.get(r);
                if (target.exists() && !target.renameTo(old.get(r))) throw new IOException("Could not move the current data of '" + r + "' aside.");
                if (!tmp.get(r).renameTo(target)) {
                    if (old.get(r).exists()) old.get(r).renameTo(target);
                    throw new IOException("Could not put the restored '" + r + "' data in place.");
                }
                swapped.add(r);
            }
        } catch (IOException ex) {
            for (String r : swapped) {
                File target = targetRoots.get(r);
                deleteTree(target);
                if (old.get(r).exists()) old.get(r).renameTo(target);
            }
            for (String r : m.roots) deleteTree(tmp.get(r));
            throw ex;
        } finally {
            z.close();
        }
        for (String r : m.roots) deleteTree(old.get(r));
        return m;
    }

    // ------------------------------------------------------------------ manifest

    static String formatManifest(Manifest m) {
        StringBuilder sb = new StringBuilder();
        sb.append("MAC-BACKUP ").append(m.format).append('\n');
        sb.append("package=").append(m.packageName).append('\n');
        sb.append("user=").append(m.userId).append('\n');
        sb.append("created=").append(m.createdAt).append('\n');
        sb.append("app=").append(esc(m.appVersion)).append('\n');
        for (String r : m.roots) sb.append("root=").append(r).append('\n');
        for (Entry e : m.entries) sb.append("entry=").append(e.sha256).append(' ').append(e.size).append(' ').append(esc(e.name)).append('\n');
        return sb.toString();
    }

    static Manifest parseManifest(byte[] data) throws IOException {
        String[] lines = new String(data, "UTF-8").split("\n");
        if (lines.length == 0 || !lines[0].startsWith("MAC-BACKUP ")) throw new IOException("This is not a clone backup (bad manifest).");
        Manifest m = new Manifest();
        try {
            m.format = Integer.parseInt(lines[0].substring(11).trim());
            for (int i = 1; i < lines.length; i++) {
                String l = lines[i];
                if (l.startsWith("package=")) m.packageName = l.substring(8);
                else if (l.startsWith("user=")) m.userId = Integer.parseInt(l.substring(5));
                else if (l.startsWith("created=")) m.createdAt = Long.parseLong(l.substring(8));
                else if (l.startsWith("app=")) m.appVersion = unesc(l.substring(4));
                else if (l.startsWith("root=")) m.roots.add(l.substring(5));
                else if (l.startsWith("entry=")) {
                    String[] p = l.substring(6).split(" ", 3);
                    if (p.length != 3 || p[0].length() != 64) throw new IOException("Damaged manifest line.");
                    m.entries.add(new Entry(unesc(p[2]), p[0], Long.parseLong(p[1])));
                }
            }
        } catch (NumberFormatException e) {
            throw new IOException("Damaged backup manifest.");
        }
        if (m.packageName.length() == 0) throw new IOException("Backup manifest has no package.");
        for (String r : m.roots) if (r.length() == 0 || r.indexOf('/') >= 0 || r.contains("..")) throw new IOException("Bad folder name in backup.");
        return m;
    }

    private static String esc(String s) {
        return s.replace("%", "%25").replace("\n", "%0A").replace("\r", "%0D");
    }

    private static String unesc(String s) {
        return s.replace("%0A", "\n").replace("%0D", "\r").replace("%25", "%");
    }

    private static void checkName(String n) throws IOException {
        if (n.startsWith("/") || n.contains("..") || n.indexOf('\\') >= 0 || n.indexOf('/') < 1) throw new IOException("Unsafe path in backup: " + n);
    }

    private static boolean rootOf(String name, Manifest m) {
        String root = name.substring(0, name.indexOf('/'));
        return m.roots.contains(root);
    }

    // ------------------------------------------------------------------ files

    /** Regular files only, in a stable order. Symbolic links are never followed or saved. */
    private static void collect(File dir, List<File> out) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        Arrays.sort(kids);
        for (File f : kids) {
            if (isLink(f)) continue;
            if (f.isDirectory()) collect(f, out);
            else if (f.isFile()) out.add(f);
        }
    }

    private static boolean isLink(File f) throws IOException {
        File parent = f.getParentFile();
        return !f.getCanonicalPath().equals(new File(parent.getCanonicalFile(), f.getName()).getPath());
    }

    private static String relative(File root, File f) throws IOException {
        String r = root.getCanonicalPath(), p = f.getCanonicalPath();
        return p.substring(r.length() + 1).replace(File.separatorChar, '/');
    }

    public static long sizeOf(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long t = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) {
            try { if (isLink(k)) continue; } catch (IOException e) { continue; }
            t += sizeOf(k);
        }
        return t;
    }

    public static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        try { if (isLink(f)) { f.delete(); return; } } catch (IOException ignored) { }
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private static MessageDigest sha() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); } catch (Exception e) { throw new IOException("SHA-256 unavailable"); }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(64);
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) > 0) { o.write(b, 0, r); if (o.size() > 64 * 1024 * 1024) throw new IOException("Manifest too large."); }
            return o.toByteArray();
        } finally {
            in.close();
        }
    }
}
