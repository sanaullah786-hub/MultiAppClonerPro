package com.multiappcloner.pro;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.multiappcloner.pro.engine.ApkRewriter;
import com.multiappcloner.pro.engine.ApkV2Signer;
import com.multiappcloner.pro.engine.ManifestPatcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Real-clone pipeline (repackaging):
 *   1. copy the installed base APK, 2. rewrite the binary manifest to a new package name,
 *   3. drop the old signature, re-zip with alignment, 4. sign (APK v2) with the on-device key,
 *   5. install through PackageInstaller (the user confirms on the system dialog).
 * The clone is a separate Android app with its own data, icon-launchable like any other app.
 */
public class ApkCloningEngine {
    public static class Result {
        public final boolean success;
        public final String message;
        public Result(boolean s, String m) { success = s; message = m; }
    }

    /** Hard blockers: with any of these the app is not cloned. */
    public static List<String> blockers(ApkInspector.Report r) {
        List<String> out = new ArrayList<String>();
        if (r.system) out.add("System app: cannot be repackaged or installed under a new package name.");
        if (r.sharedUserId) out.add("Uses sharedUserId: cannot be given a new package identity safely.");
        if (r.splitApks) out.add("Split APKs (app bundle): the splits would need merging first, which is not supported.");
        if (r.deviceAdmin) out.add("Registers a device-administration receiver: protected app, will not be modified.");
        if (r.accessibility) out.add("Provides an accessibility service: security-sensitive, will not be modified.");
        if (r.vpnService) out.add("Provides a VPN service: security-sensitive, will not be modified.");
        return out;
    }

    /** Things that may stop the clone from working at run time even though it installs. */
    public static List<String> warnings(ApkInspector.Report r) {
        List<String> out = new ArrayList<String>();
        if (r.providers > 0) out.add(r.providers + " content provider(s): authorities are rewritten automatically.");
        if (r.nativeLibs) out.add("Native libraries: usually fine, but code that hard-codes the original package name will fail.");
        out.add("The clone gets a new package name and signature. Apps that check their signature, use Google Sign-In / Firebase / Play Integrity "
            + "or licence checks tied to the original package may start but fail to log in or run.");
        return out;
    }

    /** Kept for the compatibility screen: blockers first, then warnings. */
    public static List<String> reasons(ApkInspector.Report r) {
        List<String> out = blockers(r);
        out.addAll(warnings(r));
        return out;
    }

    public static String clonePackageFor(String pkg, long cloneId) {
        return pkg + ".clone" + cloneId;
    }

    /**
     * Builds and signs the clone APK, then commits it to the system installer. Blocking: call off the main thread.
     * Success here means "handed to the installer"; the final outcome arrives in {@link InstallResultReceiver}.
     */
    public static Result cloneReal(Context c, long cloneId, String pkg, String cloneName) {
        File dir = new File(c.getCacheDir(), "clones/" + cloneId);
        File unsigned = new File(dir, "unsigned.apk");
        File signed = new File(dir, "signed.apk");
        try {
            ApkInspector.Report rep = ApkInspector.inspect(c, pkg);
            if (rep.error != null) return new Result(false, rep.error);
            List<String> b = blockers(rep);
            if (!b.isEmpty()) return new Result(false, b.get(0));
            if (!CloneInstaller.canInstall(c)) return new Result(false, "Allow \"Install unknown apps\" for this app first.");

            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            File base = new File(ai.sourceDir);
            if (!base.canRead()) return new Result(false, "The app's APK is not readable.");
            if (!dir.isDirectory() && !dir.mkdirs()) return new Result(false, "Could not create a working folder.");

            final ManifestPatcher.Options o = new ManifestPatcher.Options();
            o.oldPackage = pkg;
            o.newPackage = clonePackageFor(pkg, cloneId);
            o.authoritySuffix = "clone" + cloneId;
            o.label = cloneName;

            ApkRewriter.rewrite(base, unsigned, new ApkRewriter.ManifestEditor() {
                public byte[] edit(byte[] m) throws IOException {
                    String actual = ManifestPatcher.readPackage(m);
                    if (actual != null && !actual.equals(o.oldPackage)) o.oldPackage = actual;
                    return ManifestPatcher.patch(m, o).manifest;
                }
            });
            CloneKeys.Keys k = CloneKeys.get();
            ApkV2Signer.sign(unsigned, signed, k.key, k.cert);
            CloneInstaller.install(c, signed, o.newPackage, cloneId);
            return new Result(true, "Sent to the system installer as " + o.newPackage + ". Confirm the install dialog.");
        } catch (PackageManager.NameNotFoundException e) {
            return new Result(false, "The original app is no longer installed.");
        } catch (Throwable t) {
            return new Result(false, "Cloning failed: " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        } finally {
            unsigned.delete();
            signed.delete();
        }
    }
}
