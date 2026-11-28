package com.multiappcloner.pro;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;

/**
 * Application class. It also runs inside the engine's own processes (":black" server and the virtual-app
 * client processes), so it must stay light: it only attaches the virtual engine.
 */
public class CloneApp extends Application {
    private static final String TAG = "CloneApp";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            final Context ctx = base;
            BlackBoxCore.get().onBeforeMainApplicationAttach(this, base);
            BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                @Override public String getHostPackageName() { return ctx.getPackageName(); }
                @Override public boolean isEnableDaemonService() { return false; }
                @Override public String getLogSenderChatId() { return ""; } // never upload logs
            });
            BlackBoxCore.get().onAfterMainApplicationAttach(this, base);
        } catch (Throwable t) {
            Log.e(TAG, "Virtual engine attach failed", t);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL && level < TRIM_MEMORY_UI_HIDDEN) {
            Diag.event(Diag.MEMORY, "trim level " + level + " in " + Diag.processName());
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Diag.event(Diag.MEMORY, "onLowMemory in " + Diag.processName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Diag.init(this);
        try {
            BlackBoxCore.get().doCreate();
        } catch (Throwable t) {
            Log.e(TAG, "Virtual engine start failed", t);
        }
    }
}
