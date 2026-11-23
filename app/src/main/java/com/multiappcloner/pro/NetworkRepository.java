package com.multiappcloner.pro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class NetworkRepository {
    public static final long ERROR = -1;
    public static final long DUPLICATE = -2;
    private final DatabaseHelper helper;

    public NetworkRepository(Context c) {
        helper = DatabaseHelper.get(c);
    }

    private ContentValues values(NetworkProfile p) {
        ContentValues v = new ContentValues();
        v.put("kind", p.kind);
        v.put("name", p.name.trim());
        v.put("host", p.host);
        v.put("port", p.port);
        v.put("username", p.username);
        v.put("password_enc", p.passwordEnc);
        v.put("provider", p.provider);
        v.put("location", p.location);
        v.put("notes", p.notes);
        v.put("created_at", p.createdAt == 0 ? System.currentTimeMillis() : p.createdAt);
        return v;
    }

    public boolean nameExists(String name, long excludeId) {
        Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_NET
            + " WHERE LOWER(name)=LOWER(?) AND id<>?", new String[]{name.trim(), String.valueOf(excludeId)});
        try {
            return c.moveToFirst() && c.getInt(0) > 0;
        } finally {
            c.close();
        }
    }

    public long insert(NetworkProfile p) {
        try {
            if (nameExists(p.name, -1)) return DUPLICATE;
            return helper.getWritableDatabase().insert(DatabaseHelper.T_NET, null, values(p));
        } catch (Exception e) {
            return ERROR;
        }
    }

    public long update(NetworkProfile p) {
        try {
            if (nameExists(p.name, p.id)) return DUPLICATE;
            int n = helper.getWritableDatabase().update(DatabaseHelper.T_NET, values(p), "id=?", new String[]{String.valueOf(p.id)});
            return n == 1 ? p.id : ERROR;
        } catch (Exception e) {
            return ERROR;
        }
    }

    private NetworkProfile read(Cursor c) {
        NetworkProfile p = new NetworkProfile();
        p.id = c.getLong(c.getColumnIndex("id"));
        p.kind = c.getString(c.getColumnIndex("kind"));
        p.name = c.getString(c.getColumnIndex("name"));
        p.host = c.getString(c.getColumnIndex("host"));
        p.port = c.getInt(c.getColumnIndex("port"));
        p.username = c.getString(c.getColumnIndex("username"));
        p.passwordEnc = c.getString(c.getColumnIndex("password_enc"));
        p.provider = c.getString(c.getColumnIndex("provider"));
        p.location = c.getString(c.getColumnIndex("location"));
        p.notes = c.getString(c.getColumnIndex("notes"));
        p.createdAt = c.getLong(c.getColumnIndex("created_at"));
        return p;
    }

    public List<NetworkProfile> getAll() {
        List<NetworkProfile> out = new ArrayList<NetworkProfile>();
        try {
            Cursor c = helper.getReadableDatabase().query(DatabaseHelper.T_NET, null, null, null, null, null, "id ASC");
            try {
                while (c.moveToNext()) out.add(read(c));
            } finally {
                c.close();
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public NetworkProfile get(long id) {
        try {
            Cursor c = helper.getReadableDatabase().query(DatabaseHelper.T_NET, null, "id=?",
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
        try {
            Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_NET, null);
            try {
                return c.moveToFirst() ? c.getInt(0) : 0;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public int clonesUsing(long id) {
        try {
            Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_CLONES
                + " WHERE network_profile_id=?", new String[]{String.valueOf(id)});
            try {
                return c.moveToFirst() ? c.getInt(0) : 0;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /** Deletes the profile and resets clones that pointed at it to "default connection". */
    public boolean delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        try {
            db.beginTransaction();
            ContentValues v = new ContentValues();
            v.put("network_profile_id", -1);
            db.update(DatabaseHelper.T_CLONES, v, "network_profile_id=?", new String[]{String.valueOf(id)});
            int n = db.delete(DatabaseHelper.T_NET, "id=?", new String[]{String.valueOf(id)});
            if (n == 1) db.setTransactionSuccessful();
            return n == 1;
        } catch (Exception e) {
            return false;
        } finally {
            try { db.endTransaction(); } catch (Exception ignored) { }
        }
    }
}
