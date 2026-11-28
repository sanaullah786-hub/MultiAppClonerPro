package com.multiappcloner.pro.backup;

import java.util.ArrayList;
import java.util.List;

/**
 * What is actually known about each app inside the virtual space. A status other than NOT_TESTED must carry
 * evidence (who observed it and when); the unit test enforces that rule.
 */
public final class CompatDb {
    public enum Status { WORKING, PARTIAL, NOT_WORKING, NOT_TESTED }

    public static final class Item {
        public final String app; public final Status status; public final String evidence; public final String limit;
        Item(String app, Status status, String evidence, String limit) { this.app = app; this.status = status; this.evidence = evidence; this.limit = limit; }
    }

    public static List<Item> all() {
        List<Item> l = new ArrayList<Item>();
        l.add(new Item("WhatsApp", Status.WORKING, "Reported working by the owner on OPPO A3x, Android 14 (2026-11). Not independently tested by the developer.", "Account use inside a cloner can be restricted by WhatsApp's own policy."));
        l.add(new Item("WhatsApp Business", Status.NOT_TESTED, "A launch crash (AppOps ClassCastException) was fixed in code on 2026-11-26; the owner has not confirmed it since.", ""));
        l.add(new Item("Chrome", Status.NOT_WORKING, "Owner screenshots 2026-11-28 09:27 and 09:29 (OPPO A3x): stuck on the splash screen with the engine of that build. An experimental Trichrome library link was added afterwards and is untested.", "Depends on Chrome's separate Trichrome library package."));
        l.add(new Item("TikTok", Status.NOT_WORKING, "Owner report 2026-11-27: crashes or hangs. Cause not yet known (no log collected).", "May refuse to run in virtual spaces. This tool does not hide itself."));
        l.add(new Item("ChatGPT", Status.NOT_WORKING, "Owner report 2026-11: returns to the launcher by itself. Split-APK merge was added afterwards and is untested.", "Ships as split APKs."));
        l.add(new Item("Google Play Store", Status.NOT_WORKING, "Owner test 2026-11: signature error and internal error inside the clone.", "Requires matching Google signatures; not solved."));
        l.add(new Item("Facebook", Status.NOT_TESTED, "", ""));
        l.add(new Item("Messenger", Status.NOT_TESTED, "", ""));
        l.add(new Item("Instagram", Status.NOT_TESTED, "", ""));
        l.add(new Item("YouTube", Status.NOT_TESTED, "", ""));
        l.add(new Item("Normal Android games", Status.NOT_TESTED, "", "Games with anti-cheat are not supported."));
        return l;
    }

    public static String label(Status s) {
        switch (s) {
            case WORKING: return "Working";
            case PARTIAL: return "Partially working";
            case NOT_WORKING: return "Not working";
            default: return "Not tested";
        }
    }
}
