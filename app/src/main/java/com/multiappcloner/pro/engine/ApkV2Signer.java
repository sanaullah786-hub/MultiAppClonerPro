package com.multiappcloner.pro.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;

/**
 * Minimal APK Signature Scheme v2 signer (RSA PKCS#1 v1.5 with SHA-256, algorithm id 0x0103).
 * Input must be a freshly written, unsigned APK (no existing signing block).
 * v2-only signing is installable on Android 7.0 (API 24) and newer, which is this app's minSdk.
 */
public final class ApkV2Signer {

    private static final int V2_BLOCK_ID = 0x7109871a;
    private static final int ALGO_RSA_PKCS1_SHA256 = 0x0103;
    private static final int CHUNK = 1024 * 1024;
    private static final byte[] MAGIC = "APK Sig Block 42".getBytes();

    public static void sign(File unsigned, File out, PrivateKey key, X509Certificate cert) throws Exception {
        byte[] apk = readFile(unsigned);
        int eocd = findEocd(apk);
        ByteBuffer bb = ByteBuffer.wrap(apk).order(ByteOrder.LITTLE_ENDIAN);
        long cdOffset = bb.getInt(eocd + 16) & 0xFFFFFFFFL;
        long cdSize = bb.getInt(eocd + 12) & 0xFFFFFFFFL;
        if (cdOffset + cdSize != eocd) throw new IOException("Unexpected data between central directory and EOCD.");
        if (cdOffset >= 16 && new String(apk, (int) cdOffset - 16, 16).equals("APK Sig Block 42"))
            throw new IOException("APK already has a signing block.");

        byte[] digest = topLevelDigest(apk, (int) cdOffset, eocd);

        // signed data
        byte[] digestEntry = concat(u32(ALGO_RSA_PKCS1_SHA256), lp(digest));
        byte[] digests = lp(lp(digestEntry));
        byte[] certs = lp(lp(cert.getEncoded()));
        byte[] attrs = lp(new byte[0]);
        byte[] signedData = concat(digests, certs, attrs);

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(signedData);
        byte[] sigBytes = sig.sign();
        byte[] sigEntry = concat(u32(ALGO_RSA_PKCS1_SHA256), lp(sigBytes));
        byte[] signatures = lp(lp(sigEntry));
        byte[] publicKey = lp(cert.getPublicKey().getEncoded());

        byte[] signer = lp(concat(lp(signedData), signatures, publicKey));
        byte[] v2Value = lp(signer);

        // signing block
        long pairLen = 4 + v2Value.length;
        long blockSizeField = 8 + pairLen + 8 + 16; // pair (len + id/value) + trailing size + magic
        ByteBuffer blk = ByteBuffer.allocate((int) (blockSizeField + 8)).order(ByteOrder.LITTLE_ENDIAN);
        blk.putLong(blockSizeField);
        blk.putLong(pairLen);
        blk.putInt(V2_BLOCK_ID);
        blk.put(v2Value);
        blk.putLong(blockSizeField);
        blk.put(MAGIC);
        byte[] block = blk.array();

        // assemble: [entries][signing block][central directory][EOCD with shifted cd offset]
        byte[] eocdBytes = new byte[apk.length - eocd];
        System.arraycopy(apk, eocd, eocdBytes, 0, eocdBytes.length);
        ByteBuffer.wrap(eocdBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(16, (int) (cdOffset + block.length));

        java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
        try {
            fos.write(apk, 0, (int) cdOffset);
            fos.write(block);
            fos.write(apk, (int) cdOffset, (int) cdSize);
            fos.write(eocdBytes);
            fos.getFD().sync();
        } finally {
            fos.close();
        }
    }

    // ------------------------------------------------------------------ digest

    private static byte[] topLevelDigest(byte[] apk, int cdOffset, int eocd) throws Exception {
        int[][] sections = {{0, cdOffset}, {cdOffset, eocd}, {eocd, apk.length}};
        int chunks = 0;
        for (int[] s : sections) chunks += (s[1] - s[0] + CHUNK - 1) / CHUNK;
        MessageDigest top = MessageDigest.getInstance("SHA-256");
        top.update((byte) 0x5a);
        top.update(u32(chunks));
        for (int[] s : sections) {
            for (int p = s[0]; p < s[1]; p += CHUNK) {
                int len = Math.min(CHUNK, s[1] - p);
                MessageDigest d = MessageDigest.getInstance("SHA-256");
                d.update((byte) 0xa5);
                d.update(u32(len));
                d.update(apk, p, len);
                top.update(d.digest());
            }
        }
        return top.digest();
    }

    // ------------------------------------------------------------------ helpers

    private static int findEocd(byte[] a) throws IOException {
        int min = Math.max(0, a.length - 22 - 65535);
        for (int i = a.length - 22; i >= min; i--) {
            if (a[i] == 0x50 && a[i + 1] == 0x4b && a[i + 2] == 0x05 && a[i + 3] == 0x06) {
                int commentLen = (a[i + 20] & 0xFF) | ((a[i + 21] & 0xFF) << 8);
                if (i + 22 + commentLen == a.length) return i;
            }
        }
        throw new IOException("ZIP end-of-central-directory not found.");
    }

    private static byte[] u32(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static byte[] lp(byte[] d) {
        return concat(u32(d.length), d);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    private static byte[] readFile(File f) throws IOException {
        RandomAccessFile r = new RandomAccessFile(f, "r");
        try {
            byte[] b = new byte[(int) r.length()];
            r.readFully(b);
            return b;
        } finally {
            r.close();
        }
    }
}
