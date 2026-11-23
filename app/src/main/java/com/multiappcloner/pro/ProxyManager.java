package com.multiappcloner.pro;

import android.content.Context;

import java.net.InetSocketAddress;
import java.net.Proxy;

/** A proxy only affects traffic that is actually sent through it (here: this app's own test requests). */
public class ProxyManager {
    public static String validateHost(String h) {
        if (h == null || h.trim().length() == 0) return "Enter a host.";
        h = h.trim();
        if (h.length() > 253) return "Host is too long.";
        if (!h.matches("^[A-Za-z0-9._:\\-\\[\\]]+$")) return "Host contains invalid characters.";
        return null;
    }

    public static String validatePort(String p) {
        try {
            int n = Integer.parseInt(p.trim());
            if (n < 1 || n > 65535) return "Port must be between 1 and 65535.";
            return null;
        } catch (Exception e) {
            return "Enter a valid port number.";
        }
    }

    public static Proxy proxyFor(NetworkProfile p) {
        Proxy.Type t = NetworkProfile.KIND_SOCKS5.equals(p.kind) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        return new Proxy(t, InetSocketAddress.createUnresolved(p.host, p.port));
    }

    public static String passwordFor(NetworkProfile p) {
        if (p.passwordEnc == null || p.passwordEnc.length() == 0) return "";
        String d = SecureStorage.decrypt(p.passwordEnc);
        return d == null ? "" : d;
    }

    /** Runs a real request through the proxy. Call from a background thread. */
    public static IpChecker.Result test(Context c, NetworkProfile p, String plainPassword) {
        if (!NetUtil.isOnline(c)) {
            IpChecker.Result r = new IpChecker.Result();
            r.error = "No internet connection.";
            r.time = System.currentTimeMillis();
            return r;
        }
        return IpChecker.fetch(AppPrefs.ipv4Endpoint(c), proxyFor(p), p.username, plainPassword, AppPrefs.timeoutMs(c));
    }
}
