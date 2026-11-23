package com.multiappcloner.pro;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;

public class ProvidersActivity extends Screen {
    @Override protected String screenTitle() { return "Providers"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void build(LinearLayout body) {
        Ui.add(body, Ui.text(this, "These are VPN clients and providers with links to their own documentation. "
            + "This app does not call provider APIs, purchase subscriptions or verify subscription status. Plans change, so check each provider's site.", 12, Ui.TEXT2, false));
        for (final Providers.P p : Providers.ALL) {
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, p.name, 15, Ui.TEXT, true));
            c.addView(Ui.text(this, p.tier, 12, Ui.TEXT2, false));
            c.addView(Ui.button(this, "Open documentation", false, new View.OnClickListener() {
                public void onClick(View v) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(p.docs))); }
                    catch (Exception e) { info("Cannot open link", p.docs); }
                }
            }));
            Intent launch = getPackageManager().getLaunchIntentForPackage(p.pkg);
            c.addView(Ui.text(this, launch == null ? "App not installed (or not visible)" : "App installed", 11, launch == null ? Ui.ORANGE : Ui.GREEN, false));
            Ui.add(body, c);
        }
    }
}
