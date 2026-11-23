package com.multiappcloner.pro.engine;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Copies an APK into a new, unsigned, correctly aligned APK with a replaced AndroidManifest.xml.
 * Old signature files (META-INF/*.SF, *.RSA, *.DSA, *.EC, MANIFEST.MF) are dropped.
 * Stored entries are 4-byte aligned, stored .so files are 4096-byte aligned (what zipalign -p does).
 */
public final class ApkRewriter {

    private static final long FIXED_TIME = 1230768000000L; // 2009-01-01, inside the DOS time range

    public interface ManifestEditor {
        byte[] edit(byte[] originalManifest) throws IOException;
    }

    private static final class Counting extends OutputStream {
        private final OutputStream o;
        long count;
        Counting(OutputStream o) { this.o = o; }
        @Override public void write(int b) throws IOException { o.write(b); count++; }
        @Override public void write(byte[] b, int off, int len) throws IOException { o.write(b, off, len); count += len; }
        @Override public void flush() throws IOException { o.flush(); }
        @Override public void close() throws IOException { o.close(); }
    }

    public static void rewrite(File src, File dst, ManifestEditor editor) throws IOException {
        ZipFile zf = new ZipFile(src);
        Counting counting = null;
        ZipOutputStream zos = null;
        boolean ok = false;
        try {
            counting = new Counting(new BufferedOutputStream(new FileOutputStream(dst), 64 * 1024));
            zos = new ZipOutputStream(counting);
            Set<String> seen = new HashSet<String>();
            boolean manifestDone = false;
            byte[] buf = new byte[32 * 1024];
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String name = e.getName();
                if (isSignatureFile(name) || !seen.add(name)) continue;
                if (name.contains("..")) throw new IOException("Unsafe entry name: " + name);

                if (name.equals("AndroidManifest.xml")) {
                    byte[] orig = readAll(zf.getInputStream(e));
                    byte[] patched = editor.edit(orig);
                    putStored(zos, counting, name, patched);
                    manifestDone = true;
                    continue;
                }
                if (e.isDirectory()) {
                    ZipEntry d = new ZipEntry(name);
                    d.setTime(FIXED_TIME);
                    zos.putNextEntry(d);
                    zos.closeEntry();
                    continue;
                }
                if (e.getMethod() == ZipEntry.STORED) {
                    byte[] bytes = readAll(zf.getInputStream(e));
                    putStored(zos, counting, name, bytes);
                } else {
                    ZipEntry n = new ZipEntry(name);
                    n.setTime(FIXED_TIME);
                    n.setMethod(ZipEntry.DEFLATED);
                    zos.putNextEntry(n);
                    InputStream in = zf.getInputStream(e);
                    try {
                        int r;
                        while ((r = in.read(buf)) > 0) zos.write(buf, 0, r);
                    } finally {
                        in.close();
                    }
                    zos.closeEntry();
                }
            }
            if (!manifestDone) throw new IOException("APK has no AndroidManifest.xml.");
            zos.finish();
            zos.flush();
            ok = true;
        } finally {
            try { if (zos != null) zos.close(); } catch (IOException ignored) { }
            try { zf.close(); } catch (IOException ignored) { }
            if (!ok) dst.delete();
        }
    }

    private static void putStored(ZipOutputStream zos, Counting c, String name, byte[] bytes) throws IOException {
        ZipEntry n = new ZipEntry(name);
        n.setTime(FIXED_TIME);
        n.setMethod(ZipEntry.STORED);
        n.setSize(bytes.length);
        n.setCompressedSize(bytes.length);
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        n.setCrc(crc.getValue());
        int align = name.endsWith(".so") ? 4096 : 4;
        int nameLen = name.getBytes("UTF-8").length;
        long dataOffset = c.count + 30 + nameLen;
        int pad = (int) ((align - (dataOffset % align)) % align);
        if (pad > 0) {
            if (pad < 4) pad += align;
            byte[] extra = new byte[pad];
            extra[0] = (byte) 0x35; // id 0xD935 (zipalign padding)
            extra[1] = (byte) 0xD9;
            int payload = pad - 4;
            extra[2] = (byte) (payload & 0xFF);
            extra[3] = (byte) ((payload >> 8) & 0xFF);
            n.setExtra(extra);
        }
        zos.putNextEntry(n);
        zos.write(bytes, 0, bytes.length);
        zos.closeEntry();
    }

    static boolean isSignatureFile(String name) {
        if (!name.startsWith("META-INF/")) return false;
        String rest = name.substring(9);
        if (rest.indexOf('/') >= 0) return false;
        String u = rest.toUpperCase(java.util.Locale.ROOT);
        return u.equals("MANIFEST.MF") || u.endsWith(".SF") || u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[32 * 1024];
            int r;
            while ((r = in.read(b)) > 0) o.write(b, 0, r);
            return o.toByteArray();
        } finally {
            in.close();
        }
    }
}
