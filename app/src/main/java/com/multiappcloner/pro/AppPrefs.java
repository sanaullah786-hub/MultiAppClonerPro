package com.multiappcloner.pro;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPrefs {
    public static final String DEF_V4 = "https://api.ipify.org";
    public static final String DEF_V6 = "https://api6.ipify.org";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("mac_prefs", Context.MODE_PRIVATE);
    }

    public static String ipv4Endpoint(Context c) { return sp(c).getString("ipv4", DEF_V4); }
    public static String ipv6Endpoint(Context c) { return sp(c).getString("ipv6", DEF_V6); }
    public static int timeoutMs(Context c) { return sp(c).getInt("timeout", 8000); }
    public static long defaultNetwork(Context c) { return sp(c).getLong("def_net", -1); }
    public static boolean onboarded(Context c) { return sp(c).getBoolean("onboarded", false); }

    public static void setIpv4Endpoint(Context c, String v) { sp(c).edit().putString("ipv4", v).apply(); }
    public static void setIpv6Endpoint(Context c, String v) { sp(c).edit().putString("ipv6", v).apply(); }
    public static void setTimeoutMs(Context c, int v) { sp(c).edit().putInt("timeout", v).apply(); }
    public static void setDefaultNetwork(Context c, long v) { sp(c).edit().putLong("def_net", v).apply(); }
    public static void setOnboarded(Context c, boolean v) { sp(c).edit().putBoolean("onboarded", v).apply(); }
    public static void clear(Context c) { sp(c).edit().clear().commit(); }
}
