package com.multiappcloner.pro;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** App-styled floating bottom sheet / dialog that replaces the grey system dialogs. */
public final class Sheet {
    private final Activity a;
    private final Dialog dialog;
    private final LinearLayout panel;
    private final LinearLayout list;

    private Sheet(Activity act, boolean bottom) {
        a = act;
        dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(act);
        panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF10213F, 0xFF0A1428});
        bg.setCornerRadius(Ui.dp(act, 24));
        bg.setStroke(Ui.dp(act, 1), 0xFF1F3C74);
        panel.setBackgroundDrawable(bg);
        panel.setPadding(Ui.dp(act, 18), Ui.dp(act, 10), Ui.dp(act, 18), Ui.dp(act, 16));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            bottom ? Gravity.BOTTOM : Gravity.CENTER);
        int m = Ui.dp(act, 12);
        lp.setMargins(m, m, m, m);
        root.addView(panel, lp);
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(bottom ? Gravity.BOTTOM : Gravity.CENTER);
            w.setDimAmount(0.6f);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        if (bottom) {
            View handle = new View(act);
            GradientDrawable hg = new GradientDrawable();
            hg.setColor(0xFF2B4A85);
            hg.setCornerRadius(Ui.dp(act, 3));
            handle.setBackgroundDrawable(hg);
            LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(Ui.dp(act, 42), Ui.dp(act, 4));
            hl.gravity = Gravity.CENTER_HORIZONTAL;
            hl.bottomMargin = Ui.dp(act, 12);
            panel.addView(handle, hl);
        }
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
    }

    /** Bottom action sheet with an app header (icon, title, subtitle). */
    public static Sheet actions(Activity a, Drawable icon, String title, String subtitle) {
        Sheet s = new Sheet(a, true);
        s.header(icon, title, subtitle);
        s.panel.addView(s.list);
        return s;
    }

    private void header(Drawable icon, String title, String subtitle) {
        LinearLayout h = new LinearLayout(a);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != null) {
            ImageView iv = new ImageView(a);
            iv.setImageDrawable(icon);
            h.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
        }
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(icon != null ? Ui.dp(a, 12) : 0, 0, 0, 0);
        t.addView(Ui.text(a, title, 18, Ui.TEXT, true));
        if (subtitle != null) t.addView(Ui.text(a, subtitle, 12, Ui.TEXT2, false));
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hl.bottomMargin = Ui.dp(a, 12);
        panel.addView(h, hl);
    }

    public Sheet item(String emoji, String label, String desc, int color, final Runnable run) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10), Ui.dp(a, 10));
        StateListDrawable sl = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(0xFF173061);
        pressed.setCornerRadius(Ui.dp(a, 14));
        sl.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sl.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        row.setBackgroundDrawable(sl);
        row.setClickable(true);

        TextView ic = new TextView(a);
        ic.setText(emoji);
        ic.setTextSize(18);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable cg = new GradientDrawable();
        cg.setShape(GradientDrawable.OVAL);
        cg.setColor((color & 0x00FFFFFF) | 0x2E000000);
        ic.setBackgroundDrawable(cg);
        row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 42), Ui.dp(a, 42)));

        LinearLayout tx = new LinearLayout(a);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.dp(a, 14), 0, 0, 0);
        tx.addView(Ui.text(a, label, 16, color == Ui.RED ? Ui.RED : Ui.TEXT, true));
        if (desc != null) tx.addView(Ui.text(a, desc, 12, Ui.TEXT2, false));
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(a, "›", 22, Ui.TEXT2, false));

        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
                if (run != null) run.run();
            }
        });
        list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    public void show() {
        if (!a.isFinishing()) dialog.show();
    }

    // ---------------------------------------------------------------- centered dialogs

    private static Sheet centered(Activity a, String title, String msg) {
        Sheet s = new Sheet(a, false);
        s.panel.setPadding(Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 20), Ui.dp(a, 16));
        TextView t = Ui.text(a, title, 19, Ui.TEXT, true);
        s.panel.addView(t);
        if (msg != null) {
            TextView m = Ui.text(a, msg, 14, Ui.TEXT2, false);
            m.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 14));
            s.panel.addView(m);
        }
        return s;
    }

    private void buttons(String no, String yes, boolean danger, final Runnable onYes) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        if (no != null) {
            android.widget.Button b = Ui.button(a, no, false, new View.OnClickListener() {
                public void onClick(View v) { dialog.dismiss(); }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f);
            l.rightMargin = Ui.dp(a, 8);
            row.addView(b, l);
        }
        android.widget.Button y = Ui.button(a, yes, true, new View.OnClickListener() {
            public void onClick(View v) {
                dialog.dismiss();
                if (onYes != null) onYes.run();
            }
        });
        if (danger) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(Ui.RED);
            g.setCornerRadius(Ui.dp(a, 14));
            y.setBackgroundDrawable(g);
        }
        row.addView(y, new LinearLayout.LayoutParams(0, Ui.dp(a, 48), 1f));
        panel.addView(row);
    }

    public static void confirm(Activity a, String title, String msg, String yes, boolean danger, Runnable onYes) {
        Sheet s = centered(a, title, msg);
        s.buttons("Cancel", yes, danger, onYes);
        s.show();
    }

    public static void message(Activity a, String title, String msg) {
        Sheet s = centered(a, title, msg);
        s.buttons(null, "OK", false, null);
        s.show();
    }

    public interface TextResult { void onText(String text); }

    public static void input(Activity a, String title, String value, String yes, final TextResult res) {
        final Sheet s = centered(a, title, null);
        final EditText e = Ui.input(a, "", value, false);
        e.setSelection(value == null ? 0 : value.length());
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.bottomMargin = Ui.dp(a, 14);
        s.panel.addView(e, l);
        s.buttons("Cancel", yes, false, new Runnable() {
            public void run() { res.onText(e.getText().toString()); }
        });
        s.show();
    }
}
