package com.multiappcloner.pro;

public class NetworkProfile {
    public static final String KIND_HTTP = "proxy_http";
    public static final String KIND_SOCKS5 = "proxy_socks5";
    public static final String KIND_VPN = "vpn";

    public long id;
    public String kind = KIND_HTTP;
    public String name = "";
    public String host = "";
    public int port;
    public String username = "";
    public String passwordEnc = "";   // Keystore-encrypted, never plain text
    public String provider = "";
    public String location = "";
    public String notes = "";
    public long createdAt;

    public boolean isProxy() { return !KIND_VPN.equals(kind); }
    public boolean isVpn() { return KIND_VPN.equals(kind); }
}
