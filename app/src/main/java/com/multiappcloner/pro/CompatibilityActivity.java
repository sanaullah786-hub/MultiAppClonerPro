package com.multiappcloner.pro;

import android.widget.LinearLayout;

import com.multiappcloner.pro.backup.CompatDb;

/** What is known about each app. Only verdicts with recorded evidence are shown as anything but "Not tested". */
public class CompatibilityActivity extends Screen {
    @Override protected String screenTitle() { return "App Compatibility"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void build(LinearLayout body) {
        Ui.add(body, Ui.text(this, "Results come from real tests on a phone. Anything not tested is marked so. A build that compiles is not a test.", 12, Ui.ORANGE, false));
        for (CompatDb.Item i : CompatDb.all()) {
            LinearLayout c = Ui.card(this);
            int color = i.status == CompatDb.Status.WORKING ? Ui.GREEN : i.status == CompatDb.Status.PARTIAL ? Ui.ORANGE
                : i.status == CompatDb.Status.NOT_WORKING ? Ui.RED : Ui.TEXT2;
            c.addView(Ui.text(this, i.app, 15, Ui.TEXT, true));
            c.addView(Ui.badge(this, CompatDb.label(i.status).toUpperCase(java.util.Locale.ROOT), color));
            if (i.evidence.length() > 0) c.addView(Ui.text(this, "Evidence: " + i.evidence, 12, Ui.TEXT2, false));
            if (i.limit.length() > 0) c.addView(Ui.text(this, "Limit: " + i.limit, 12, Ui.TEXT2, false));
            Ui.add(body, c);
        }
    }
}
