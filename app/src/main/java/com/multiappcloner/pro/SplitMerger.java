package com.multiappcloner.pro;

import android.util.Log;

import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.multiappcloner.pro.engine.ApkV2Signer;

/**
 * Merges a base APK and its split APKs (config / feature splits) into one installable APK, because the
 * virtual engine can only load a single APK. The merged file is signed with this app's own key.
 * Returns null on any failure so callers can fall back to the unmerged base APK.
 */
public final class SplitMerger {
    private static final String TAG = "SplitMerger";

    public static File merge(List<File> apks, File workDir, String outName) {
        File dir = new File(workDir, "merge_in");
        File raw = new File(workDir, outName + "_merged_raw.apk");
        File out = new File(workDir, outName + "_merged.apk");
        try {
            deleteTree(dir);
            dir.mkdirs();
            int i = 0;
            for (File f : apks) copy(f, new File(dir, "m" + (i++) + ".apk"));
            ApkBundle bundle = new ApkBundle();
            bundle.loadApkDirectory(dir);
            ApkModule merged = bundle.mergeModules();
            merged.writeApk(raw);
            merged.close();
            bundle.close();
            CloneKeys.Keys k = CloneKeys.get();
            ApkV2Signer.sign(raw, out, k.key, k.cert);
            return out;
        } catch (Throwable t) {
            Log.w(TAG, "merge failed: " + t, t);
            out.delete();
            return null;
        } finally {
            raw.delete();
            deleteTree(dir);
        }
    }

    private static void deleteTree(File f) {
        File[] c = f.listFiles();
        if (c != null) for (File x : c) deleteTree(x);
        f.delete();
    }

    private static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream os = new FileOutputStream(dst);
        try {
            byte[] b = new byte[64 * 1024];
            int r;
            while ((r = in.read(b)) > 0) os.write(b, 0, r);
        } finally {
            in.close();
            os.close();
        }
    }
}
