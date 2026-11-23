package com.multiappcloner.pro;

import android.content.Context;
import android.content.Intent;
import android.widget.LinearLayout;

public class InfoActivity extends Screen {
    public static final String K_TITLE = "title", K_BODY = "body";

    public static void open(Context c, String title, String body) {
        Intent i = new Intent(c, InfoActivity.class);
        i.putExtra(K_TITLE, title);
        i.putExtra(K_BODY, body);
        c.startActivity(i);
    }

    @Override protected String screenTitle() {
        String t = getIntent().getStringExtra(K_TITLE);
        return t == null ? "Info" : t;
    }

    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, String.valueOf(getIntent().getStringExtra(K_BODY)), 14, Ui.TEXT2, false));
        Ui.add(body, card);
    }
}
