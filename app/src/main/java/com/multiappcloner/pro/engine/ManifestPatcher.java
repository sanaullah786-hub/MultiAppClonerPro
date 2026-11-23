package com.multiappcloner.pro.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rewrites a compiled (binary / AXML) AndroidManifest.xml so the app can be installed next to the
 * original under a different package name.
 *
 * Only attribute VALUES are changed (strings are appended to the string pool and the attribute is
 * re-pointed), so no chunk other than the string pool changes size. Pure Java, no Android classes,
 * so it is unit-testable on a plain JVM.
 *
 * What is rewritten:
 *  - manifest/@package
 *  - relative component class names (".Main", "Main") are made fully-qualified against the ORIGINAL
 *    package, so the code still resolves after the package attribute changes
 *  - custom permissions (declared and used), provider authorities, absolute process names
 *  - the application label and the launcher activity label (shown under the icon)
 */
public final class ManifestPatcher {

    public static final class Options {
        public String oldPackage;
        public String newPackage;
        /** Appended to provider authorities that do not contain the old package name. */
        public String authoritySuffix = "clone";
        /** Literal label for the application and the launcher activity. Null = keep. */
        public String label;
    }

    public static final class Result {
        public byte[] manifest;
        public int changedAttributes;
        public boolean labelPatched;
    }

    private static final int CHUNK_XML = 0x0003;
    private static final int CHUNK_STRING_POOL = 0x0001;
    private static final int CHUNK_START_TAG = 0x0102;
    private static final int CHUNK_END_TAG = 0x0103;
    private static final int FLAG_UTF8 = 0x100;
    private static final int TYPE_STRING = 0x03;
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset UTF16 = Charset.forName("UTF-16LE");

    private final byte[] data;
    private final ByteBuffer buf;
    private final Options opt;

    private List<String> strings = new ArrayList<String>();
    private boolean utf8;
    private int styleCount;
    private int[] styleOffsets;
    private byte[] styleData;
    private int poolStart, poolSize;
    private int changed;
    private boolean labelPatched;

    private ManifestPatcher(byte[] d, Options o) {
        data = d.clone();
        buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        opt = o;
    }

    public static Result patch(byte[] axml, Options o) throws IOException {
        if (o == null || o.oldPackage == null || o.newPackage == null) throw new IOException("Package names missing.");
        ManifestPatcher p = new ManifestPatcher(axml, o);
        return p.run();
    }

    /** Reads the manifest package name without modifying anything. */
    public static String readPackage(byte[] axml) throws IOException {
        Options o = new Options();
        o.oldPackage = "-";
        o.newPackage = "-";
        ManifestPatcher p = new ManifestPatcher(axml, o);
        p.parsePool();
        int off = p.poolStart + p.poolSize;
        while (off + 8 <= p.data.length) {
            int type = p.buf.getShort(off) & 0xFFFF;
            int size = p.buf.getInt(off + 4);
            if (size < 8) break;
            if (type == CHUNK_START_TAG) {
                String tag = p.str(p.buf.getInt(off + 20));
                if ("manifest".equals(tag)) {
                    int count = p.buf.getShort(off + 28) & 0xFFFF;
                    int aStart = off + 16 + (p.buf.getShort(off + 24) & 0xFFFF);
                    int aSize = p.buf.getShort(off + 26) & 0xFFFF;
                    for (int i = 0; i < count; i++) {
                        int a = aStart + i * aSize;
                        if ("package".equals(p.str(p.buf.getInt(a + 4)))) return p.attrString(a);
                    }
                }
                return null;
            }
            off += size;
        }
        return null;
    }

    // ------------------------------------------------------------------ pool parsing

    private String str(int idx) {
        return idx >= 0 && idx < strings.size() ? strings.get(idx) : null;
    }

