package com.multiappcloner.pro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InstalledAppsActivity extends Activity {
    public static final String EXTRA_PICK = "pick";
    private static final String[] SORT_LABELS = {"Sort: A-Z", "Sort: Z-A", "Sort: Recent"};

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final List<AppItem> all = new ArrayList<AppItem>();
    private final List<AppItem> shown = new ArrayList<AppItem>();
    private final TextView[] chips = new TextView[3];
    private AppAdapter adapter;
    private EditText search;
    private TextView empty, count, btnSort;
    private int filter = 0;
    private int sort = 0;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.applyDarkBars(this);
        setContentView(R.layout.activity_installed_apps);

        boolean pick = getIntent().getBooleanExtra(EXTRA_PICK, false);
        ((TextView) findViewById(R.id.screenTitle)).setText(pick ? "Select App to Clone" : "Installed Apps");
        findViewById(R.id.btnBack).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { finish(); }
        });
        findViewById(R.id.btnRefresh).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { load(); }
        });

        search = (EditText) findViewById(R.id.search);
        empty = (TextView) findViewById(R.id.empty);
        count = (TextView) findViewById(R.id.count);
        btnSort = (TextView) findViewById(R.id.btnSort);

        int[] ids = {R.id.chip0, R.id.chip1, R.id.chip2};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            chips[i] = (TextView) findViewById(ids[i]);
            chips[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { filter = idx; applyFilter(); }
            });
        }
        btnSort.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sort = (sort + 1) % 3; applyFilter(); }
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { applyFilter(); }
            public void afterTextChanged(Editable e) { }
        });

        adapter = new AppAdapter();
        ((ListView) findViewById(R.id.list)).setAdapter(adapter);
        load();
    }

    private void load() {
        if (loading) return;
        loading = true;
        shown.clear();
        adapter.notifyDataSetChanged();
        empty.setText("Loading apps...");
        empty.setVisibility(View.VISIBLE);
        exec.execute(new Runnable() {
            public void run() {
                final List<AppItem> r = AppLoader.load(getApplicationContext());
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (isFinishing()) return;
                        all.clear();
                        all.addAll(r);
                        loading = false;
                        applyFilter();
                    }
                });
            }
        });
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (AppItem it : all) {
            if (filter == 1 && it.system) continue;
            if (filter == 2 && !it.system) continue;
            if (q.length() > 0 && !it.label.toLowerCase(Locale.getDefault()).contains(q)
                && !it.packageName.toLowerCase(Locale.getDefault()).contains(q)) continue;
            shown.add(it);
        }
        Collections.sort(shown, new Comparator<AppItem>() {
            public int compare(AppItem a, AppItem b) {
                if (sort == 2) return a.installTime == b.installTime ? 0 : (a.installTime < b.installTime ? 1 : -1);
                int r = a.label.compareToIgnoreCase(b.label);
                return sort == 1 ? -r : r;
            }
        });
        for (int i = 0; i < 3; i++) {
            chips[i].setBackgroundResource(i == filter ? R.drawable.bg_btn_primary : R.drawable.bg_card);
            chips[i].setTextColor(i == filter ? Color.WHITE : Color.parseColor("#A7B5CC"));
        }
        btnSort.setText(SORT_LABELS[sort]);
        count.setText(shown.size() + " of " + all.size() + " apps");
        adapter.notifyDataSetChanged();
        if (shown.isEmpty()) {
            empty.setText(all.isEmpty()
                ? "No apps found. Android may limit which apps are visible to this app. Tap refresh to try again."
                : "No apps match your search or filter.");
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    private class AppAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int p) { return shown.get(p); }
        public long getItemId(int p) { return p; }

        public View getView(int p, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(InstalledAppsActivity.this).inflate(R.layout.item_app, parent, false);
            final AppItem it = shown.get(p);
            ((ImageView) v.findViewById(R.id.appIcon)).setImageDrawable(it.icon);
            ((TextView) v.findViewById(R.id.appName)).setText(it.label);
            ((TextView) v.findViewById(R.id.appPkg)).setText(it.packageName);
            TextView st = (TextView) v.findViewById(R.id.appStatus);
            View add = v.findViewById(R.id.btnAdd);
            if (it.system) {
                st.setText("Installed  •  System app: cloning unsupported");
                st.setTextColor(Color.parseColor("#FF4560"));
                add.setVisibility(View.GONE);
            } else {
                st.setText("Installed  •  Compatibility not analyzed yet");
                st.setTextColor(Color.parseColor("#FFB547"));
                add.setVisibility(View.VISIBLE);
                add.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View x) {
                        Intent i = new Intent(InstalledAppsActivity.this, CreateCloneActivity.class);
                        i.putExtra(CreateCloneActivity.EXTRA_PKG, it.packageName);
                        i.putExtra(CreateCloneActivity.EXTRA_LABEL, it.label);
                        startActivity(i);
                    }
                });
            }
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    Intent i = new Intent(InstalledAppsActivity.this, AppDetailsActivity.class);
                    i.putExtra(AppDetailsActivity.EXTRA_PKG, it.packageName);
                    i.putExtra(AppDetailsActivity.EXTRA_LABEL, it.label);
                    startActivity(i);
                }
            });
            return v;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exec.shutdownNow();
    }
}
