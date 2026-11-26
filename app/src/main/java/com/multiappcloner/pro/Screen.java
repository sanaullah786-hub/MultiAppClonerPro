package com.multiappcloner.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Base class: dark header + scrolling body. Subclasses only describe their content in build(). */
public abstract class Screen extends Activity {
    protected LinearLayout body;
    protected final ExecutorService exec = Executors.newSingleThreadExecutor();
    private boolean created = false;

    protected abstract String screenTitle();
    protected abstract void build(LinearLayout body);
    protected void onReady() { }
    protected boolean showNav() { return false; }
    protected int navIndex() { return -1; }
    protected boolean rebuildOnResume() { return true; }
    protected boolean showBack() { return true; }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.applyDarkBars(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        if (showBack()) {
            header.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 16), 0);
            TextView back = Ui.text(this, "←", 24, Ui.CYAN, false);
            back.setGravity(Gravity.CENTER);
            back.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { finish(); }
            });
            header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        } else {
            header.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        }
        header.addView(Ui.text(this, screenTitle(), 20, Ui.TEXT, true));
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

        ScrollView sv = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 24));
        sv.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (showNav()) root.addView(getLayoutInflater().inflate(R.layout.include_bottom_nav, root, false));
        setContentView(root);
        if (showNav()) BottomNav.setup(this, navIndex());

        onReady();
        if (isFinishing()) return;
        rebuild();
        created = true;
    }

    protected void rebuild() {
        body.removeAllViews();
        build(body);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (created && rebuildOnResume()) rebuild();
    }

    protected void ui(final Runnable r) {
        runOnUiThread(new Runnable() {
            public void run() { if (!isFinishing()) r.run(); }
        });
    }

    protected void confirm(String title, String msg, String yes, final Runnable onYes) {
        boolean danger = yes != null && (yes.startsWith("Delete") || yes.startsWith("Remove") || yes.startsWith("Clear") || yes.startsWith("Uninstall"));
        Sheet.confirm(this, title, msg, yes, danger, onYes);
    }

    protected void info(String title, String msg) {
        Sheet.message(this, title, msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
