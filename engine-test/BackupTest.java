import com.multiappcloner.pro.backup.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

public class BackupTest {
    static int failed = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS  " : "FAIL  ") + what); if (!ok) failed++; }

    static void write(File f, String s) throws Exception { f.getParentFile().mkdirs(); Files.write(f.toPath(), s.getBytes("UTF-8")); }
    static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), "UTF-8"); }

    public static void main(String[] a) throws Exception {
        File t = Files.createTempDirectory("bk").toFile();
        File data = new File(t, "data"), ext = new File(t, "ext");
        write(new File(data, "shared_prefs/login.xml"), "<user>alice</user>");
        write(new File(data, "databases/main.db"), "SQLITE-ALICE");
        write(new File(data, "name with space/100%\nodd.txt"), "odd name ok");
        write(new File(ext, "files/doc.txt"), "external");
        Map<String, File> roots = new LinkedHashMap<String, File>();
        roots.put("data", data); roots.put("ext", ext); roots.put("de", new File(t, "de-missing"));

        // create + verify
        File bk = new File(t, "a.mbk");
        final long[] last = {0};
        BackupEngine.Manifest m = BackupEngine.create(bk, "com.x.app", 3, "1.2.3", roots, new BackupEngine.Progress() { public void onProgress(long d, long tt) { last[0] = d; } });
        check(m.entries.size() == 4 && m.roots.size() == 3, "backup holds 4 files and 3 folders (one missing folder recorded)");
        check(last[0] == m.totalBytes(), "progress reaches 100%");
        BackupEngine.Manifest v = BackupEngine.verify(bk, null);
        check(v.packageName.equals("com.x.app") && v.userId == 3 && v.appVersion.equals("1.2.3") && v.format == 1, "manifest: package, user, version, format");
        check(!new File(t, "a.mbk.part").exists(), "no temporary .part file left");

        // damage detection
        File bad = new File(t, "bad.mbk");
        ZipFile zb = new ZipFile(bk);
        ZipOutputStream zbo = new ZipOutputStream(new FileOutputStream(bad));
        for (Enumeration<? extends ZipEntry> en = zb.entries(); en.hasMoreElements();) {
            ZipEntry e = en.nextElement(); zbo.putNextEntry(new ZipEntry(e.getName()));
            InputStream in = zb.getInputStream(e); ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); in.close();
            byte[] c = bo.toByteArray();
            if (e.getName().equals("data/databases/main.db")) c[0] ^= 0x55; // same size, different content
            zbo.write(c); zbo.closeEntry();
        }
        zbo.close(); zb.close();
        boolean threw = false;
        try { BackupEngine.verify(bad, null); } catch (IOException e) { threw = true; }
        check(threw, "a damaged backup is rejected");
        File junk = new File(t, "junk.mbk"); write(junk, "not a zip");
        threw = false; try { BackupEngine.verify(junk, null); } catch (IOException e) { threw = e.getMessage().contains("not a valid backup"); }
        check(threw, "a non-zip file is rejected with a readable message");
        File nomanifest = new File(t, "nm.mbk");
        ZipOutputStream z0 = new ZipOutputStream(new FileOutputStream(nomanifest)); z0.putNextEntry(new ZipEntry("data/x")); z0.write(1); z0.closeEntry(); z0.close();
        threw = false; try { BackupEngine.verify(nomanifest, null); } catch (IOException e) { threw = e.getMessage().contains("no manifest"); }
        check(threw, "a zip without manifest is rejected");

        // extra file smuggled into a valid backup
        File extra = new File(t, "extra.mbk");
        ZipFile zin = new ZipFile(bk);
        ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(extra));
        for (Enumeration<? extends ZipEntry> en = zin.entries(); en.hasMoreElements();) {
            ZipEntry e = en.nextElement(); zo.putNextEntry(new ZipEntry(e.getName()));
            InputStream in = zin.getInputStream(e); byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) zo.write(b, 0, n); in.close(); zo.closeEntry();
        }
        zo.putNextEntry(new ZipEntry("data/evil.txt")); zo.write(9); zo.closeEntry(); zo.close(); zin.close();
        threw = false; try { BackupEngine.verify(extra, null); } catch (IOException e) { threw = e.getMessage().contains("unexpected"); }
        check(threw, "an extra, unlisted file makes the backup invalid");

        // restore replaces data completely (new login appears, files created later vanish)
        write(new File(data, "shared_prefs/login.xml"), "<user>BOB-NEW-LOGIN</user>");
        write(new File(data, "later.txt"), "created after backup");
        write(new File(t, "de-missing/leftover.txt"), "should be removed");
        BackupEngine.restore(bk, "com.x.app", roots, null);
        check(read(new File(data, "shared_prefs/login.xml")).equals("<user>alice</user>"), "restore brings back the backed-up login data");
        check(!new File(data, "later.txt").exists(), "files created after the backup are gone (exact state restored)");
        check(read(new File(data, "name with space/100%\nodd.txt")).equals("odd name ok"), "names with spaces, % and newlines survive");
        check(!new File(t, "de-missing/leftover.txt").exists(), "a folder that was empty at backup time is emptied");
        check(!new File(t, "data.restore_old").exists() && !new File(t, "data.restore_tmp").exists(), "no temporary folders left");

        // wrong package / unknown root: data untouched
        write(new File(data, "marker.txt"), "keep me");
        threw = false; try { BackupEngine.restore(bk, "com.other.app", roots, null); } catch (IOException e) { threw = e.getMessage().contains("not com.other.app"); }
        check(threw && new File(data, "marker.txt").exists(), "restore into another package is refused and data is untouched");
        Map<String, File> partial = new LinkedHashMap<String, File>(); partial.put("data", data);
        threw = false; try { BackupEngine.restore(bk, "com.x.app", partial, null); } catch (IOException e) { threw = true; }
        check(threw && new File(data, "marker.txt").exists(), "restore without a target for every folder is refused, data untouched");
        threw = false; try { BackupEngine.restore(bad, "com.x.app", roots, null); } catch (IOException e) { threw = true; }
        check(threw && new File(data, "marker.txt").exists(), "restore from a damaged backup never touches existing data");

        // zip-slip: hand-made backup whose manifest points outside
        File slip = new File(t, "slip.mbk");
        ZipOutputStream zs = new ZipOutputStream(new FileOutputStream(slip));
        byte[] payload = "x".getBytes();
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        StringBuilder hx = new StringBuilder(); for (byte x : md.digest(payload)) hx.append(String.format("%02x", x & 0xff));
        zs.putNextEntry(new ZipEntry("data/../../escape.txt")); zs.write(payload); zs.closeEntry();
        zs.putNextEntry(new ZipEntry("MANIFEST"));
        zs.write(("MAC-BACKUP 1\npackage=com.x.app\nuser=0\ncreated=1\napp=\nroot=data\nentry=" + hx + " 1 data/../../escape.txt\n").getBytes());
        zs.closeEntry(); zs.close();
        threw = false; try { BackupEngine.restore(slip, "com.x.app", roots, null); } catch (IOException e) { threw = e.getMessage().contains("Unsafe"); }
        check(threw && !new File(t.getParentFile(), "escape.txt").exists(), "path traversal (zip-slip) is rejected");

        // symbolic links are not followed
        File outside = new File(t, "outside"); write(new File(outside, "secret.txt"), "secret");
        Files.createSymbolicLink(new File(data, "link").toPath(), outside.toPath());
        File bk2 = new File(t, "b.mbk");
        BackupEngine.Manifest m2 = BackupEngine.create(bk2, "com.x.app", 3, "", roots, null);
        boolean leaked = false; for (BackupEngine.Entry e : m2.entries) if (e.name.contains("secret")) leaked = true;
        check(!leaked, "symbolic links are skipped (no data from outside the clone is saved)");

        // sizeOf / deleteTree
        check(BackupEngine.sizeOf(data) > 0, "size of a clone folder is counted");
        BackupEngine.deleteTree(data);
        check(!data.exists() && outside.exists(), "deleteTree removes the folder but not what a link pointed to");

        // CloneLimit
        check(CloneLimit.defaultFor(3700) == 4 && CloneLimit.defaultFor(1800) == 2 && CloneLimit.defaultFor(12000) == 8, "clone limit follows RAM");
        check(CloneLimit.clamp(0) == 1 && CloneLimit.clamp(99) == 12, "clone limit is clamped");

        // CompatDb rule: no verdict without evidence
        boolean rule = true; boolean anyWorking = false;
        for (CompatDb.Item i : CompatDb.all()) {
            if (i.status != CompatDb.Status.NOT_TESTED && i.evidence.length() < 20) rule = false;
            if (i.status == CompatDb.Status.WORKING) anyWorking = true;
        }
        check(rule, "every verdict other than 'Not tested' carries evidence");
        check(anyWorking, "evidence-backed entries exist");

        System.out.println(failed == 0 ? "\nALL BACKUP TESTS PASSED" : "\n" + failed + " BACKUP TESTS FAILED");
        System.exit(failed == 0 ? 0 : 1);
    }
}
