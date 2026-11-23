package com.multiappcloner.pro;

import java.util.ArrayList;
import java.util.List;

/**
 * Directory of real VPN clients/providers with links to their own documentation.
 * No provider APIs are called: this app cannot connect, purchase or verify subscriptions.
 */
public class Providers {
    public static class P {
        public final String name, pkg, docs, tier;
        P(String n, String p, String d, String t) { name = n; pkg = p; docs = d; tier = t; }
    }

    public static final List<P> ALL = new ArrayList<P>();

    static {
        ALL.add(new P("WireGuard", "com.wireguard.android", "https://www.wireguard.com/",
            "Open-source protocol and client. You import a config from your own server or provider."));
        ALL.add(new P("OpenVPN for Android", "de.blinkt.openvpn", "https://openvpn.net/community-resources/",
            "Open-source client. You import a .ovpn file from your provider."));
        ALL.add(new P("Proton VPN", "ch.protonvpn.android", "https://protonvpn.com/support/",
            "Provider with free and paid plans (check the provider's site for current terms)."));
        ALL.add(new P("Cloudflare WARP", "com.cloudflare.onedotonedotonedotone", "https://one.one.one.one/",
            "Provider client; free and paid options (check the provider's site)."));
        ALL.add(new P("Mullvad VPN", "net.mullvad.mullvadvpn", "https://mullvad.net/en/help",
            "Paid provider (check the provider's site for pricing)."));
    }

    public static P byName(String name) {
        for (P p : ALL) if (p.name.equals(name)) return p;
        return null;
    }
}
