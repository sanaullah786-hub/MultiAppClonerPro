package com.multiappcloner.pro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class IpCheckerActivity extends Screen {
    private IpChecker.Result r4, r6;
    private String via = "Default connection";
    private long lastOk = 0;
    private boolean busy = false;

    @Override protected String screenTitle() { return "IP Checker"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout big = Ui.card(this);
        big.addView(Ui.text(this, "Public IPv4", 12, Ui.TEXT2, false));
        String t;
        int col = Ui.TEXT;
        if (busy) t = "Checking...";
        else if (r4 == null) t = "Not checked yet";
        else if (r4.ok) { t = r4.ip; col = Ui.CYAN; }
        else { t = "Unavailable"; col = Ui.RED; }
        big.addView(Ui.text(this, t, 24, col, true));
        if (!busy && r4 != null && !r4.ok) big.addView(Ui.text(this, r4.error, 12, Ui.RED, false));
        Ui.add(body, big);

        LinearLayout d = Ui.card(this);
        String v6;
        if (busy || r6 == null) v6 = "Not checked yet";
        else v6 = r6.ok ? r6.ip : "Unavailable (" + r6.error + ")";
        d.addView(Ui.row(this, "Public IPv6", v6));
        d.addView(Ui.row(this, "Connection", NetUtil.isOnline(this) ? NetUtil.connectionType(this) : "None"));
        d.addView(Ui.row(this, "VPN interface", NetUtil.isVpnActive() ? "Detected" : "Not detected"));
        d.addView(Ui.row(this, "Network profile", via));
        d.addView(Ui.row(this, "Endpoint", AppPrefs.ipv4Endpoint(this)));
        d.addView(Ui.row(this, "Last successful check", lastOk == 0 ? "None yet"
            : new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(new Date(lastOk))));
        boolean verified = (r4 != null && r4.ok) || (r6 != null && r6.ok);
        d.addView(Ui.row(this, "Verification", busy ? "Running" : (r4 == null ? "Not run" : (verified ? "Verified by a real network request" : "Not verified"))));
        Ui.add(body, d);

        Ui.add(body, Ui.button(this, busy ? "Checking..." : "Check again", true, new View.OnClickListener() {
            public void onClick(View v) { if (!busy) run(null); }
        }));
        Ui.add(body, Ui.button(this, "Check through a proxy profile", false, new View.OnClickListener() {
            public void onClick(View v) { if (!busy) pickProxy(); }
        }));
        Ui.add(body, Ui.text(this, "The address shown is what the check endpoint sees, not your local LAN IP. "
            + "A public IP alone does not prove anonymity.", 11, Ui.TEXT2, false));
    }

    private void pickProxy() {
        final List<NetworkProfile> proxies = new ArrayList<NetworkProfile>();
        for (NetworkProfile n : new NetworkRepository(this).getAll()) if (n.isProxy()) proxies.add(n);
        if (proxies.isEmpty()) { info("No proxy profiles", "Add a proxy profile in the Network Manager first."); return; }
        String[] names = new String[proxies.size()];
        for (int i = 0; i < names.length; i++) names[i] = proxies.get(i).name;
        new AlertDialog.Builder(this).setTitle("Proxy profile").setItems(names, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { run(proxies.get(which)); }
        }).show();
    }

    private void run(final NetworkProfile proxy) {
        busy = true;
        via = proxy == null ? "Default connection" : "Proxy: " + proxy.name;
        rebuild();
        exec.execute(new Runnable() {
            public void run() {
                final android.content.Context c = getApplicationContext();
                final IpChecker.Result a, b;
                if (!NetUtil.isOnline(c)) {
                    a = new IpChecker.Result();
                    a.error = "No internet connection.";
                    a.time = System.currentTimeMillis();
                    b = a;
                } else {
                    java.net.Proxy px = proxy == null ? null : ProxyManager.proxyFor(proxy);
                    String u = proxy == null ? null : proxy.username;
                    String pw = proxy == null ? null : ProxyManager.passwordFor(proxy);
                    a = IpChecker.fetch(AppPrefs.ipv4Endpoint(c), px, u, pw, AppPrefs.timeoutMs(c));
                    b = IpChecker.fetch(AppPrefs.ipv6Endpoint(c), px, u, pw, AppPrefs.timeoutMs(c));
                }
                ui(new Runnable() {
                    public void run() {
                        r4 = a;
                        r6 = b;
                        busy = false;
                        if (a.ok) lastOk = a.time;
                        else if (b.ok) lastOk = b.time;
                        rebuild();
                    }
                });
            }
        });
    }
}
