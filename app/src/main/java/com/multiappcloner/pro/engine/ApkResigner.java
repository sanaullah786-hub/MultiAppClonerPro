package com.multiappcloner.pro.engine;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Signs an APK that may already carry a signature (for example one produced by a merge tool).
 * The APK is first copied into a fresh, aligned zip without any old signature files or signing block,
 * then signed with APK Signature Scheme v2.
 */
public final class ApkResigner {
    public static void resign(File in, File out, PrivateKey key, X509Certificate cert) throws Exception {
        File aligned = new File(out.getPath() + ".aligned");
        try {
            ApkRewriter.rewrite(in, aligned, new ApkRewriter.ManifestEditor() {
                public byte[] edit(byte[] original) throws IOException { return original; }
            });
            ApkV2Signer.sign(aligned, out, key, cert);
        } finally {
            aligned.delete();
        }
    }
}
