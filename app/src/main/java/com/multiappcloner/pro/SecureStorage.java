package com.multiappcloner.pro;

import android.annotation.TargetApi;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-256-GCM with a non-exportable Android Keystore key. Requires Android 6.0 (API 23)+. */
@TargetApi(23)
public class SecureStorage {
    private static final String ALIAS = "mac_pro_secret_key";
    private static final String PROVIDER = "AndroidKeyStore";

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 23;
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
            return kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    /** Returns Base64(iv + ciphertext) or null on failure / unsupported. */
    public static String encrypt(String plain) {
        if (!isSupported() || plain == null) return null;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return Base64.encodeToString(all, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    public static String decrypt(String enc) {
        if (!isSupported() || enc == null || enc.length() == 0) return null;
        try {
            byte[] all = Base64.decode(enc, Base64.NO_WRAP);
            if (all.length < 13) return null;
            byte[] iv = new byte[12];
            byte[] ct = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, ct, 0, ct.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static void deleteKey() {
        if (!isSupported()) return;
        try {
            KeyStore ks = KeyStore.getInstance(PROVIDER);
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) {
        }
    }
}
