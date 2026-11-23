package com.multiappcloner.pro;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs up this app's own metadata only: clone profile records, network profile metadata
 * (WITHOUT passwords) and IP-check settings. Other apps' private data is never accessed.
 */
public class BackupManager {
    public static final String FORMAT = "multi-app-cloner-pro-backup";

    public static class Summary {
        public boolean ok;
        public String message;
    }

    public static String export(Context c) {
        try {
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("version", 1);
            root.put("exported_at", System.currentTimeMillis());

            JSONArray nets = new JSONArray();
            Map<Long, String> netNames = new HashMap<Long, String>();
            for (NetworkProfile n : new NetworkRepository(c).getAll()) {
                netNames.put(n.id, n.name);
                JSONObject o = new JSONObject();
                o.put("kind", n.kind);
                o.put("name", n.name);
                o.put("host", n.host);
                o.put("port", n.port);
                o.put("username", n.username);
                o.put("provider", n.provider);
                o.put("location", n.location);
                o.put("notes", n.notes);
                nets.put(o);
            }
            root.put("networks", nets);

            JSONArray clones = new JSONArray();
            for (CloneProfile p : new CloneRepository(c).getAll()) {
                JSONObject o = new JSONObject();
                o.put("app_name", p.appName);
                o.put("package_name", p.packageName);
                o.put("clone_name", p.cloneName);
                o.put("method", p.method);
                o.put("status", p.status);
                o.put("created_at", p.createdAt);
                o.put("note", p.note);
                o.put("network", netNames.containsKey(p.networkProfileId) ? netNames.get(p.networkProfileId) : "");
                clones.put(o);
            }
            root.put("clones", clones);

            JSONObject prefs = new JSONObject();
            prefs.put("ipv4_endpoint", AppPrefs.ipv4Endpoint(c));
            prefs.put("ipv6_endpoint", AppPrefs.ipv6Endpoint(c));
            prefs.put("timeout_ms", AppPrefs.timeoutMs(c));
            root.put("preferences", prefs);
            return root.toString(2);
        } catch (Exception e) {
            return null;
        }
    }

    public static Summary restore(Context c, String json) {
        Summary s = new Summary();
        try {
            JSONObject root = new JSONObject(json);
            if (!FORMAT.equals(root.optString("format"))) {
                s.message = "This file is not a Multi App Cloner Pro backup.";
                return s;
            }
            NetworkRepository nr = new NetworkRepository(c);
            CloneRepository cr = new CloneRepository(c);
            Map<String, Long> netIds = new HashMap<String, Long>();
            for (NetworkProfile n : nr.getAll()) netIds.put(n.name.toLowerCase(), n.id);

            int netAdded = 0, netSkipped = 0, cloneAdded = 0, cloneSkipped = 0;
            JSONArray nets = root.optJSONArray("networks");
            if (nets != null) {
                for (int i = 0; i < nets.length(); i++) {
                    JSONObject o = nets.getJSONObject(i);
                    NetworkProfile n = new NetworkProfile();
                    n.kind = o.optString("kind", NetworkProfile.KIND_HTTP);
                    if (!NetworkProfile.KIND_HTTP.equals(n.kind) && !NetworkProfile.KIND_SOCKS5.equals(n.kind)
                        && !NetworkProfile.KIND_VPN.equals(n.kind)) { netSkipped++; continue; }
                    n.name = o.optString("name", "").trim();
                    if (n.name.length() == 0 || n.name.length() > 40) { netSkipped++; continue; }
                    n.host = o.optString("host", "");
                    n.port = o.optInt("port", 0);
                    if (n.isProxy() && (ProxyManager.validateHost(n.host) != null || n.port < 1 || n.port > 65535)) { netSkipped++; continue; }
                    n.username = o.optString("username", "");
                    n.provider = o.optString("provider", "");
                    n.location = o.optString("location", "");
                    n.notes = o.optString("notes", "");
                    long id = nr.insert(n);
                    if (id > 0) { netAdded++; netIds.put(n.name.toLowerCase(), id); } else netSkipped++;
                }
            }
            JSONArray clones = root.optJSONArray("clones");
            if (clones != null) {
                for (int i = 0; i < clones.length(); i++) {
                    JSONObject o = clones.getJSONObject(i);
                    CloneProfile p = new CloneProfile();
                    p.appName = o.optString("app_name", "");
                    p.packageName = o.optString("package_name", "");
                    p.cloneName = o.optString("clone_name", "");
                    if (p.appName.length() == 0 || p.packageName.length() == 0 || CloneRepository.validateName(p.cloneName) != null) { cloneSkipped++; continue; }
                    p.method = CloneRepository.METHOD_NONE;
                    p.status = CloneRepository.STATUS_RECORD_ONLY; // a restored profile has no installed clone yet
                    p.createdAt = o.optLong("created_at", System.currentTimeMillis());
                    p.note = o.optString("note", "");
                    String net = o.optString("network", "");
                    Long nid = net.length() == 0 ? null : netIds.get(net.toLowerCase());
                    p.networkProfileId = nid == null ? -1 : nid;
                    long id = cr.insert(p);
                    if (id > 0) { cloneAdded++; new LogRepository(c).add(id, "Restored from backup"); } else cloneSkipped++;
                }
            }
            s.ok = true;
            s.message = "Restored " + cloneAdded + " clone profile(s) and " + netAdded + " network profile(s). Skipped "
                + cloneSkipped + " clone(s) and " + netSkipped + " network profile(s) (duplicates or invalid). "
                + "Proxy passwords are never backed up: re-enter them if needed.";
            return s;
        } catch (Exception e) {
            s.message = "Could not read the backup file: " + e.getMessage();
            return s;
        }
    }
}
