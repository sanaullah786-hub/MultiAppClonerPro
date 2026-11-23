package com.multiappcloner.pro;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "clones.db";
    public static final int DB_VERSION = 4;
    public static final String T_CLONES = "clones";
    public static final String T_NET = "network_profiles";
    public static final String T_LOGS = "clone_logs";
    public static final String T_COMPAT = "compat_registry";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper get(Context c) {
        if (instance == null) instance = new DatabaseHelper(c.getApplicationContext());
        return instance;
    }

    /** Deletes the whole database file (used by "delete all app data"). */
    public static synchronized void wipe(Context c) {
        if (instance != null) {
            instance.close();
            instance = null;
        }
        c.getApplicationContext().deleteDatabase(DB_NAME);
    }

    private DatabaseHelper(Context c) {
        super(c, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + T_CLONES + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "app_name TEXT NOT NULL,"
            + "package_name TEXT NOT NULL,"
            + "clone_name TEXT NOT NULL,"
            + "method TEXT NOT NULL,"
            + "network_profile_id INTEGER NOT NULL DEFAULT -1,"
            + "created_at INTEGER NOT NULL,"
            + "status TEXT NOT NULL,"
            + "note TEXT NOT NULL DEFAULT '',"
            + "clone_package TEXT NOT NULL DEFAULT '',"
            + "virtual_user INTEGER NOT NULL DEFAULT -1)");
        createV2Tables(db);
    }

    private void createV2Tables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_NET + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "kind TEXT NOT NULL,"
            + "name TEXT NOT NULL,"
            + "host TEXT NOT NULL DEFAULT '',"
            + "port INTEGER NOT NULL DEFAULT 0,"
            + "username TEXT NOT NULL DEFAULT '',"
            + "password_enc TEXT NOT NULL DEFAULT '',"
            + "provider TEXT NOT NULL DEFAULT '',"
            + "location TEXT NOT NULL DEFAULT '',"
            + "notes TEXT NOT NULL DEFAULT '',"
            + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_LOGS + " ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "clone_id INTEGER NOT NULL,"
            + "ts INTEGER NOT NULL,"
            + "message TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS " + T_COMPAT + " ("
            + "package_name TEXT PRIMARY KEY,"
            + "real_status TEXT NOT NULL,"
            + "virtual_status TEXT NOT NULL,"
            + "reasons TEXT NOT NULL,"
            + "checked_at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + T_CLONES + " ADD COLUMN note TEXT NOT NULL DEFAULT ''");
            createV2Tables(db);
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + T_CLONES + " ADD COLUMN clone_package TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + T_CLONES + " ADD COLUMN virtual_user INTEGER NOT NULL DEFAULT -1");
        }
    }
}
