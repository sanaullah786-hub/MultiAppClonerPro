package com.multiappcloner.pro;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Profile-specific event log (stored in this app's own database). */
public class LogRepository {
    private final DatabaseHelper helper;

    public LogRepository(Context c) {
        helper = DatabaseHelper.get(c);
    }

    public void add(long cloneId, String message) {
        try {
            ContentValues v = new ContentValues();
            v.put("clone_id", cloneId);
            v.put("ts", System.currentTimeMillis());
            v.put("message", message);
            helper.getWritableDatabase().insert(DatabaseHelper.T_LOGS, null, v);
        } catch (Exception ignored) {
        }
    }

    public List<String> list(long cloneId, int limit) {
        List<String> out = new ArrayList<String>();
        try {
            Cursor c = helper.getReadableDatabase().query(DatabaseHelper.T_LOGS, null, "clone_id=?",
                new String[]{String.valueOf(cloneId)}, null, null, "id DESC", String.valueOf(limit));
            try {
                SimpleDateFormat f = new SimpleDateFormat("dd MMM HH:mm", Locale.getDefault());
                while (c.moveToNext()) {
                    out.add(f.format(new Date(c.getLong(c.getColumnIndex("ts")))) + "  " + c.getString(c.getColumnIndex("message")));
                }
            } finally {
                c.close();
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public int countAll() {
        try {
            Cursor c = helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.T_LOGS, null);
            try {
                return c.moveToFirst() ? c.getInt(0) : 0;
            } finally {
                c.close();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    public void clearAll() {
        try {
            helper.getWritableDatabase().delete(DatabaseHelper.T_LOGS, null, null);
        } catch (Exception ignored) {
        }
    }
}
