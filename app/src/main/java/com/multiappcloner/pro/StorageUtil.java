package com.multiappcloner.pro;

import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import java.util.Locale;

/** Real device storage figures (internal data partition). Not clone storage. */
public class StorageUtil {
    public static String usedOfTotal() {
        try {
            StatFs s = new StatFs(Environment.getDataDirectory().getPath());
            long total, free;
            if (Build.VERSION.SDK_INT >= 18) {
                total = s.getBlockCountLong() * s.getBlockSizeLong();
                free = s.getAvailableBlocksLong() * s.getBlockSizeLong();
            } else {
                total = (long) s.getBlockCount() * s.getBlockSize();
                free = (long) s.getAvailableBlocks() * s.getBlockSize();
            }
            return gb(total - free) + " / " + gb(total);
        } catch (Exception e) {
            return "Unavailable";
        }
    }

    private static String gb(long bytes) {
        return String.format(Locale.US, "%.1f GB", bytes / 1073741824.0);
    }
}
