package com.multiappcloner.pro.backup;

/**
 * Default cap on clone processes running at once, from the phone's total RAM. These numbers are conservative
 * estimates, not measurements; the user can change the limit.
 */
public final class CloneLimit {
    public static int defaultFor(long totalRamMb) {
        if (totalRamMb < 2500) return 2;
        if (totalRamMb < 3500) return 3;
        if (totalRamMb < 5500) return 4;
        if (totalRamMb < 8000) return 6;
        return 8;
    }

    public static int clamp(int v) {
        return Math.max(1, Math.min(12, v));
    }
}
