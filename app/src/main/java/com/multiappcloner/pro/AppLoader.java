package com.multiappcloner.pro;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.List;

/** Reads launchable apps through PackageManager. Call from a background thread. */
public class AppLoader {
    public static List<AppItem> load(Context c) {
        List<AppItem> out = new ArrayList<AppItem>();
        PackageManager pm = c.getPackageManager();
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(0);
        } catch (Exception e) {
            return out;
        }
        for (ApplicationInfo ai : apps) {
            try {
                if (ai.packageName.equals(c.getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                AppItem it = new AppItem();
                it.packageName = ai.packageName;
                CharSequence l = pm.getApplicationLabel(ai);
                it.label = l == null ? ai.packageName : l.toString();
                it.icon = pm.getApplicationIcon(ai);
                it.system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                try {
                    PackageInfo pi = pm.getPackageInfo(ai.packageName, 0);
                    it.versionName = pi.versionName;
                    it.installTime = pi.firstInstallTime;
                } catch (Exception ignored) {
                }
                out.add(it);
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
