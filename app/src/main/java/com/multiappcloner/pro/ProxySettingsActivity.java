package com.multiappcloner.pro;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ProxySettingsActivity extends Screen {
    public static final String EXTRA_ID = "id";
    private NetworkRepository repo;
    private NetworkProfile editing;
    private String kind = NetworkProfile.KIND_HTTP;
    private EditText name, host, port, user, pass;
    private TextView error, result;
    private Button typeBtn;

    @Override protected String screenTitle() { return "Proxy Settings"; }
    @Override protected boolean rebuildOnResume() { return false; }

    @Override
    protected void onReady() {
        repo = new NetworkRepository(this);
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        if (id > 0) {
            editing = repo.get(id);
            if (editing != null) kind = editing.kind;
        }
    }

    @Override
    protected void build(LinearLayout body) {
        NetworkProfile e = editing;
        Ui.add(body, Ui.section(this, "Profile name"));
        name = Ui.input(this, "e.g. Office proxy", e == null ? "" : e.name, false);
        Ui.add(body, name);
        Ui.add(body, Ui.section(this, "Proxy type"));
        typeBtn = Ui.button(this, label(), false, new View.OnClickListener() {
            public void onClick(View v) {
                kind = NetworkProfile.KIND_HTTP.equals(kind) ? NetworkProfile.KIND_SOCKS5 : NetworkProfile.KIND_HTTP;
                typeBtn.setText(label());
            }
        });
        Ui.add(body, typeBtn);
        Ui.add(body, Ui.section(this, "Host and port"));
        host = Ui.input(this, "proxy.example.com", e == null ? "" : e.host, false);
        Ui.add(body, host);
        port = Ui.input(this, "8080", e == null ? "" : String.valueOf(e.port), false);
        port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Ui.add(body, port);
        Ui.add(body, Ui.section(this, "Credentials (optional)"));
        user = Ui.input(this, "Username", e == null ? "" : e.username, false);
        Ui.add(body, user);
        pass = Ui.input(this, e != null && e.passwordEnc.length() > 0 ? "Password (leave empty to keep saved)" : "Password", "", true);
        Ui.add(body, pass);
        Ui.add(body, Ui.text(this, SecureStorage.isSupported()
            ? "Passwords are encrypted with a hardware-backed Android Keystore key and never stored in plain text."
            : "This Android version (below 6.0) has no supported secure storage, so passwords cannot be saved. Use a proxy without credentials.",
            11, Ui.TEXT2, false));
        error = Ui.text(this, "", 12, Ui.RED, false);
        Ui.add(body, error);
        result = Ui.text(this, "", 13, Ui.TEXT2, false);
        Ui.add(body, result);
        Ui.add(body, Ui.button(this, "Test connection", false, new View.OnClickListener() {
            public void onClick(View v) { test(); }
        }));
        Ui.add(body, Ui.button(this, "Save profile", true, new View.OnClickListener() {
            public void onClick(View v) { save(); }
        }));
        Ui.add(body, Ui.text(this, "A proxy only affects traffic that actually uses it. Testing sends one request from this app through the proxy.", 11, Ui.TEXT2, false));
    }

    private String label() {
        return NetworkProfile.KIND_HTTP.equals(kind) ? "Type: HTTP (tap to switch)" : "Type: SOCKS5 (tap to switch)";
    }

    private String validate() {
        String n = name.getText().toString().trim();
        if (n.length() == 0) return "Enter a profile name.";
        if (n.length() > 40) return "Profile name must be at most 40 characters.";
        String h = ProxyManager.validateHost(host.getText().toString());
        if (h != null) return h;
        String p = ProxyManager.validatePort(port.getText().toString());
        if (p != null) return p;
        if (pass.getText().length() > 0 && !SecureStorage.isSupported()) return "Passwords cannot be stored securely on this Android version.";
        if (pass.getText().length() > 0 && user.getText().toString().trim().length() == 0) return "Enter a username for the password.";
        return null;
    }

    private NetworkProfile fromForm() {
        NetworkProfile p = new NetworkProfile();
        p.kind = kind;
        p.name = name.getText().toString().trim();
        p.host = host.getText().toString().trim();
        p.port = Integer.parseInt(port.getText().toString().trim());
        p.username = user.getText().toString().trim();
        return p;
    }

    private void test() {
        error.setText("");
        String err = validate();
        if (err != null) { error.setText(err); return; }
        final NetworkProfile p = fromForm();
        String typed = pass.getText().toString();
        final String pw = typed.length() > 0 ? typed : (editing != null ? ProxyManager.passwordFor(editing) : "");
        result.setText("Testing...");
        exec.execute(new Runnable() {
            public void run() {
                final IpChecker.Result r = ProxyManager.test(getApplicationContext(), p, pw);
                ui(new Runnable() {
                    public void run() {
                        result.setTextColor(r.ok ? Ui.GREEN : Ui.RED);
                        result.setText(r.ok ? "Success: the IP endpoint saw " + r.ip + " through this proxy." : "Failed: " + r.error);
                    }
                });
            }
        });
    }

    private void save() {
        error.setText("");
        String err = validate();
        if (err != null) { error.setText(err); return; }
        NetworkProfile p = fromForm();
        String typed = pass.getText().toString();
        if (typed.length() > 0) {
            String enc = SecureStorage.encrypt(typed);
            if (enc == null) { error.setText("Could not encrypt the password. Nothing was saved."); return; }
            p.passwordEnc = enc;
        } else if (editing != null) {
            p.passwordEnc = editing.passwordEnc;
        }
        long r;
        if (editing != null) {
            p.id = editing.id;
            p.createdAt = editing.createdAt;
            r = repo.update(p);
        } else {
            r = repo.insert(p);
        }
        if (r == NetworkRepository.DUPLICATE) error.setText("A network profile with this name already exists.");
        else if (r < 0) error.setText("Could not save the profile (storage error). Nothing was saved.");
        else finish();
    }
}