    private void parsePool() throws IOException {
        if (data.length < 16) throw new IOException("Manifest too small.");
        if ((buf.getShort(0) & 0xFFFF) != CHUNK_XML) throw new IOException("Not a binary XML file.");
        poolStart = buf.getShort(2) & 0xFFFF;
        if (poolStart != 8) poolStart = 8;
        if ((buf.getShort(poolStart) & 0xFFFF) != CHUNK_STRING_POOL) throw new IOException("String pool not found.");
        int headerSize = buf.getShort(poolStart + 2) & 0xFFFF;
        poolSize = buf.getInt(poolStart + 4);
        int stringCount = buf.getInt(poolStart + 8);
        styleCount = buf.getInt(poolStart + 12);
        int flags = buf.getInt(poolStart + 16);
        int stringsStart = buf.getInt(poolStart + 20);
        int stylesStart = buf.getInt(poolStart + 24);
        if (poolSize <= 0 || poolStart + poolSize > data.length || stringCount < 0 || styleCount < 0)
            throw new IOException("Corrupt string pool.");
        utf8 = (flags & FLAG_UTF8) != 0;
        int offsTable = poolStart + headerSize;
        strings = new ArrayList<String>(stringCount + 8);
        for (int i = 0; i < stringCount; i++) {
            int so = poolStart + stringsStart + buf.getInt(offsTable + 4 * i);
            strings.add(readString(so));
        }
        if (styleCount > 0) {
            styleOffsets = new int[styleCount];
            for (int i = 0; i < styleCount; i++) styleOffsets[i] = buf.getInt(offsTable + 4 * stringCount + 4 * i);
            int len = poolSize - stylesStart;
            if (len < 0) throw new IOException("Corrupt style table.");
            styleData = new byte[len];
            System.arraycopy(data, poolStart + stylesStart, styleData, 0, len);
        }
    }

    private String readString(int o) throws IOException {
        try {
            if (utf8) {
                int cl = data[o] & 0xFF;
                int p = o + 1;
                if ((cl & 0x80) != 0) { p++; }
                int bl = data[p] & 0xFF;
                p++;
                if ((bl & 0x80) != 0) {
                    bl = ((bl & 0x7F) << 8) | (data[p] & 0xFF);
                    p++;
                }
                return new String(data, p, bl, UTF8);
            }
            int len = buf.getShort(o) & 0xFFFF;
            int p = o + 2;
            if ((len & 0x8000) != 0) {
                len = ((len & 0x7FFF) << 16) | (buf.getShort(p) & 0xFFFF);
                p += 2;
            }
            return new String(data, p, len * 2, UTF16);
        } catch (RuntimeException e) {
            throw new IOException("Corrupt string in pool.");
        }
    }

    // ------------------------------------------------------------------ patching

    private Result run() throws IOException {
        parsePool();
        int end = poolStart + poolSize;

        // pass 1: find activities that contain the LAUNCHER category
        Set<Integer> launcherActivities = new HashSet<Integer>();
        {
            List<String> names = new ArrayList<String>();
            List<Integer> starts = new ArrayList<Integer>();
            int off = end;
            while (off + 8 <= data.length) {
                int type = buf.getShort(off) & 0xFFFF;
                int size = buf.getInt(off + 4);
                if (size < 8 || off + size > data.length) throw new IOException("Corrupt chunk.");
                if (type == CHUNK_START_TAG) {
                    String tag = str(buf.getInt(off + 20));
                    names.add(tag);
                    starts.add(off);
                    if ("category".equals(tag)) {
                        String v = androidAttr(off, "name");
                        if ("android.intent.category.LAUNCHER".equals(v)) {
                            for (int i = names.size() - 1; i >= 0; i--) {
                                if ("activity".equals(names.get(i)) || "activity-alias".equals(names.get(i))) {
                                    launcherActivities.add(starts.get(i));
                                    break;
                                }
                            }
                        }
                    }
                } else if (type == CHUNK_END_TAG) {
                    if (!names.isEmpty()) { names.remove(names.size() - 1); starts.remove(starts.size() - 1); }
                }
                off += size;
            }
        }

        // pass 2: patch
        boolean pkgDone = false;
        int off = end;
        while (off + 8 <= data.length) {
            int type = buf.getShort(off) & 0xFFFF;
            int size = buf.getInt(off + 4);
            if (type == CHUNK_START_TAG) {
                String tag = str(buf.getInt(off + 20));
                int count = buf.getShort(off + 28) & 0xFFFF;
                int aStart = off + 16 + (buf.getShort(off + 24) & 0xFFFF);
                int aSize = buf.getShort(off + 26) & 0xFFFF;
                if (aSize < 20) throw new IOException("Unexpected attribute size.");
                boolean launcher = launcherActivities.contains(off);
                for (int i = 0; i < count; i++) {
                    int a = aStart + i * aSize;
                    String name = str(buf.getInt(a + 4));
                    if (name == null) continue;
                    String v = attrString(a);
                    if (v == null && !("label".equals(name))) continue;
                    String nv = rewrite(tag, name, v, launcher, a);
                    if (nv != null && !nv.equals(v)) {
                        setString(a, nv);
                        changed++;
                        if ("manifest".equals(tag) && "package".equals(name)) pkgDone = true;
                    }
                }
            }
            off += size;
        }
        if (!pkgDone) throw new IOException("manifest/@package not found.");

        Result r = new Result();
        r.manifest = rebuild();
        r.changedAttributes = changed;
        r.labelPatched = labelPatched;
        return r;
    }

