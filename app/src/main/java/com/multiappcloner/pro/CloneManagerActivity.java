package com.multiappcloner.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
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
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CloneManagerActivity extends Activity {
    private CloneRepository repo;
    private final List<CloneProfile> all = new ArrayList<CloneProfile>();
    private final List<CloneProfile> shown = new ArrayList<CloneProfile>();
    private CloneAdapter adapter;
    private EditText search;
    private TextView count, btnSort;
    private View emptyBox;
    private TextView emptyTitle, emptyText;
    private ListView list;
    private int sortMode = 0;
    private NetworkRepository netRepo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiUtil.applyDarkBars(this);
        setContentView(R.layout.activity_clone_manager);
        repo = new CloneRepository(this);
        netRepo = new NetworkRepository(this);

        search = (EditText) findViewById(R.id.search);
        count = (TextView) findViewById(R.id.count);
        btnSort = (TextView) findViewById(R.id.btnSort);
        emptyBox = findViewById(R.id.emptyBox);
        emptyTitle = (TextView) findViewById(R.id.emptyTitle);
        emptyText = (TextView) findViewById(R.id.emptyText);
        list = (ListView) findViewById(R.id.list);
        adapter = new CloneAdapter();
        list.setAdapter(adapter);

        View.OnClickListener add = new View.OnClickListener() {
            public void onClick(View v) { Screens.createClone(CloneManagerActivity.this); }
        };
        findViewById(R.id.btnAdd).setOnClickListener(add);
        findViewById(R.id.btnEmptyAdd).setOnClickListener(add);
        btnSort.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sortMode = (sortMode + 1) % 3; applyFilter(); }
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            public void onTextChanged(CharSequence s, int a, int b2, int c) { applyFilter(); }
            public void afterTextChanged(Editable e) { }
        });
        BottomNav.setup(this, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        all.clear();
        all.addAll(repo.getAll());
        applyFilter();
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (CloneProfile p : all) {
            if (q.length() == 0 || p.cloneName.toLowerCase(Locale.getDefault()).contains(q)
                || p.appName.toLowerCase(Locale.getDefault()).contains(q)) shown.add(p);
        }
        Collections.sort(shown, new Comparator<CloneProfile>() {
            public int compare(CloneProfile a, CloneProfile b) {
                if (sortMode == 1) return a.cloneName.compareToIgnoreCase(b.cloneName);
                if (sortMode == 2) {
                    int r = a.appName.compareToIgnoreCase(b.appName);
                    if (r != 0) return r;
                    return a.cloneName.compareToIgnoreCase(b.cloneName);
                }
                return a.createdAt == b.createdAt ? 0 : (a.createdAt < b.createdAt ? 1 : -1);
            }
        });
        btnSort.setText(sortMode == 0 ? "Sort: Newest" : (sortMode == 1 ? "Sort: Name" : "Sort: By app"));
        count.setText(shown.size() + (shown.size() == 1 ? " profile" : " profiles"));
        adapter.notifyDataSetChanged();
        if (shown.isEmpty()) {
            boolean noData = all.isEmpty();
            emptyTitle.setText(noData ? "No clone profiles yet" : "No matching clones");
            emptyText.setText(noData ? "Select an installed app to create a clone." : "Try a different search.");
            findViewById(R.id.btnEmptyAdd).setVisibility(noData ? View.VISIBLE : View.GONE);
            emptyBox.setVisibility(View.VISIBLE);
        } else {
            emptyBox.setVisibility(View.GONE);
        }
    }

    private static String date(long t) {
        return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(t));
    }

    private void showMenu(final CloneProfile p) {
        final boolean active = CloneRepository.STATUS_ACTIVE.equals(p.status);
        Drawable ic;
        try {
            ic = getPackageManager().getApplicationIcon(p.packageName);
        } catch (Exception e) {
            ic = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
        }
        String how = CloneRepository.METHOD_VIRTUAL.equals(p.method) ? "Virtual clone" : CloneRepository.METHOD_REAL.equals(p.method) ? "Installed clone" : "Profile";
        Sheet sh = Sheet.actions(this, ic, p.cloneName, p.appName + "  •  " + how);
        if (active) {
            sh.item("▶", "Open clone", "Run it now", Ui.GREEN, new Runnable() {
                public void run() { CloneState.open(CloneManagerActivity.this, p); }
            });
        }
        sh.item("ℹ", "View details", "Storage, Google services, activity log", Ui.CYAN, new Runnable() {
            public void run() { showDetails(p); }
        });
        sh.item("✎", "Rename", "Change the clone's name", Ui.PURPLE, new Runnable() {
            public void run() { showRename(p); }
        });
        sh.item("🗑", "Delete", "Remove this clone and its data", Ui.RED, new Runnable() {
            public void run() { showDelete(p); }
        });
        sh.show();
    }

    private void showDetails(CloneProfile p) {
        Intent i = new Intent(this, CloneDetailsActivity.class);
        i.putExtra(CloneDetailsActivity.EXTRA_ID, p.id);
        startActivity(i);
    }

    private void showRename(final CloneProfile p) {
        Sheet.input(this, "Rename clone", p.cloneName, "Save", new Sheet.TextResult() {
            public void onText(String n) {
                String err = CloneRepository.validateName(n);
                if (err != null) { Toast.makeText(CloneManagerActivity.this, err, Toast.LENGTH_LONG).show(); return; }
                long r = repo.rename(p, n);
                if (r == CloneRepository.DUPLICATE) {
                    Toast.makeText(CloneManagerActivity.this, "A clone with this name already exists for " + p.appName + ".", Toast.LENGTH_LONG).show();
                } else if (r < 0) {
                    Toast.makeText(CloneManagerActivity.this, "Rename failed. Nothing was changed.", Toast.LENGTH_LONG).show();
                } else {
                    reload();
                }
            }
        });
    }

    private void showDelete(final CloneProfile p) {
        boolean virt = CloneRepository.METHOD_VIRTUAL.equals(p.method);
        Sheet.confirm(this, "Delete \"" + p.cloneName + "\"?",
            virt ? "The virtual clone and all data saved inside it are erased. The original app and other clones are not touched."
                 : "It is removed from this list. If the clone app is installed it stays on your device until you uninstall it from Clone details.",
            "Delete", true, new Runnable() {
                public void run() {
                    CloneState.cleanup(p);
                    if (repo.delete(p.id)) reload();
                    else Toast.makeText(CloneManagerActivity.this, "Delete failed. Nothing was changed.", Toast.LENGTH_LONG).show();
                }
            });
    }

    private class CloneAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int p) { return shown.get(p); }
        public long getItemId(int p) { return shown.get(p).id; }

        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(CloneManagerActivity.this).inflate(R.layout.item_clone, parent, false);
            final CloneProfile p = shown.get(pos);
            Drawable ic;
            try {
                ic = getPackageManager().getApplicationIcon(p.packageName);
            } catch (PackageManager.NameNotFoundException e) {
                ic = getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            }
            ((ImageView) v.findViewById(R.id.cloneIcon)).setImageDrawable(ic);
            ((TextView) v.findViewById(R.id.cloneName)).setText(p.cloneName);
            ((TextView) v.findViewById(R.id.cloneLine2)).setText(p.appName + "  •  Method: " + (CloneRepository.METHOD_VIRTUAL.equals(p.method) ? "virtual" : CloneRepository.METHOD_REAL.equals(p.method) ? "installed clone" : "not assigned"));
            String net = "Default";
            if (p.networkProfileId > 0) {
                NetworkProfile np = netRepo.get(p.networkProfileId);
                if (np != null) net = np.name;
            }
            ((TextView) v.findViewById(R.id.cloneLine3)).setText(date(p.createdAt) + "  •  Network: " + net);
            ((TextView) v.findViewById(R.id.cloneStatus)).setText(CloneState.label(CloneState.sync(CloneManagerActivity.this, repo, p)));
            View more = v.findViewById(R.id.btnMore);
            more.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) { showMenu(p); }
            });
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) { showMenu(p); }
            });
            return v;
        }
    }
}
