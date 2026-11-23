package com.multiappcloner.pro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Screen {
    @Override protected String screenTitle() { return "Settings"; }
    @Override protected boolean showNav() { return true; }
    @Override protected int navIndex() { return 3; }
    @Override protected boolean showBack() { return false; }

    @Override
    protected void build(LinearLayout body) {
        Ui.add(body, Ui.section(this, "IP check"));
        LinearLayout c = Ui.card(this);
        c.addView(Ui.row(this, "IPv4 endpoint", AppPrefs.ipv4Endpoint(this)));
        c.addView(Ui.row(this, "IPv6 endpoint", AppPrefs.ipv6Endpoint(this)));
        c.addView(Ui.row(this, "Timeout", (AppPrefs.timeoutMs(this) / 1000) + " s"));
        Ui.add(body, c);
        Ui.add(body, Ui.button(this, "Edit IPv4 endpoint", false, new View.OnClickListener() {
            public void onClick(View v) { editEndpoint(true); }
        }));
        Ui.add(body, Ui.button(this, "Edit IPv6 endpoint", false, new View.OnClickListener() {
            public void onClick(View v) { editEndpoint(false); }
        }));
        Ui.add(body, Ui.button(this, "Change timeout", false, new View.OnClickListener() {
            public void onClick(View v) { editTimeout(); }
        }));

        Ui.add(body, Ui.section(this, "Defaults"));
        String def = "Default connection";
        long id = AppPrefs.defaultNetwork(this);
        if (id > 0) {
            NetworkProfile n = new NetworkRepository(this).get(id);
            if (n != null) def = n.name;
        }
        LinearLayout d = Ui.card(this);
        d.addView(Ui.row(this, "Network for new clones", def));
        d.addView(Ui.text(this, "Only stored as metadata on new profiles. The default clone method is always \"not assigned\" because no cloning engine exists.", 11, Ui.TEXT2, false));
        Ui.add(body, d);
        Ui.add(body, Ui.button(this, "Choose default network profile", false, new View.OnClickListener() {
            public void onClick(View v) { chooseDefaultNetwork(); }
        }));

        Ui.add(body, Ui.section(this, "Data and diagnostics"));
        Ui.add(body, Ui.button(this, "Data management", false, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, DataManagementActivity.class)); }
        }));
        Ui.add(body, Ui.button(this, "Backup & restore", false, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, BackupActivity.class)); }
        }));
        Ui.add(body, Ui.button(this, "Diagnostics", false, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, DiagnosticsActivity.class)); }
        }));

        Ui.add(body, Ui.section(this, "About"));
        Ui.add(body, Ui.button(this, "Privacy information", false, new View.OnClickListener() {
            public void onClick(View v) { InfoActivity.open(SettingsActivity.this, "Privacy", PRIVACY); }
        }));
        Ui.add(body, Ui.button(this, "About this app", false, new View.OnClickListener() {
            public void onClick(View v) { InfoActivity.open(SettingsActivity.this, "About", ABOUT); }
        }));
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        Ui.add(body, Ui.text(this, "Multi App Cloner Pro  •  v" + ver, 11, Ui.TEXT2, false));
    }

    private static final String PRIVACY =
        "What this app stores: clone profiles, network profile metadata, notes and logs, in its own private database on this device.\n\n"
        + "Proxy passwords are encrypted with an Android Keystore key (Android 6.0+). On older versions passwords are not stored.\n\n"
        + "Network use: the app only makes requests when you run an IP check or a proxy test. These go to the IP endpoint you configured (and through the proxy you choose). "
        + "That endpoint can see your public IP.\n\n"
        + "To clone an app the app reads that app's installed APK file (the same file anyone can extract) and writes a modified copy into its own cache, which is deleted right after the install is handed over. It does not read other apps' private data, does not collect target-app passwords, does not capture other apps' content and does not route traffic secretly. "
        + "No analytics or advertising are included.\n\n"
        + "You can delete everything under Settings > Data management.";

    private static final String ABOUT =
        "Multi App Cloner Pro creates real clones of installed apps by repackaging: it copies the app's APK, gives it a new package name, "
        + "signs it with a key kept in this device's Keystore and installs it through Android's installer (you confirm each install). "
        + "The clone is a separate app with its own data and its own launcher entry.\n\n"
        + "Limits: split-APK/bundle apps, system apps and security-sensitive apps are refused. Apps that verify their own signature or package name, "
        + "or depend on Google Sign-In, Firebase or Play Integrity, may install but not work. No virtual-app engine is included. "
        + "VPN profiles are references to provider apps; this app creates no VPN tunnel.\n\n"
        + "Theme, language and notification settings are not implemented.";

    private void editEndpoint(final boolean v4) {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setText(v4 ? AppPrefs.ipv4Endpoint(this) : AppPrefs.ipv6Endpoint(this));
        new AlertDialog.Builder(this).setTitle(v4 ? "IPv4 endpoint (https)" : "IPv6 endpoint (https)").setView(in)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    if (v4) AppPrefs.setIpv4Endpoint(SettingsActivity.this, AppPrefs.DEF_V4);
                    else AppPrefs.setIpv6Endpoint(SettingsActivity.this, AppPrefs.DEF_V6);
                    rebuild();
                }
            })
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String s = in.getText().toString().trim();
                    try {
                        URL u = new URL(s);
                        if (!"https".equalsIgnoreCase(u.getProtocol()) || u.getHost().length() == 0 || s.length() > 200) throw new Exception();
                    } catch (Exception e) {
                        Toast.makeText(SettingsActivity.this, "Enter a valid https:// URL.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (v4) AppPrefs.setIpv4Endpoint(SettingsActivity.this, s);
                    else AppPrefs.setIpv6Endpoint(SettingsActivity.this, s);
                    rebuild();
                }
            }).show();
    }

    private void editTimeout() {
        final int[] ms = {5000, 8000, 15000};
        new AlertDialog.Builder(this).setTitle("Timeout").setItems(new String[]{"5 seconds", "8 seconds", "15 seconds"}, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                AppPrefs.setTimeoutMs(SettingsActivity.this, ms[which]);
                rebuild();
            }
        }).show();
    }

    private void chooseDefaultNetwork() {
        final List<NetworkProfile> all = new NetworkRepository(this).getAll();
        List<String> names = new ArrayList<String>();
        names.add("Default connection");
        for (NetworkProfile n : all) names.add((n.isVpn() ? "VPN: " : "Proxy: ") + n.name);
        new AlertDialog.Builder(this).setTitle("Default network profile").setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                AppPrefs.setDefaultNetwork(SettingsActivity.this, which == 0 ? -1 : all.get(which - 1).id);
                rebuild();
            }
        }).show();
    }
}