    /** Returns the new value, or null to leave unchanged. */
    private String rewrite(String tag, String name, String v, boolean launcher, int attrOff) {
        final String oldP = opt.oldPackage, newP = opt.newPackage;
        boolean component = "activity".equals(tag) || "activity-alias".equals(tag) || "service".equals(tag)
            || "receiver".equals(tag) || "provider".equals(tag);
        if ("manifest".equals(tag)) {
            if ("package".equals(name)) return newP;
            return null;
        }
        if ("application".equals(tag)) {
            if ("label".equals(name)) return opt.label != null ? labelValue() : null;
            if ("name".equals(name) || "backupAgent".equals(name) || "manageSpaceActivity".equals(name)
                || "appComponentFactory".equals(name)) return qualify(v);
            if ("process".equals(name) || "taskAffinity".equals(name) || "permission".equals(name)) return renamePrefix(v);
            return null;
        }
        if (component) {
            if ("name".equals(name) || "targetActivity".equals(name) || "parentActivityName".equals(name)) return qualify(v);
            if ("label".equals(name)) return launcher && opt.label != null ? labelValue() : null;
            if ("process".equals(name) || "taskAffinity".equals(name) || "permission".equals(name)
                || "readPermission".equals(name) || "writePermission".equals(name)) return renamePrefix(v);
            if ("authorities".equals(name) && "provider".equals(tag)) return authorities(v);
            return null;
        }
        if ("permission".equals(tag) || "permission-group".equals(tag) || "permission-tree".equals(tag)) {
            if ("name".equals(name) || "permissionGroup".equals(name)) return renamePrefix(v);
            return null;
        }
        if ("uses-permission".equals(tag) || "uses-permission-sdk-23".equals(tag)) {
            if ("name".equals(name)) return renamePrefix(v);
            return null;
        }
        return null;
    }

    private String labelValue() {
        labelPatched = true;
        return opt.label;
    }

    private String qualify(String v) {
        if (v == null || v.length() == 0) return null;
        if (v.charAt(0) == '.') return opt.oldPackage + v;
        if (v.indexOf('.') < 0) return opt.oldPackage + "." + v;
        return null;
    }

    private String renamePrefix(String v) {
        if (v == null) return null;
        if (v.equals(opt.oldPackage)) return opt.newPackage;
        if (v.startsWith(opt.oldPackage + ".")) return opt.newPackage + v.substring(opt.oldPackage.length());
        if (v.startsWith(":")) return null;
        return null;
    }

