package com.multiappcloner.pro;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

public class NetworkManagerActivity extends Screen {
    private NetworkRepository repo;
    @Override protected String screenTitle() { return "Network Manager"; }
    @Override protected boolean showNav() { return true; }
    @Override protected int navIndex() { return 2; }
    @Override protected boolean showBack() { return false; }
    @Override protected void onReady() { repo = new NetworkRepository(this); }

    @Override
    protected void build(LinearLayout body) {
        LinearLayout st = Ui.card(this);
        st.addView(Ui.row(this, "Internet", NetUtil.isOnline(this) ? "Connected (" + NetUtil.connectionType(this) + ")" : "Not connected"));
        st.addView(Ui.row(this, "VPN interface", NetUtil.isVpnActive() ? "Detected" : "Not detected"));
        st.addView(Ui.text(this, "VPN detection uses network interface names and may be unavailable on newer Android versions. This app does not create VPN tunnels.", 11, Ui.TEXT2, false));
        Ui.add(body, st);
        Ui.add(body, Ui.button(this, "Open Android VPN settings", false, new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent("android.settings.VPN_SETTINGS")); }
                catch (Exception e) { info("Not available", "Could not open VPN settings on this device."); }
            }
        }));
        Ui.add(body, Ui.button(this, "Public IP checker", true, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(NetworkManagerActivity.this, IpCheckerActivity.class)); }
        }));

        List<NetworkProfile> all = repo.getAll();
        Ui.add(body, Ui.section(this, "VPN profiles (provider references)"));
        boolean anyVpn = false;
        for (final NetworkProfile n : all) {
            if (!n.isVpn()) continue;
            anyVpn = true;
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, n.name, 15, Ui.TEXT, true));
            c.addView(Ui.text(this, (n.provider.length() == 0 ? "Custom" : n.provider) + (n.location.length() > 0 ? "  •  " + n.location : ""), 12, Ui.TEXT2, false));
            if (n.notes.length() > 0) c.addView(Ui.text(this, n.notes, 11, Ui.TEXT2, false));
            c.addView(Ui.badge(this, "STATUS: NOT CONNECTED BY THIS APP", Ui.ORANGE));
            c.addView(Ui.button(this, "Open provider app", false, new View.OnClickListener() {
                public void onClick(View v) { openProvider(n); }
            }));
            c.addView(Ui.button(this, "Delete", false, new View.OnClickListener() {
                public void onClick(View v) { deleteProfile(n); }
            }));
            Ui.add(body, c);
        }
        if (!anyVpn) Ui.add(body, Ui.text(this, "No VPN profiles saved.", 13, Ui.TEXT2, false));
        Ui.add(body, Ui.button(this, "Add VPN profile", false, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(NetworkManagerActivity.this, VpnProfileActivity.class)); }
        }));
        Ui.add(body, Ui.button(this, "Providers & documentation", false, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(NetworkManagerActivity.this, ProvidersActivity.class)); }
        }));

        Ui.add(body, Ui.section(this, "Proxy profiles"));
        boolean anyProxy = false;
        for (final NetworkProfile n : all) {
            if (!n.isProxy()) continue;
            anyProxy = true;
            LinearLayout c = Ui.card(this);
            c.addView(Ui.text(this, n.name, 15, Ui.TEXT, true));
            c.addView(Ui.text(this, (NetworkProfile.KIND_SOCKS5.equals(n.kind) ? "SOCKS5  " : "HTTP  ") + n.host + ":" + n.port
                + (n.username.length() > 0 ? "  •  user " + n.username : ""), 12, Ui.TEXT2, false));
            c.addView(Ui.button(this, "Test connection", false, new View.OnClickListener() {
                public void onClick(View v) { testProxy(n); }
            }));
            c.addView(Ui.button(this, "Edit", false, new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i = new Intent(NetworkManagerActivity.this, ProxySettingsActivity.class);
                    i.putExtra(ProxySettingsActivity.EXTRA_ID, n.id);
                    startActivity(i);
                }
            }));
            c.addView(Ui.button(this, "Delete", false, new View.OnClickListener() {
                public void onClick(View v) { deleteProfile(n); }
            }));
            Ui.add(body, c);
        }
        if (!anyProxy) Ui.add(body, Ui.text(this, "No proxy profiles saved.", 13, Ui.TEXT2, false));
        Ui.add(body, Ui.button(this, "Add proxy profile", false, new View.OnClickListener() {
            public void onClick(View v) { startActivity(new Intent(NetworkManagerActivity.this, ProxySettingsActivity.class)); }
        }));
        Ui.add(body, Ui.text(this, "A proxy only affects traffic that is actually sent through it. Android normally allows one active VPN at a time, "
            + "so different VPNs for different clones are not possible in this app.", 11, Ui.TEXT2, false));
    }

    private void openProvider(NetworkProfile n) {
        Providers.P p = Providers.byName(n.provider);
        if (p == null) { info("Custom provider", "No provider app is linked to this profile. Open your VPN app manually."); return; }
        Intent i = getPackageManager().getLaunchIntentForPackage(p.pkg);
        if (i == null) info("Not installed", p.name + " is not installed (or not visible to this app).");
        else startActivity(i);
    }

    private void deleteProfile(final NetworkProfile n) {
        int used = repo.clonesUsing(n.id);
        String msg = "\"" + n.name + "\" will be deleted." + (used > 0 ? " " + used + " clone profile(s) assigned to it will be reset to the default connection." : "");
        confirm("Delete network profile?", msg, "Delete", new Runnable() {
            public void run() {
                if (!repo.delete(n.id)) info("Delete failed", "Nothing was changed.");
                rebuild();
            }
        });
    }

    private void testProxy(final NetworkProfile n) {
        info("Testing...", "Sending a real request through " + n.host + ":" + n.port + ". This can take a few seconds.");
        exec.execute(new Runnable() {
            public void run() {
                final IpChecker.Result r = ProxyManager.test(getApplicationContext(), n, ProxyManager.passwordFor(n));
                ui(new Runnable() {
                    public void run() {
                        info(r.ok ? "Proxy works" : "Proxy test failed",
                            r.ok ? "The IP endpoint saw this address through the proxy: " + r.ip : r.error);
                    }
                });
            }
        });
    }
}
