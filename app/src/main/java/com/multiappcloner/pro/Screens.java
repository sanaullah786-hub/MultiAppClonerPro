package com.multiappcloner.pro;

import android.content.Context;
import android.content.Intent;

public class Screens {
    public static void installedApps(Context c) {
        Intent i = new Intent(c, InstalledAppsActivity.class);
        i.putExtra(InstalledAppsActivity.EXTRA_PICK, false);
        c.startActivity(i);
    }

    public static void createClone(Context c) {
        Intent i = new Intent(c, InstalledAppsActivity.class);
        i.putExtra(InstalledAppsActivity.EXTRA_PICK, true);
        c.startActivity(i);
    }

    public static void clones(Context c) { c.startActivity(new Intent(c, CloneManagerActivity.class)); }
    public static void network(Context c) { c.startActivity(new Intent(c, NetworkManagerActivity.class)); }
    public static void ipChecker(Context c) { c.startActivity(new Intent(c, IpCheckerActivity.class)); }
    public static void settings(Context c) { c.startActivity(new Intent(c, SettingsActivity.class)); }
}
