import com.multiappcloner.pro.diag.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Plain-JVM tests for the diagnostics classes (no Android needed). */
public class DiagTest {
    static int failed = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failed++;
    }

    public static void main(String[] a) throws Exception {
        // ---- Redactor
        String r = Redactor.redact("login user=bob@example.com password=hunter2 token: abc123 Authorization: Bearer eyJhbGciOi.eyJzdWIi.sig");
        check(!r.contains("bob@example.com") && !r.contains("hunter2") && !r.contains("abc123") && !r.contains("eyJhbGciOi"), "credentials and emails removed: " + r);
        check(Redactor.redact("call +92 300 1234567 now").contains("[phone]"), "phone number removed");
        check(Redactor.redact("GET /x?token=AAAA1111&a=b").contains("token=[redacted]&a=b"), "url token removed, other params kept");
        String secret = "AbCdEf0123456789AbCdEf0123456789XyZ";
        check(Redactor.redact("key " + secret).contains("[secret]"), "long random secret removed");
        String keep = "E/AndroidRuntime: java.lang.ClassCastException: java.lang.Integer cannot be cast to android.app.SyncNotedAppOp at top.niunaijun.blackbox.fake.service.IAppOpsManagerProxy.invoke(IAppOpsManagerProxy.java:88)";
        check(Redactor.redact(keep).equals(keep), "stack trace text is not damaged");
        check(Redactor.redact("12-01 10:22:33.456  1234  5678 E Tag: x").contains("10:22:33.456"), "timestamps and pids are kept");
        check(Redactor.redact("versionCode 2024101500 sdk 34").contains("2024101500"), "plain numbers without separators are kept");
        check(Redactor.redact(null) == null && Redactor.redact("").equals(""), "null/empty safe");
        check(Redactor.redact("android_id=9774d56d682e549c ok").contains("android_id=[redacted]"), "device identifiers removed");

        // ---- LogAnalyzer
        List<String> lines = new ArrayList<String>(Arrays.asList(
            "E/art: dlopen failed: library \"libfoo.so\" not found",
            "java.lang.UnsatisfiedLinkError: couldn't find \"libbar.so\"",
            "E/AndroidRuntime: FATAL EXCEPTION: main",
            "W/ActivityManager: Process com.x:p0 (pid 123) has died",
            "E/x: java.lang.OutOfMemoryError: Failed to allocate a 1048576 byte allocation",
            "W/Chrome: requires com.google.android.trichromelibrary_700",
            "I/VirtualEngine: just info",
            "E/x: INSTALL_PARSE_FAILED_NO_CERTIFICATES"));
        List<LogAnalyzer.Finding> f = LogAnalyzer.analyze(lines);
        Set<String> cats = new LinkedHashSet<String>();
        for (LogAnalyzer.Finding x : f) cats.add(x.category);
        check(cats.contains("MISSING_LIBRARY"), "detects missing library");
        check(cats.contains("APP_CRASH") && cats.contains("PROCESS_DIED") && cats.contains("MEMORY"), "detects crash, died process, OOM");
        check(cats.contains("SHARED_LIBRARY"), "detects Trichrome shared-library dependency");
        check(cats.contains("INSTALL_SIGNATURE"), "detects signature install error");
        check(LogAnalyzer.analyze(Arrays.asList("I/VirtualEngine: just info", "D/BlackBox: started")).isEmpty(), "harmless lines give no finding");
        int mc = 0; for (LogAnalyzer.Finding x : f) if (x.category.equals("MISSING_LIBRARY")) mc = x.count;
        check(mc == 2, "counts repeated findings (2 missing-lib lines)");
        check(LogAnalyzer.format(f).contains("[MISSING_LIBRARY]"), "format names the category");

        // ---- DiagStore
        File dir = new File(System.getProperty("java.io.tmpdir"), "diagtest" + System.nanoTime());
        DiagStore s = new DiagStore(dir);
        s.record("INSTALL", "failed for user bob@example.com\nsecond line");
        s.record("CRASH", "boom");
        List<String> all = s.read(null, 10);
        check(all.size() == 2, "two events stored");
        check(!all.get(0).contains("bob@example.com"), "stored events are redacted");
        check(all.get(0).contains("second line"), "multi-line message survives");
        check(s.read("CRASH", 10).size() == 1 && s.read("INSTALL", 10).size() == 1, "filter by category");
        for (int i = 0; i < 3000; i++) s.record("ENGINE", "filler line number " + i + " xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
        long total = new File(dir, "events.log").length() + new File(dir, "events.old.log").length();
        check(total < 700 * 1024, "log is rotated and stays small (" + total + " bytes)");
        check(s.read(null, 5).size() == 5, "read limits the number of lines");
        s.clear();
        check(s.read(null, 10).isEmpty(), "clear empties the log");

        // ---- AbiScan
        File apk = File.createTempFile("abi", ".apk");
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(apk));
        for (String n : new String[]{"lib/armeabi-v7a/libgame.so", "lib/armeabi-v7a/libb.so", "classes.dex"}) {
            z.putNextEntry(new ZipEntry(n)); z.write(1); z.closeEntry();
        }
        z.close();
        Set<String> abis = AbiScan.abis(apk);
        check(abis.size() == 1 && abis.contains("armeabi-v7a"), "reads ABIs from lib/: " + abis);
        check(AbiScan.incompatibility(abis, new String[]{"arm64-v8a"}) != null, "32-bit-only app on 64-bit-only phone is flagged");
        check(AbiScan.incompatibility(abis, new String[]{"arm64-v8a", "armeabi-v7a"}) == null, "32-bit app on phone with 32-bit support is fine");
        check(AbiScan.incompatibility(new LinkedHashSet<String>(), new String[]{"arm64-v8a"}) == null, "pure-Java app is fine");
        apk.delete();
        File sl = File.createTempFile("static", ".apk");
        ZipOutputStream z2 = new ZipOutputStream(new FileOutputStream(sl));
        z2.putNextEntry(new ZipEntry("AndroidManifest.xml"));
        z2.write("xxxx".getBytes("UTF-8")); z2.write("uses-static-library".getBytes("UTF-16LE")); z2.write(7);
        z2.closeEntry(); z2.close();
        check(AbiScan.usesStaticLibrary(sl), "detects uses-static-library in a manifest (Trichrome case)");
        File plain = File.createTempFile("plain", ".apk");
        ZipOutputStream z3 = new ZipOutputStream(new FileOutputStream(plain));
        z3.putNextEntry(new ZipEntry("AndroidManifest.xml")); z3.write("hello uses-library".getBytes("UTF-16LE")); z3.closeEntry(); z3.close();
        check(!AbiScan.usesStaticLibrary(plain), "normal uses-library is not flagged");
        check(!AbiScan.usesStaticLibrary(new File("/nonexistent.apk")), "missing file is not flagged");
        sl.delete(); plain.delete();

        // ---- UserIds (a deleted clone's id must never come back)
        check(UserIds.next(-1, -1) == 0, "first user id is 0");
        check(UserIds.next(3, 3) == 4, "next after 3 is 4");
        check(UserIds.next(2, 5) == 6, "id 5 was used once and deleted: next is 6, not 3");
        check(UserIds.next(7, 4) == 8, "database ahead of counter is respected");

        System.out.println(failed == 0 ? "\nALL DIAGNOSTICS TESTS PASSED" : "\n" + failed + " DIAGNOSTICS TESTS FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
