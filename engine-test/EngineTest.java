package com.multiappcloner.pro.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Plain-JVM tests for the cloning engine (no JUnit needed). Usage: EngineTest <keystore.p12> <password> */
public class EngineTest {
    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        for (boolean utf8 : new boolean[]{true, false}) testManifest(utf8);
        testManifestEdgeCases();
        testZipAndSigner(args[0], args[1]);
        if (failures > 0) {
            System.out.println(failures + " FAILED");
            System.exit(1);
        }
        System.out.println("ALL PASSED");
    }

    // ============================================================ AXML builder / dumper (test-side)

    static final String NS = "http://schemas.android.com/apk/res/android";

    static class Attr {
        String name; int type; String str; int data;
        Attr(String n, String s) { name = n; type = 3; str = s; }
        Attr(String n, int t, int d) { name = n; type = t; data = d; }
    }

    static class El {
        String tag; List<Attr> attrs = new ArrayList<Attr>(); boolean open = true;
        El(String t, Attr... a) { tag = t; attrs.addAll(Arrays.asList(a)); }
    }

    static byte[] buildAxml(List<Object> events, boolean utf8) {
        // events: El (start) or String "/" (end)
        List<String> pool = new ArrayList<String>();
        for (Object o : events) {
            if (o instanceof El) {
                El e = (El) o;
                for (Attr a : e.attrs) intern(pool, a.name);
            }
        }
        // attribute names first (resource map would cover them)
        int nAttrNames = pool.size();
        intern(pool, "android");
        intern(pool, NS);
        for (Object o : events) {
            if (o instanceof El) {
                El e = (El) o;
                intern(pool, e.tag);
                for (Attr a : e.attrs) if (a.type == 3) intern(pool, a.str);
            }
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // namespace start
        body.write(chunk(0x0100, 16, le32(1), le32(-1), le32(pool.indexOf("android")), le32(pool.indexOf(NS))), 0, 24);
        for (Object o : events) {
            if (o instanceof El) {
                El e = (El) o;
                ByteArrayOutputStream ext = new ByteArrayOutputStream();
                w(ext, le32(-1), le32(pool.indexOf(e.tag)), le16(20), le16(20), le16(e.attrs.size()), le16(0), le16(0), le16(0));
                for (Attr a : e.attrs) {
                    boolean noNs = a.name.equals("package");
                    w(ext, le32(noNs ? -1 : pool.indexOf(NS)), le32(pool.indexOf(a.name)),
                        le32(a.type == 3 ? pool.indexOf(a.str) : -1), le16(8), new byte[]{0, (byte) a.type},
                        le32(a.type == 3 ? pool.indexOf(a.str) : a.data));
                }
                byte[] x = ext.toByteArray();
                byte[] c = chunk(0x0102, 16, le32(2), le32(-1), x);
                body.write(c, 0, c.length);
            } else {
                byte[] c = chunk(0x0103, 16, le32(3), le32(-1), le32(-1), le32(pool.indexOf("manifest")));
                body.write(c, 0, c.length);
            }
        }
        byte[] ns = chunk(0x0101, 16, le32(99), le32(-1), le32(pool.indexOf("android")), le32(pool.indexOf(NS)));
        body.write(ns, 0, ns.length);

        // resource map: ids for attribute names (fake values)
        byte[] resMap = new byte[8 + 4 * nAttrNames];
        ByteBuffer rb = ByteBuffer.wrap(resMap).order(ByteOrder.LITTLE_ENDIAN);
        rb.putShort((short) 0x0180); rb.putShort((short) 8); rb.putInt(resMap.length);
        for (int i = 0; i < nAttrNames; i++) rb.putInt(0x01010000 + i);

        byte[] poolBytes = buildPool(pool, utf8);
        int total = 8 + poolBytes.length + resMap.length + body.size();
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putShort((short) 0x0003); out.putShort((short) 8); out.putInt(total);
        out.put(poolBytes); out.put(resMap); out.put(body.toByteArray());
        return out.array();
    }

    static byte[] buildPool(List<String> pool, boolean utf8) {
        ByteArrayOutputStream sd = new ByteArrayOutputStream();
        int[] offs = new int[pool.size()];
        for (int i = 0; i < pool.size(); i++) {
            offs[i] = sd.size();
            String s = pool.get(i);
            if (utf8) {
                byte[] b = s.getBytes(Charset.forName("UTF-8"));
                sd.write(s.length()); sd.write(b.length); sd.write(b, 0, b.length); sd.write(0);
            } else {
                sd.write(s.length() & 0xFF); sd.write(s.length() >> 8);
                byte[] b = s.getBytes(Charset.forName("UTF-16LE"));
                sd.write(b, 0, b.length); sd.write(0); sd.write(0);
            }
        }
        while (sd.size() % 4 != 0) sd.write(0);
        int ss = 28 + 4 * pool.size();
        ByteBuffer b = ByteBuffer.allocate(ss + sd.size()).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 1); b.putShort((short) 28); b.putInt(b.capacity());
        b.putInt(pool.size()); b.putInt(0); b.putInt(utf8 ? 0x100 : 0); b.putInt(ss); b.putInt(0);
        for (int o : offs) b.putInt(o);
        b.put(sd.toByteArray());
        return b.array();
    }

    static int intern(List<String> p, String s) { int i = p.indexOf(s); if (i < 0) { p.add(s); i = p.size() - 1; } return i; }
    static byte[] le32(int v) { return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)}; }
    static byte[] le16(int v) { return new byte[]{(byte) v, (byte) (v >> 8)}; }
    static void w(ByteArrayOutputStream o, byte[]... parts) { for (byte[] p : parts) o.write(p, 0, p.length); }
    static byte[] chunk(int type, int header, byte[]... payload) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : payload) o.write(p, 0, p.length);
        byte[] pl = o.toByteArray();
        ByteBuffer b = ByteBuffer.allocate(8 + pl.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) type); b.putShort((short) header); b.putInt(8 + pl.length); b.put(pl);
        return b.array();
    }

    /** Independent decoder: renders the XML as text lines, resolving every string via the pool. */
    static List<String> dump(byte[] d) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt(4) != d.length) throw new Exception("file size field " + b.getInt(4) + " != " + d.length);
        int p = 8;
        int sc = b.getInt(p + 8), flags = b.getInt(p + 16), ss = b.getInt(p + 20);
        boolean utf8 = (flags & 0x100) != 0;
        String[] pool = new String[sc];
        for (int i = 0; i < sc; i++) {
            int o = p + ss + b.getInt(p + 28 + 4 * i);
            if (utf8) {
                int cl = d[o] & 0xFF, q = o + 1;
                if ((cl & 0x80) != 0) q++;
                int bl = d[q++] & 0xFF;
                if ((bl & 0x80) != 0) bl = ((bl & 0x7F) << 8) | (d[q++] & 0xFF);
                pool[i] = new String(d, q, bl, "UTF-8");
            } else {
                int len = b.getShort(o) & 0xFFFF, q = o + 2;
                if ((len & 0x8000) != 0) { len = ((len & 0x7FFF) << 16) | (b.getShort(q) & 0xFFFF); q += 2; }
                pool[i] = new String(d, q, len * 2, "UTF-16LE");
            }
        }
        List<String> out = new ArrayList<String>();
        int off = p + b.getInt(p + 4);
        while (off < d.length) {
            int type = b.getShort(off) & 0xFFFF, size = b.getInt(off + 4);
            if (type == 0x0102) {
                StringBuilder sb = new StringBuilder("<" + pool[b.getInt(off + 20)]);
                int cnt = b.getShort(off + 28) & 0xFFFF;
                for (int i = 0; i < cnt; i++) {
                    int a = off + 36 + i * 20;
                    String val;
                    int t = d[a + 15] & 0xFF;
                    if (t == 3) {
                        int raw = b.getInt(a + 8), dat = b.getInt(a + 16);
                        if (raw != dat) throw new Exception("raw/data mismatch");
                        val = pool[dat];
                    } else val = "#" + t + ":" + b.getInt(a + 16);
                    sb.append(' ').append(pool[b.getInt(a + 4)]).append("=").append(val);
                }
                out.add(sb.append(">").toString());
            } else if (type == 0x0103) out.add("</>");
            off += size;
        }
        return out;
    }

    // ============================================================ manifest tests

    static byte[] sampleManifest(boolean utf8) {
        List<Object> ev = new ArrayList<Object>();
        ev.add(new El("manifest", new Attr("package", "com.old.app"), new Attr("versionCode", 0x10, 7)));
        ev.add(new El("uses-permission", new Attr("name", "android.permission.INTERNET"))); ev.add("/");
        ev.add(new El("uses-permission", new Attr("name", "com.old.app.permission.C2D_MESSAGE"))); ev.add("/");
        ev.add(new El("permission", new Attr("name", "com.old.app.permission.C2D_MESSAGE"))); ev.add("/");
        ev.add(new El("application", new Attr("label", 0x01, 0x7f010001), new Attr("name", ".App"), new Attr("allowBackup", 0x12, -1)));
        ev.add(new El("activity", new Attr("label", "Inner Screen"), new Attr("name", ".Inner"))); ev.add("/");
        ev.add(new El("activity", new Attr("label", 0x01, 0x7f010002), new Attr("name", "com.old.app.Main"), new Attr("taskAffinity", "com.old.app.task")));
        ev.add(new El("intent-filter"));
        ev.add(new El("action", new Attr("name", "android.intent.action.MAIN"))); ev.add("/");
        ev.add(new El("category", new Attr("name", "android.intent.category.LAUNCHER"))); ev.add("/");
        ev.add("/"); ev.add("/");
        ev.add(new El("service", new Attr("name", "Sync"), new Attr("process", ":bg"))); ev.add("/");
        ev.add(new El("service", new Attr("name", ".Priv"), new Attr("process", "com.old.app.priv"), new Attr("permission", "com.old.app.permission.C2D_MESSAGE"))); ev.add("/");
        ev.add(new El("provider", new Attr("name", ".Files"), new Attr("authorities", "com.old.app.fileprovider;third.party.auth"))); ev.add("/");
        ev.add("/"); // application
        ev.add("/"); // manifest
        return buildAxml(ev, utf8);
    }

    static void testManifest(boolean utf8) throws Exception {
        String tag = utf8 ? "[utf8] " : "[utf16] ";
        byte[] src = sampleManifest(utf8);
        check(ManifestPatcher.readPackage(src).equals("com.old.app"), tag + "readPackage");
        List<String> before = dump(src);

        ManifestPatcher.Options o = new ManifestPatcher.Options();
        o.oldPackage = "com.old.app";
        o.newPackage = "com.old.app.clone3";
        o.authoritySuffix = "clone3";
        o.label = "My Clone ✓";
        ManifestPatcher.Result r = ManifestPatcher.patch(src, o);
        List<String> after = dump(r.manifest);
        for (String s : after) System.out.println("      " + s);

        check(before.size() == after.size(), tag + "same element count");
        check(ManifestPatcher.readPackage(r.manifest).equals("com.old.app.clone3"), tag + "package replaced");
        check(after.contains("<manifest package=com.old.app.clone3 versionCode=#16:7>"), tag + "manifest attrs / int attr kept");
        check(after.contains("<uses-permission name=android.permission.INTERNET>"), tag + "system permission untouched");
        check(after.contains("<uses-permission name=com.old.app.clone3.permission.C2D_MESSAGE>"), tag + "custom uses-permission renamed");
        check(after.contains("<permission name=com.old.app.clone3.permission.C2D_MESSAGE>"), tag + "declared permission renamed");
        check(after.contains("<application label=My Clone ✓ name=com.old.app.App allowBackup=#18:-1>"), tag + "app label literal, class qualified to OLD package");
        check(after.contains("<activity label=Inner Screen name=com.old.app.Inner>"), tag + "non-launcher label kept, class qualified");
        check(after.contains("<activity label=My Clone ✓ name=com.old.app.Main taskAffinity=com.old.app.clone3.task>"), tag + "launcher label replaced, FQCN kept, affinity renamed");
        check(after.contains("<service name=com.old.app.Sync process=:bg>"), tag + "bare class name qualified, :process kept");
        check(after.contains("<service name=com.old.app.Priv process=com.old.app.clone3.priv permission=com.old.app.clone3.permission.C2D_MESSAGE>"), tag + "absolute process + permission renamed");
        check(after.contains("<provider name=com.old.app.Files authorities=com.old.app.clone3.fileprovider;third.party.auth.clone3>"), tag + "authorities: renamed or suffixed");
        check(r.labelPatched, tag + "labelPatched flag");
        // file is structurally valid: chunk walk ends exactly at EOF
        ByteBuffer b = ByteBuffer.wrap(r.manifest).order(ByteOrder.LITTLE_ENDIAN);
        int off = 8 + b.getInt(12), end = r.manifest.length;
        while (off < end) { int s = b.getInt(off + 4); if (s < 8) break; off += s; }
        check(off == end, tag + "chunks tile the file exactly");
        check((b.getInt(8 + 4) % 4) == 0, tag + "string pool 4-byte aligned");
    }

    static void testManifestEdgeCases() throws Exception {
        try {
            ManifestPatcher.Options o = new ManifestPatcher.Options();
            o.oldPackage = "a.b"; o.newPackage = "a.b.c";
            ManifestPatcher.patch(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}, o);
            check(false, "garbage rejected");
        } catch (java.io.IOException e) { check(true, "garbage rejected: " + e.getMessage()); }
        // no label option -> labels untouched
        ManifestPatcher.Options o = new ManifestPatcher.Options();
        o.oldPackage = "com.old.app"; o.newPackage = "com.old.app.c1";
        ManifestPatcher.Result r = ManifestPatcher.patch(sampleManifest(true), o);
        check(!r.labelPatched, "no label option -> label untouched");
        // long (>127 chars) label in UTF-8 pool
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) sb.append('x');
        o.label = sb.toString();
        r = ManifestPatcher.patch(sampleManifest(true), o);
        List<String> d = dump(r.manifest);
        check(d.get(7).contains("label=" + sb), "300-char label survives UTF-8 length encoding");
        r = ManifestPatcher.patch(sampleManifest(false), o);
        check(dump(r.manifest).get(7).contains("label=" + sb), "300-char label survives UTF-16 pool");
    }

    // ============================================================ zip + signer tests

    static void testZipAndSigner(String ksPath, String pw) throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "engine-test-" + System.nanoTime());
        dir.mkdirs();
        File orig = new File(dir, "orig.apk");
        Random rnd = new Random(42);
        byte[] arsc = new byte[5001]; rnd.nextBytes(arsc);
        byte[] so = new byte[3333]; rnd.nextBytes(so);
        byte[] dex = new byte[2500000]; rnd.nextBytes(dex);         // incompressible, > 2 chunks of 1 MiB
        byte[] txt = new byte[50000]; Arrays.fill(txt, (byte) 'a');
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(orig));
        put(z, "AndroidManifest.xml", sampleManifest(true), false);
        put(z, "classes.dex", dex, false);
        put(z, "resources.arsc", arsc, true);
        put(z, "lib/arm64-v8a/libfoo.so", so, true);
        put(z, "assets/a.txt", txt, false);
        put(z, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(), false);
        put(z, "META-INF/CERT.SF", "x".getBytes(), false);
        put(z, "META-INF/CERT.RSA", "x".getBytes(), false);
        put(z, "META-INF/services/foo", "keep".getBytes(), false);
        z.close();

        final ManifestPatcher.Options o = new ManifestPatcher.Options();
        o.oldPackage = "com.old.app"; o.newPackage = "com.old.app.clone1"; o.label = "Clone";
        File unsigned = new File(dir, "unsigned.apk");
        ApkRewriter.rewrite(orig, unsigned, new ApkRewriter.ManifestEditor() {
            public byte[] edit(byte[] m) throws java.io.IOException { return ManifestPatcher.patch(m, o).manifest; }
        });

        ZipFile zf = new ZipFile(unsigned);
        check(zf.getEntry("META-INF/CERT.RSA") == null && zf.getEntry("META-INF/MANIFEST.MF") == null && zf.getEntry("META-INF/CERT.SF") == null, "old signature files removed");
        check(zf.getEntry("META-INF/services/foo") != null, "other META-INF kept");
        check(Arrays.equals(read(zf, "classes.dex"), dex), "dex bytes identical (large, deflated)");
        check(Arrays.equals(read(zf, "resources.arsc"), arsc), "arsc bytes identical");
        check(Arrays.equals(read(zf, "lib/arm64-v8a/libfoo.so"), so), "so bytes identical");
        check(ManifestPatcher.readPackage(read(zf, "AndroidManifest.xml")).equals("com.old.app.clone1"), "manifest rewritten inside zip");
        check(zf.getEntry("resources.arsc").getMethod() == ZipEntry.STORED, "arsc stays STORED");
        zf.close();
        checkAlignment(unsigned);

        // keystore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        InputStream ksi = new FileInputStream(ksPath);
        ks.load(ksi, pw.toCharArray()); ksi.close();
        String alias = ks.aliases().nextElement();
        PrivateKey key = (PrivateKey) ks.getKey(alias, pw.toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

        File signed = new File(dir, "signed.apk");
        ApkV2Signer.sign(unsigned, signed, key, cert);
        verifyV2(signed, cert.getPublicKey());
        zf = new ZipFile(signed);
        check(Arrays.equals(read(zf, "classes.dex"), dex), "signed APK still opens as a normal zip");
        zf.close();
        checkAlignment(signed);

        // tamper test: flipping one byte in an entry must invalidate the digest
        byte[] all = java.nio.file.Files.readAllBytes(signed.toPath());
        all[100] ^= 1;
        File bad = new File(dir, "bad.apk");
        java.nio.file.Files.write(bad.toPath(), all);
        boolean threw = false;
        try { verifyV2(bad, cert.getPublicKey()); } catch (Exception e) { threw = true; }
        check(threw || lastVerifyFailed, "tampered APK is rejected by verifier");
        lastVerifyFailed = false;
    }

    static boolean lastVerifyFailed = false;

    static void put(ZipOutputStream z, String name, byte[] d, boolean stored) throws Exception {
        ZipEntry e = new ZipEntry(name);
        if (stored) {
            e.setMethod(ZipEntry.STORED); e.setSize(d.length); e.setCompressedSize(d.length);
            CRC32 c = new CRC32(); c.update(d); e.setCrc(c.getValue());
        }
        z.putNextEntry(e); z.write(d); z.closeEntry();
    }

    static byte[] read(ZipFile zf, String n) throws Exception {
        InputStream in = zf.getInputStream(zf.getEntry(n));
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int r;
        while ((r = in.read(b)) > 0) o.write(b, 0, r);
        in.close();
        return o.toByteArray();
    }

    /** Parses the central directory by hand and checks where each stored entry's data begins. */
    static void checkAlignment(File f) throws Exception {
        byte[] a = java.nio.file.Files.readAllBytes(f.toPath());
        ByteBuffer b = ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN);
        int eocd = a.length - 22;
        while (b.getInt(eocd) != 0x06054b50) eocd--;
        int n = b.getShort(eocd + 10) & 0xFFFF;
        int cd = b.getInt(eocd + 16);
        boolean ok = true;
        String info = "";
        for (int i = 0; i < n; i++) {
            int method = b.getShort(cd + 10) & 0xFFFF;
            int nl = b.getShort(cd + 28) & 0xFFFF, el = b.getShort(cd + 30) & 0xFFFF, cl = b.getShort(cd + 32) & 0xFFFF;
            int lho = b.getInt(cd + 42);
            String name = new String(a, cd + 46, nl, "UTF-8");
            int lnl = b.getShort(lho + 26) & 0xFFFF, lel = b.getShort(lho + 28) & 0xFFFF;
            long data = lho + 30 + lnl + lel;
            if (method == 0) {
                int al = name.endsWith(".so") ? 4096 : 4;
                if (data % al != 0) { ok = false; info += " " + name + "@" + data; }
            }
            cd += 46 + nl + el + cl;
        }
        check(ok, "stored entries aligned in " + f.getName() + info);
    }

    /** Verifies the v2 block the way a platform would: locate block, re-digest, check signature. */
    static void verifyV2(File f, PublicKey expectedKey) throws Exception {
        byte[] a = java.nio.file.Files.readAllBytes(f.toPath());
        ByteBuffer b = ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN);
        int eocd = a.length - 22;
        while (b.getInt(eocd) != 0x06054b50) eocd--;
        int cdOff = b.getInt(eocd + 16);
        if (!new String(a, cdOff - 16, 16).equals("APK Sig Block 42")) { check(false, "magic present"); lastVerifyFailed = true; return; }
        long blockSize = b.getLong(cdOff - 24);
        int blockStart = (int) (cdOff - blockSize - 8);
        if (b.getLong(blockStart) != blockSize) { check(false, "block size fields match"); lastVerifyFailed = true; return; }
        // pairs
        int p = blockStart + 8, pairsEnd = cdOff - 24;
        byte[] value = null;
        while (p < pairsEnd) {
            long len = b.getLong(p); int id = b.getInt(p + 8);
            if (id == 0x7109871a) { value = Arrays.copyOfRange(a, p + 12, (int) (p + 8 + len)); }
            p += 8 + (int) len;
        }
        if (p != pairsEnd || value == null) { check(false, "pairs well-formed"); lastVerifyFailed = true; return; }
        ByteBuffer v = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        int signersLen = v.getInt();
        if (signersLen != value.length - 4) { check(false, "signers length"); lastVerifyFailed = true; return; }
        int signerLen = v.getInt();
        int signerStart = v.position();
        int sdLen = v.getInt();
        byte[] signedData = new byte[sdLen]; v.get(signedData);
        int sigsLen = v.getInt();
        ByteBuffer sg = ByteBuffer.wrap(sigsLen > 0 ? slice(v, sigsLen) : new byte[0]).order(ByteOrder.LITTLE_ENDIAN);
        int pkLen = v.getInt();
        byte[] pk = new byte[pkLen]; v.get(pk);
        if (v.position() - signerStart != signerLen) { check(false, "signer length"); lastVerifyFailed = true; return; }
        sg.getInt(); int algo = sg.getInt(); int sl = sg.getInt(); byte[] sig = new byte[sl]; sg.get(sig);
        boolean keyOk = Arrays.equals(pk, expectedKey.getEncoded());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initVerify(expectedKey); s.update(signedData);
        boolean sigOk = s.verify(sig);
        // digest from signed data
        ByteBuffer sd = ByteBuffer.wrap(signedData).order(ByteOrder.LITTLE_ENDIAN);
        sd.getInt(); sd.getInt(); int dAlgo = sd.getInt(); int dl = sd.getInt(); byte[] claimed = new byte[dl]; sd.get(claimed);
        // recompute per spec: sections = [0,blockStart) [cdOff,eocd) [eocd,end) with eocd.cdOffset = blockStart
        byte[] copy = a.clone();
        ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN).putInt(eocd + 16, blockStart);
        int cdSize = b.getInt(eocd + 12);
        byte[] stitched = new byte[blockStart + cdSize + (a.length - eocd)];
        System.arraycopy(copy, 0, stitched, 0, blockStart);
        System.arraycopy(copy, cdOff, stitched, blockStart, cdSize);
        System.arraycopy(copy, eocd, stitched, blockStart + cdSize, a.length - eocd);
        int[][] secs = {{0, blockStart}, {blockStart, blockStart + cdSize}, {blockStart + cdSize, stitched.length}};
        int chunks = 0;
        for (int[] x : secs) chunks += (x[1] - x[0] + (1 << 20) - 1) >> 20;
        MessageDigest top = MessageDigest.getInstance("SHA-256");
        top.update((byte) 0x5a); top.update(le32(chunks));
        for (int[] x : secs) for (int q = x[0]; q < x[1]; q += 1 << 20) {
            int len = Math.min(1 << 20, x[1] - q);
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            d.update((byte) 0xa5); d.update(le32(len)); d.update(stitched, q, len);
            top.update(d.digest());
        }
        boolean digestOk = Arrays.equals(top.digest(), claimed);
        boolean all = keyOk && sigOk && digestOk && algo == 0x0103 && dAlgo == 0x0103;
        if (all) check(true, "v2 block valid in " + f.getName() + " (key, signature, digest over " + chunks + " chunks)");
        else { lastVerifyFailed = true; if (!f.getName().equals("bad.apk")) check(false, "v2 block INVALID key=" + keyOk + " sig=" + sigOk + " digest=" + digestOk); }
    }

    static byte[] slice(ByteBuffer v, int n) { byte[] x = new byte[n]; v.get(x); return x; }
}
