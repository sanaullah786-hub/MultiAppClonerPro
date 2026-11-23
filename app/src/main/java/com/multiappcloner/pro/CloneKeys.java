package com.multiappcloner.pro;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;

import javax.security.auth.x500.X500Principal;

/**
 * Signing identity for cloned APKs. A 2048-bit RSA key is generated once, inside the Android Keystore
 * of this device, and never leaves it. Every clone made on this device is signed with it, which is what
 * lets Android accept the rewritten APK. Removing this app removes the key.
 */
public final class CloneKeys {
    private static final String ALIAS = "mac_clone_signing_key";

    public static final class Keys {
        public PrivateKey key;
        public X509Certificate cert;
    }

    public static synchronized Keys get() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) generate();
        Keys k = new Keys();
        k.key = (PrivateKey) ks.getKey(ALIAS, null);
        k.cert = (X509Certificate) ks.getCertificate(ALIAS);
        if (k.key == null || k.cert == null) throw new IllegalStateException("Signing key is unavailable.");
        return k;
    }

    private static void generate() throws Exception {
        Calendar from = Calendar.getInstance();
        from.add(Calendar.DAY_OF_YEAR, -1);
        Calendar to = Calendar.getInstance();
        to.add(Calendar.YEAR, 30);
        KeyPairGenerator g = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        g.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(new X500Principal("CN=Multi App Cloner Pro, O=Local clone signer"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(from.getTime())
            .setCertificateNotAfter(to.getTime())
            .build());
        g.generateKeyPair();
    }
}
