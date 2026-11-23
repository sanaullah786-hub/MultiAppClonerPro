package com.multiappcloner.pro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

/** Fetches the public IP seen by a configurable HTTPS endpoint. Never returns a made-up address. */
public class IpChecker {
    public static class Result {
        public boolean ok;
        public String ip;
        public String error;
        public long time;
    }

    private static boolean validIp(String s) {
        if (s == null) return false;
        if (s.matches("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")) {
            for (String part : s.split("\\.")) if (Integer.parseInt(part) > 255) return false;
            return true;
        }
        return s.indexOf(':') >= 0 && s.matches("^[0-9a-fA-F:.]{2,45}$");
    }

    public static Result fetch(String endpoint, Proxy proxy, final String user, final String pass, int timeoutMs) {
        Result r = new Result();
        r.time = System.currentTimeMillis();
        HttpURLConnection c = null;
        boolean authSet = false;
        try {
            URL u = new URL(endpoint);
            if (!"https".equalsIgnoreCase(u.getProtocol())) {
                r.error = "Endpoint must use https.";
                return r;
            }
            if (proxy != null && user != null && user.length() > 0) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, (pass == null ? "" : pass).toCharArray());
                    }
                });
                authSet = true;
            }
            c = (HttpURLConnection) (proxy == null ? u.openConnection() : u.openConnection(proxy));
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "MultiAppClonerPro");
            int code = c.getResponseCode();
            if (code != 200) {
                r.error = "Server returned HTTP " + code + ".";
                return r;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line = br.readLine();
            br.close();
            String ip = line == null ? "" : line.trim();
            if (!validIp(ip)) {
                r.error = "Invalid response from the IP endpoint.";
                return r;
            }
            r.ok = true;
            r.ip = ip;
        } catch (UnknownHostException e) {
            r.error = "DNS error: could not resolve the host.";
        } catch (SocketTimeoutException e) {
            r.error = "Timed out after " + (timeoutMs / 1000) + " s.";
        } catch (ConnectException e) {
            r.error = "Connection failed: " + e.getMessage();
        } catch (IOException e) {
            r.error = "Network error: " + e.getMessage();
        } catch (Exception e) {
            r.error = "Error: " + e.getMessage();
        } finally {
            if (c != null) c.disconnect();
            if (authSet) Authenticator.setDefault(null);
        }
        return r;
    }
}