    private String authorities(String v) {
        String[] parts = v.split(";");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String a = parts[i];
            if (i > 0) sb.append(';');
            if (a.contains(opt.oldPackage)) sb.append(a.replace(opt.oldPackage, opt.newPackage));
            else sb.append(a).append('.').append(opt.authoritySuffix);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ attribute helpers

    /** String value of an attribute, or null when it is not a plain string. */
    private String attrString(int a) {
        int type = data[a + 15] & 0xFF;
        if (type == TYPE_STRING) return str(buf.getInt(a + 16));
        int raw = buf.getInt(a + 8);
        return type == 0 ? str(raw) : null;
    }

    private String androidAttr(int tagChunk, String wanted) {
        int count = buf.getShort(tagChunk + 28) & 0xFFFF;
        int aStart = tagChunk + 16 + (buf.getShort(tagChunk + 24) & 0xFFFF);
        int aSize = buf.getShort(tagChunk + 26) & 0xFFFF;
        for (int i = 0; i < count; i++) {
            int a = aStart + i * aSize;
            if (wanted.equals(str(buf.getInt(a + 4)))) return attrString(a);
        }
        return null;
    }

    private void setString(int a, String v) {
        int idx = strings.size();
        strings.add(v);
        buf.putInt(a + 8, idx);          // rawValue
        buf.putShort(a + 12, (short) 8); // Res_value.size
        data[a + 14] = 0;                // res0
        data[a + 15] = TYPE_STRING;      // dataType
        buf.putInt(a + 16, idx);         // data
    }

    // ------------------------------------------------------------------ rebuilding

    private byte[] rebuild() throws IOException {
        int sc = strings.size();
        ByteArrayOutputStream sd = new ByteArrayOutputStream();
        int[] offs = new int[sc];
        for (int i = 0; i < sc; i++) {
            offs[i] = sd.size();
            writeString(sd, strings.get(i));
        }
        while (sd.size() % 4 != 0) sd.write(0);
        int headerSize = 28;
        int stringsStart = headerSize + 4 * sc + 4 * styleCount;
        int stylesStart = styleCount > 0 ? stringsStart + sd.size() : 0;
        int poolLen = stringsStart + sd.size() + (styleCount > 0 ? styleData.length : 0);

        ByteBuffer pool = ByteBuffer.allocate(poolLen).order(ByteOrder.LITTLE_ENDIAN);
        pool.putShort((short) CHUNK_STRING_POOL);
        pool.putShort((short) headerSize);
        pool.putInt(poolLen);
        pool.putInt(sc);
        pool.putInt(styleCount);
        pool.putInt(utf8 ? FLAG_UTF8 : 0);
        pool.putInt(stringsStart);
        pool.putInt(stylesStart);
        for (int i = 0; i < sc; i++) pool.putInt(offs[i]);
        for (int i = 0; i < styleCount; i++) pool.putInt(styleOffsets[i]);
        pool.put(sd.toByteArray());
        if (styleCount > 0) pool.put(styleData);

        int restStart = poolStart + poolSize;
        int total = poolStart + poolLen + (data.length - restStart);
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.put(data, 0, poolStart);
        out.put(pool.array());
        out.put(data, restStart, data.length - restStart);
        out.putInt(4, total);
        return out.array();
    }

    private void writeString(ByteArrayOutputStream o, String s) {
        if (utf8) {
            byte[] b = s.getBytes(UTF8);
            writeLen8(o, s.length());
            writeLen8(o, b.length);
            o.write(b, 0, b.length);
            o.write(0);
        } else {
            int len = s.length();
            if (len > 0x7FFF) {
                int hi = (len >> 16) | 0x8000;
                o.write(hi & 0xFF);
                o.write((hi >> 8) & 0xFF);
            }
            o.write(len & 0xFF);
            o.write((len >> 8) & 0xFF);
            byte[] b = s.getBytes(UTF16);
            o.write(b, 0, b.length);
            o.write(0);
            o.write(0);
        }
    }

    private static void writeLen8(ByteArrayOutputStream o, int n) {
        if (n > 0x7F) {
            o.write(((n >> 8) & 0x7F) | 0x80);
            o.write(n & 0xFF);
        } else {
            o.write(n);
        }
    }
}
