package com.multiappcloner.pro;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

public class UiUtil {
    public static void applyDarkBars(Activity a) {
        if (Build.VERSION.SDK_INT >= 21) {
            a.getWindow().setStatusBarColor(0xFF050B18);
            a.getWindow().setNavigationBarColor(0xFF0B1730);
        }
    }

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }
}
