package com.multiappcloner.pro;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private TextView statInstalled, statTotal, statActive, statNet;
    private CloneRepository repo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.applyDarkBars(this);
        setContentView(R.layout.activity_main);

        SpannableString s = new SpannableString("Multi App Cloner Pro");
        s.setSpan(new ForegroundColorSpan(Color.parseColor("#00D9FF")), 10, 20, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.title)).setText(s);

        LinearLayout r1 = (LinearLayout) findViewById(R.id.row1);
        LinearLayout r2 = (LinearLayout) findViewById(R.id.row2);
        LinearLayout r3 = (LinearLayout) findViewById(R.id.row3);

        addTile(r1, "📲", "Clone Apps", "Create & manage clones", R.drawable.bg_tile_blue, 0, new View.OnClickListener() {
            public void onClick(View v) { Screens.createClone(MainActivity.this); }
        });
        addTile(r1, "🌐", "VPN & Proxy", "Network per clone", R.drawable.bg_tile_green, 10, new View.OnClickListener() {
            public void onClick(View v) { Screens.network(MainActivity.this); }
        });
        addTile(r2, "📦", "Installed Apps", "View & select apps", R.drawable.bg_tile_orange, 0, new View.OnClickListener() {
            public void onClick(View v) { Screens.installedApps(MainActivity.this); }
        });
        addTile(r2, "🗂", "Clone Manager", "Manage your clones", R.drawable.bg_tile_purple, 10, new View.OnClickListener() {
            public void onClick(View v) { Screens.clones(MainActivity.this); }
        });
        addSmall(r3, "📍  IP Checker", 0, new View.OnClickListener() {
            public void onClick(View v) { Screens.ipChecker(MainActivity.this); }
        });
        addSmall(r3, "📥  Install files", 10, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new android.content.Intent(MainActivity.this, ImportApkActivity.class)); }
        });
        addSmall(r3, "⚙  Settings", 10, new View.OnClickListener() {
            public void onClick(View v) { Screens.settings(MainActivity.this); }
        });

        statTotal = addStat((LinearLayout) findViewById(R.id.statsRow1), "Total Clone Profiles", "0", true);
        statInstalled = addStat((LinearLayout) findViewById(R.id.statsRow1), "Installed Apps", "…", false);
        statActive = addStat((LinearLayout) findViewById(R.id.statsRow2), "Active Clone Profiles", "0", true);
        statNet = addStat((LinearLayout) findViewById(R.id.statsRow2), "Saved Network Profiles", "0", false);
        addStat((LinearLayout) findViewById(R.id.statsRow3), "Device Storage Used", StorageUtil.usedOfTotal(), true);

        findViewById(R.id.btnCreate).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Screens.createClone(MainActivity.this); }
        });

        repo = new CloneRepository(this);
        BottomNav.setup(this, 0);
        loadInstalledCount();
    }

    private void addTile(LinearLayout row, String icon, String title, String sub, int bg, int leftMargin, View.OnClickListener l) {
        View t = LayoutInflater.from(this).inflate(R.layout.view_tile, row, false);
        ((TextView) t.findViewById(R.id.tileIcon)).setText(icon);
        ((TextView) t.findViewById(R.id.tileTitle)).setText(title);
        ((TextView) t.findViewById(R.id.tileSub)).setText(sub);
        t.setBackgroundResource(bg);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = UiUtil.dp(this, leftMargin);
        row.addView(t, lp);
    }

    private void addSmall(LinearLayout row, String text, int leftMargin, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(14);
        t.setGravity(android.view.Gravity.CENTER);
        t.setBackgroundResource(R.drawable.bg_btn_outline);
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, UiUtil.dp(this, 48), 1f);
        lp.leftMargin = UiUtil.dp(this, leftMargin);
        row.addView(t, lp);
    }

    private TextView addStat(LinearLayout row, String label, String value, boolean first) {
        View v = LayoutInflater.from(this).inflate(R.layout.view_stat, row, false);
        ((TextView) v.findViewById(R.id.statLabel)).setText(label);
        TextView val = (TextView) v.findViewById(R.id.statValue);
        val.setText(value);
        row.addView(v);
        return val;
    }

    /** Counts launchable apps using PackageManager on a background thread. */
    private void loadInstalledCount() {
        final Context c = getApplicationContext();
        exec.execute(new Runnable() {
            public void run() {
                int count = 0;
                String result;
                try {
                    PackageManager pm = c.getPackageManager();
                    List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                    for (ApplicationInfo ai : apps) {
                        if (pm.getLaunchIntentForPackage(ai.packageName) != null) count++;
                    }
                    result = String.valueOf(count);
                } catch (Exception e) {
                    result = "Unavailable";
                }
                final String r = result;
                runOnUiThread(new Runnable() {
                    public void run() { statInstalled.setText(r); }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        statTotal.setText(String.valueOf(repo.count()));
        statActive.setText(String.valueOf(repo.countActive()));
        statNet.setText(String.valueOf(new NetworkRepository(this).count()));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
