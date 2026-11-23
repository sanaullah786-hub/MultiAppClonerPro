package com.multiappcloner.pro;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.net.NetworkInterface;
import java.util.Enumeration;

public class NetUtil {
    public static boolean isOnline(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    public static String connectionType(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return "None";
            String t = ni.getTypeName();
            if ("WIFI".equalsIgnoreCase(t)) return "Wi-Fi";
            if ("MOBILE".equalsIgnoreCase(t)) return "Mobile data";
            return t;
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /** Detects a VPN by network interface name (tun/ppp/tap/wg). May be unreliable on newer Android versions. */
    public static boolean isVpnActive() {
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            while (e != null && e.hasMoreElements()) {
                NetworkInterface i = e.nextElement();
                String n = i.getName();
                if (i.isUp() && (n.startsWith("tun") || n.startsWith("ppp") || n.startsWith("tap") || n.startsWith("wg"))) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
