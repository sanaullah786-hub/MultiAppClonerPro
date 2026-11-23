package com.multiappcloner.pro;

/** Placeholder boundary for a real virtualization engine. None is bundled, so it reports unavailable. */
public class VirtualizationEngine {
    public static boolean isAvailable() {
        return false;
    }

    public static String unavailableReason() {
        return "No virtual-app engine is bundled. Cloning here is done by APK repackaging (a real second installed app), "
            + "not by running apps inside a sandbox.";
    }

    public static ApkCloningEngine.Result launch(String pkg) {
        return new ApkCloningEngine.Result(false, unavailableReason());
    }
}
