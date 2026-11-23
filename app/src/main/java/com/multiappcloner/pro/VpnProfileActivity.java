package com.multiappcloner.pro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class VpnProfileActivity extends Screen {
    private NetworkRepository repo;
    private String provider = "";
    private EditText name, location, notes;
    private TextView error;
    private Button providerBtn;

    @Override protected String screenTitle() { return "VPN Profile"; }
    @Override protected boolean rebuildOnResume() { return false; }
    @Override protected void onReady() { repo = new NetworkRepository(this); }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout n = Ui.card(this);
        n.addView(Ui.badge(this, "REFERENCE ONLY", Ui.ORANGE));
        n.addView(Ui.text(this, "This app cannot create VPN tunnels or connect to providers. A VPN profile stores which provider app you use "
            + "so you can open it quickly. Connection status is never claimed here. No VPN credentials are collected.", 12, Ui.TEXT2, false));
        Ui.add(body, n);
        Ui.add(body, Ui.section(this, "Profile name"));
        name = Ui.input(this, "e.g. Work VPN", "", false);
        Ui.add(body, name);
        Ui.add(body, Ui.section(this, "Provider"));
        providerBtn = Ui.button(this, "Provider: Custom (tap to choose)", false, new View.OnClickListener() {
            public void onClick(View v) { chooseProvider(); }
        });
        Ui.add(body, providerBtn);
        Ui.add(body, Ui.section(this, "Server location (your own label)"));
        location = Ui.input(this, "e.g. Germany", "", false);
        Ui.add(body, location);
        Ui.add(body, Ui.section(this, "Config reference (optional)"));
        notes = Ui.input(this, "e.g. config file name", "", false);
        Ui.add(body, notes);
        error = Ui.text(this, "", 12, Ui.RED, false);
        Ui.add(body, error);
        Ui.add(body, Ui.button(this, "Save profile", true, new View.OnClickListener() {
            public void onClick(View v) { save(); }
        }));
    }

    private void chooseProvider() {
        final List<String> names = new ArrayList<String>();
        names.add("Custom");
        for (Providers.P p : Providers.ALL) names.add(p.name);
        new AlertDialog.Builder(this).setTitle("Provider").setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                provider = which == 0 ? "" : names.get(which);
                providerBtn.setText("Provider: " + (which == 0 ? "Custom" : names.get(which)) + " (tap to change)");
            }
        }).show();
    }

    private void save() {
        error.setText("");
        String n = name.getText().toString().trim();
        if (n.length() == 0) { error.setText("Enter a profile name."); return; }
        if (n.length() > 40) { error.setText("Profile name must be at most 40 characters."); return; }
        NetworkProfile p = new NetworkProfile();
        p.kind = NetworkProfile.KIND_VPN;
        p.name = n;
        p.provider = provider;
        p.location = location.getText().toString().trim();
        p.notes = notes.getText().toString().trim();
        long r = repo.insert(p);
        if (r == NetworkRepository.DUPLICATE) error.setText("A network profile with this name already exists.");
        else if (r < 0) error.setText("Could not save the profile (storage error). Nothing was saved.");
        else finish();
    }
}
