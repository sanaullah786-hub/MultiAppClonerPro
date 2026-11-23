package com.multiappcloner.pro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

public class BottomNav {
    private static final int[] BOX = {R.id.nav0, R.id.nav1, R.id.nav2, R.id.nav3};
    private static final int[] ICON = {R.id.navIcon0, R.id.navIcon1, R.id.navIcon2, R.id.navIcon3};
    private static final int[] LABEL = {R.id.navLabel0, R.id.navLabel1, R.id.navLabel2, R.id.navLabel3};

    public static void setup(final Activity a, final int selected) {
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            int color = (i == selected) ? Color.parseColor("#00D9FF") : Color.parseColor("#A7B5CC");
            ((TextView) a.findViewById(LABEL[i])).setTextColor(color);
            ((TextView) a.findViewById(ICON[i])).setAlpha(i == selected ? 1f : 0.6f);
            a.findViewById(BOX[i]).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx == selected) return;
                    if (idx == 0) {
                        Intent in = new Intent(a, MainActivity.class);
                        in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        a.startActivity(in);
                    } else {
                        if (idx == 1) Screens.clones(a);
                        else if (idx == 2) Screens.network(a);
                        else Screens.settings(a);
                        if (!(a instanceof MainActivity)) a.finish();
                    }
                }
            });
        }
    }
}
