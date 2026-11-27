package com.multiappcloner.pro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class CloneRepository {
    public static final long ERROR = -1;
    public static final long DUPLICATE = -2;
    public static final String METHOD_NONE = "unassigned";
    public static final String METHOD_REAL = "real_apk";
    public static final String METHOD_VIRTUAL = "virtual";
    public static final String STATUS_RECORD_ONLY = "profile_only";
    public static final String STATUS_INSTALLING = "installing";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_REMOVED = "removed";
    public static final int MAX_NAME = 30;

    private final DatabaseHelper helper;

    public CloneRepository(Context c) {
        helper = DatabaseHelper.get(c);
    }

    public static String validateName(String name) {
        if (name == null || name.trim().length() == 0) return "Enter a clone name.";
        if (name.trim().length() > MAX_NAME) return "Clone name must be at most " + MAX_NAME + " characters.";
        return null;
    }

    public long insert(CloneProfile p) {
        try {
            if (nameExists(p.packageName, p.cloneName, -1)) return DUPLICATE;
            ContentValues v = new ContentValues();
            v.put("app_name", p.appName);
            v.put("package_name", p.packageName);
            v.put("clone_name", p.cloneName.trim());
            v.put("method", p.method);
            v.put("network_profile_id", p.networkProfileId);
            v.put("created_at", p.createdAt);
            v.put("status", p.status);
            v.put("note", p.note == null ? "" : p.note);
            v.put("clone_package", p.clonePackage == null ? "" : p.clonePackage);
            v.put("virtual_user", p.virtualUser);
            return helper.getWritableDatabase().insert(DatabaseHelper.T_CLONES, null, v);
        } catch (Exception e) {
            return ERROR;
        }
    }

    public boolean nameExists(String pkg, String name, long excludeId) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_CLONES
            + " WHERE package_name=? AND LOWER(clone_name)=LOWER(?) AND id<>?",
            new String[]{pkg, name.trim(), String.valueOf(excludeId)});
        try {
            return c.moveToFirst() && c.getInt(0) > 0;
        } finally {
            c.close();
        }
    }

    private CloneProfile read(Cursor c) {
        CloneProfile p = new CloneProfile();
        p.id = c.getLong(c.getColumnIndex("id"));
        p.appName = c.getString(c.getColumnIndex("app_name"));
        p.packageName = c.getString(c.getColumnIndex("package_name"));
        p.cloneName = c.getString(c.getColumnIndex("clone_name"));
        p.method = c.getString(c.getColumnIndex("method"));
        p.networkProfileId = c.getLong(c.getColumnIndex("network_profile_id"));
        p.createdAt = c.getLong(c.getColumnIndex("created_at"));
        p.status = c.getString(c.getColumnIndex("status"));
        p.note = c.getString(c.getColumnIndex("note"));
        int cp = c.getColumnIndex("clone_package");
        p.clonePackage = cp >= 0 && !c.isNull(cp) ? c.getString(cp) : "";
        int vu = c.getColumnIndex("virtual_user");
        p.virtualUser = vu >= 0 && !c.isNull(vu) ? c.getInt(vu) : -1;
        return p;
    }

    public List<CloneProfile> getAll() {
        List<CloneProfile> out = new ArrayList<CloneProfile>();
        try {
            Cursor c = helper.getReadableDatabase().query(DatabaseHelper.T_CLONES, null, null, null, null, null, "id DESC");
            try {
                while (c.moveToNext()) out.add(read(c));
            } finally {
                c.close();
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public CloneProfile getById(long id) {
        try {
            Cursor c = helper.getReadableDatabase().query(DatabaseHelper.T_CLONES, null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);
            try {
                return c.moveToFirst() ? read(c) : null;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public int count() {
        return scalar("SELECT COUNT(*) FROM " + DatabaseHelper.T_CLONES, null);
    }

    public int countActive() {
        return scalar("SELECT COUNT(*) FROM " + DatabaseHelper.T_CLONES + " WHERE status=?", new String[]{STATUS_ACTIVE});
    }

    /** "App - Clone N" with the lowest free number; the number is never cut off, only the app name is shortened. */
    public String defaultName(String pkg, String label) {
        int n = countForPackage(pkg) + 1;
        for (int guard = 0; guard < 200; guard++, n++) {
            String suffix = " - Clone " + n;
            String base = label == null ? "App" : label;
            int room = MAX_NAME - suffix.length();
            if (base.length() > room) base = base.substring(0, Math.max(1, room)).trim();
            String name = base + suffix;
            if (!nameExists(pkg, name, -1)) return name;
        }
        return (label == null ? "App" : label) + " - " + System.currentTimeMillis() % 10000;
    }

    public int countForPackage(String pkg) {
        return scalar("SELECT COUNT(*) FROM " + DatabaseHelper.T_CLONES + " WHERE package_name=?", new String[]{pkg});
    }

    private int scalar(String sql, String[] args) {
        try {
            Cursor c = helper.getReadableDatabase().rawQuery(sql, args);
            try {
                return c.moveToFirst() ? c.getInt(0) : 0;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /** Returns 1 on success, DUPLICATE or ERROR otherwise. */
    public long rename(CloneProfile p, String newName) {
        try {
            if (nameExists(p.packageName, newName, p.id)) return DUPLICATE;
            return update(p.id, "clone_name", newName.trim()) ? 1 : ERROR;
        } catch (Exception e) {
            return ERROR;
        }
    }

    public boolean setNetwork(long cloneId, long networkId) {
        try {
            ContentValues v = new ContentValues();
            v.put("network_profile_id", networkId);
            return helper.getWritableDatabase().update(DatabaseHelper.T_CLONES, v, "id=?", new String[]{String.valueOf(cloneId)}) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** Next unused virtual user id: every virtual clone gets its own separate data area. */
    public int nextVirtualUser() {
        try {
            Cursor c = helper.getReadableDatabase().rawQuery("SELECT MAX(virtual_user) FROM " + DatabaseHelper.T_CLONES, null);
            try {
                int max = c.moveToFirst() && !c.isNull(0) ? c.getInt(0) : -1;
                return max + 1;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            return (int) (System.currentTimeMillis() % 100000);
        }
    }

    public boolean setVirtualUser(long cloneId, int user) {
        try {
            ContentValues v = new ContentValues();
            v.put("virtual_user", user);
            return helper.getWritableDatabase().update(DatabaseHelper.T_CLONES, v, "id=?", new String[]{String.valueOf(cloneId)}) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** Updates status, method and clone package together. */
    public boolean setState(long cloneId, String status, String method, String clonePackage) {
        try {
            ContentValues v = new ContentValues();
            v.put("status", status);
            if (method != null) v.put("method", method);
            if (clonePackage != null) v.put("clone_package", clonePackage);
            return helper.getWritableDatabase().update(DatabaseHelper.T_CLONES, v, "id=?", new String[]{String.valueOf(cloneId)}) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setNote(long cloneId, String note) {
        return update(cloneId, "note", note == null ? "" : note);
    }

    private boolean update(long id, String col, String val) {
        try {
            ContentValues v = new ContentValues();
            v.put(col, val);
            return helper.getWritableDatabase().update(DatabaseHelper.T_CLONES, v, "id=?", new String[]{String.valueOf(id)}) == 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** Deletes only this profile record and its own logs. */
    public boolean delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            db.beginTransaction();
            db.delete(DatabaseHelper.T_LOGS, "clone_id=?", new String[]{String.valueOf(id)});
            int n = db.delete(DatabaseHelper.T_CLONES, "id=?", new String[]{String.valueOf(id)});
            if (n == 1) db.setTransactionSuccessful();
            return n == 1;
        } catch (Exception e) {
            return false;
        } finally {
            try { db.endTransaction(); } catch (Exception ignored) { }
        }
    }
}
