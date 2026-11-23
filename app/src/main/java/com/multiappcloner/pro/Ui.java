package com.multiappcloner.pro;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small programmatic UI kit so every screen shares the same design system. */
public class Ui {
    public static final int BG = 0xFF050B18, BG2 = 0xFF0B1730, BLUE = 0xFF087FFF, CYAN = 0xFF00D9FF, PURPLE = 0xFF7957FF,
        GREEN = 0xFF16D98C, ORANGE = 0xFFFFB547, RED = 0xFFFF4560, TEXT = 0xFFFFFFFF, TEXT2 = 0xFFA7B5CC;

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.bg_card);
        l.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));
        return l;
    }

    public static void add(LinearLayout parent, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(parent.getContext(), 10);
        parent.addView(v, lp);
    }

    public static TextView section(Context c, String t) {
        TextView v = text(c, t, 13, CYAN, true);
        v.setPadding(0, dp(c, 8), 0, dp(c, 6));
        return v;
    }

    public static TextView badge(Context c, String t, int color) {
        TextView v = text(c, t, 11, color, true);
        GradientDrawable g = new GradientDrawable();
        g.setColor((color & 0x00FFFFFF) | 0x33000000);
        g.setStroke(dp(c, 1), color);
        g.setCornerRadius(dp(c, 8));
        v.setBackgroundDrawable(g);
        v.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        return v;
    }

    public static View row(Context c, String label, String value) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(c, 4), 0, dp(c, 4));
        TextView a = text(c, label, 12, TEXT2, false);
        TextView b = text(c, value, 13, TEXT, false);
        r.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));
        r.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f));
        return r;
    }

    public static Button button(Context c, String t, boolean primary, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setMinHeight(dp(c, 48));
        b.setBackgroundResource(primary ? R.drawable.bg_btn_primary : R.drawable.bg_btn_outline);
        b.setOnClickListener(l);
        return b;
    }

    public static EditText input(Context c, String hint, String value, boolean password) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(TEXT2);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackgroundResource(R.drawable.bg_card);
        e.setPadding(dp(c, 14), 0, dp(c, 14), 0);
        e.setMinHeight(dp(c, 48));
        if (value != null) e.setText(value);
        if (password) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }
}
