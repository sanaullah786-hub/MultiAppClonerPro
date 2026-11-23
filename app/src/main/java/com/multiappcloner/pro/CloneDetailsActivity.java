package com.multiappcloner.pro;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CloneDetailsActivity extends Screen {
    public static final String EXTRA_ID = "id";
    private CloneRepository repo;
    private NetworkRepository nets;
    private LogRepository logs;
    private long id;

    @Override protected String screenTitle() { return "Clone Details"; }

    @Override
    protected void onReady() {
        repo = new CloneRepository(this);
        nets = new NetworkRepository(this);
        logs = new LogRepository(this);
        id = getIntent().getLongExtra(EXTRA_ID, -1);
    }

    @Override
    protected void build(LinearLayout body) {
        final CloneProfile p = CloneState.sync(this, repo, repo.getById(id));
        if (p == null) {
            Toast.makeText(this, "This profile no longer exists.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        LinearLayout head = Ui.card(this);
        ImageView icon = new ImageView(this);
        try {
            Drawable d = getPackageManager().getApplicationIcon(p.packageName);
            icon.setImageDrawable(d);
        } catch (Exception e) {
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        head.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        head.addView(Ui.text(this, p.cloneName, 18, Ui.TEXT, true));
        head.addView(Ui.text(this, p.appName, 12, Ui.TEXT2, false));
        Ui.add(body, head);

        LinearLayout st = Ui.card(this);
        final boolean active = CloneRepository.STATUS_ACTIVE.equals(p.status);
        final boolean virt = CloneRepository.METHOD_VIRTUAL.equals(p.method);
        if (active && virt) {
            st.addView(Ui.badge(this, "VIRTUAL CLONE", Ui.GREEN));
            st.addView(Ui.text(this, "Runs inside this app with its own private data (virtual user " + p.virtualUser + "). It is not installed in Android.", 12, Ui.TEXT2, false));
        } else if (active) {
            st.addView(Ui.badge(this, "INSTALLED CLONE", Ui.GREEN));
            st.addView(Ui.text(this, "This clone is a separate app on your device (" + p.clonePackage + ") with its own data. It also appears in your launcher.", 12, Ui.TEXT2, false));
        } else if (CloneRepository.STATUS_INSTALLING.equals(p.status)) {
            st.addView(Ui.badge(this, "WAITING FOR INSTALL", Ui.ORANGE));
            st.addView(Ui.text(this, "The clone was built and sent to Android's installer. Confirm the system dialog. If it never appeared or was dismissed, use Install clone below.", 12, Ui.TEXT2, false));
        } else if (CloneRepository.STATUS_FAILED.equals(p.status)) {
            st.addView(Ui.badge(this, "INSTALL FAILED", Ui.RED));
            List<String> last = logs.list(p.id, 1);
            st.addView(Ui.text(this, last.isEmpty() ? "The installer reported an error." : last.get(0), 12, Ui.TEXT2, false));
        } else if (CloneRepository.STATUS_REMOVED.equals(p.status)) {
            st.addView(Ui.badge(this, "CLONE APP REMOVED", Ui.ORANGE));
            st.addView(Ui.text(this, "The clone app is no longer installed. Its data went with it. You can install a fresh clone from this profile.", 12, Ui.TEXT2, false));
        } else {
            st.addView(Ui.badge(this, "PROFILE RECORD ONLY", Ui.ORANGE));
            st.addView(Ui.text(this, "No clone app exists for this profile yet (for example it was restored from a backup). Use Install clone to create it.", 12, Ui.TEXT2, false));
        }
        Ui.add(body, st);
        if (active) {
            Ui.add(body, Ui.button(this, "Open clone", true, new View.OnClickListener() {
                public void onClick(View v) {
                    if (!CloneState.launch(CloneDetailsActivity.this, p))
                        Toast.makeText(CloneDetailsActivity.this, "Could not open the clone.", Toast.LENGTH_LONG).show();
                }
            }));
            if (virt) {
                Ui.add(body, Ui.button(this, "Stop clone", false, new View.OnClickListener() {
                    public void onClick(View v) {
                        VirtualEngine.stop(p.packageName, p.virtualUser);
                        Toast.makeText(CloneDetailsActivity.this, "Clone stopped.", Toast.LENGTH_SHORT).show();
                    }
                }));
                Ui.add(body, Ui.button(this, "Clear clone data", false, new View.OnClickListener() {
                    public void onClick(View v) {
                        confirm("Clear data?", "Everything this clone saved (logins, files, databases) is erased. The original app is not touched.", "Clear", new Runnable() {
                            public void run() {
                                boolean ok = VirtualEngine.clearData(p.packageName, p.virtualUser);
                                logs.add(p.id, ok ? "Clone data cleared" : "Clearing clone data failed");
                                Toast.makeText(CloneDetailsActivity.this, ok ? "Data cleared." : "Could not clear data.", Toast.LENGTH_LONG).show();
                                rebuild();
                            }
                        });
                    }
                }));
                Ui.add(body, Ui.button(this, "Remove virtual clone", false, new View.OnClickListener() {
                    public void onClick(View v) {
                        confirm("Remove clone?", "The virtual clone and all its data are deleted. The original app is not touched.", "Remove", new Runnable() {
                            public void run() {
                                VirtualEngine.remove(p.packageName, p.virtualUser);
                                repo.setState(p.id, CloneRepository.STATUS_REMOVED, null, null);
                                logs.add(p.id, "Virtual clone removed");
                                rebuild();
                            }
                        });
                    }
                }));
            } else Ui.add(body, Ui.button(this, "Uninstall clone app", false, new View.OnClickListener() {
                public void onClick(View v) {
                    confirm("Uninstall clone?", "Android will ask you to confirm. The clone's data is deleted with it. The original app is not touched.", "Continue", new Runnable() {
                        public void run() { startActivity(CloneInstaller.uninstallIntent(p.clonePackage)); }
                    });
                }
            }));
        } else {
            Ui.add(body, Ui.button(this, "Install clone", true, new View.OnClickListener() {
                public void onClick(View v) { reinstall(p); }
            }));
        }

        String netName = "Default connection";
        if (p.networkProfileId > 0) {
            NetworkProfile np = nets.get(p.networkProfileId);
            if (np != null) netName = np.name;
        }
        LinearLayout info = Ui.card(this);
        info.addView(Ui.row(this, "Clone ID", String.valueOf(p.id)));
        info.addView(Ui.row(this, "Package", p.packageName));
        info.addView(Ui.row(this, "Method", virt ? "Virtual (own data area)" : CloneRepository.METHOD_REAL.equals(p.method) ? "APK repackaging" : "Not assigned"));
        if (p.clonePackage != null && p.clonePackage.length() > 0) info.addView(Ui.row(this, "Clone package", p.clonePackage));
        info.addView(Ui.row(this, "Network", netName));
        info.addView(Ui.row(this, "Created", new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(p.createdAt))));
        info.addView(Ui.row(this, "Metadata size", "about " + approxBytes(p) + " bytes (this app's database)"));
        info.addView(Ui.row(this, "App's own data", "Not accessible (Android sandbox)"));
        Ui.add(body, info);

        Ui.add(body, Ui.section(this, "Profile note"));
        final EditText note = Ui.input(this, "Your own note", p.note, false);
        Ui.add(body, note);
        Ui.add(body, Ui.button(this, "Save note", false, new View.OnClickListener() {
            public void onClick(View v) {
                String n = note.getText().toString();
                if (n.length() > 200) { Toast.makeText(CloneDetailsActivity.this, "Note is limited to 200 characters.", Toast.LENGTH_SHORT).show(); return; }
                if (repo.setNote(p.id, n)) {
                    logs.add(p.id, "Note updated");
                    Toast.makeText(CloneDetailsActivity.this, "Note saved.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CloneDetailsActivity.this, "Could not save the note.", Toast.LENGTH_LONG).show();
                }
            }
        }));

        Ui.add(body, Ui.button(this, "Rename", false, new View.OnClickListener() {
            public void onClick(View v) { showRename(p); }
        }));
        Ui.add(body, Ui.button(this, "Assign network profile", false, new View.OnClickListener() {
            public void onClick(View v) { showAssign(p); }
        }));

        Ui.add(body, Ui.section(this, "Activity log"));
        LinearLayout lc = Ui.card(this);
        List<String> l = logs.list(p.id, 20);
        if (l.isEmpty()) lc.addView(Ui.text(this, "No log entries.", 12, Ui.TEXT2, false));
        for (String s : l) lc.addView(Ui.text(this, s, 11, Ui.TEXT2, false));
        Ui.add(body, lc);

        Ui.add(body, Ui.button(this, active ? "Delete profile (keeps the installed clone)" : "Delete profile", false, new View.OnClickListener() {
            public void onClick(View v) {
                confirm("Delete profile?", "\"" + p.cloneName + "\" and its log will be removed from this app. The original app and other clones are not touched.",
                    "Delete", new Runnable() {
                        public void run() {
                            CloneState.cleanup(p);
                            if (repo.delete(p.id)) finish();
                            else Toast.makeText(CloneDetailsActivity.this, "Delete failed. Nothing was changed.", Toast.LENGTH_LONG).show();
                        }
                    });
            }
        }));
    }

    private void reinstall(final CloneProfile p) {
        if (CloneRepository.METHOD_VIRTUAL.equals(p.method)) {
            final android.content.Context app = getApplicationContext();
            Toast.makeText(this, "Creating the virtual clone...", Toast.LENGTH_SHORT).show();
            exec.execute(new Runnable() {
                public void run() {
                    final ApkCloningEngine.Result res = VirtualEngine.install(app, p.packageName, p.virtualUser);
                    repo.setState(p.id, res.success ? CloneRepository.STATUS_ACTIVE : CloneRepository.STATUS_FAILED, CloneRepository.METHOD_VIRTUAL, p.packageName);
                    logs.add(p.id, res.success ? "Virtual clone created (virtual user " + p.virtualUser + ")" : res.message);
                    ui(new Runnable() {
                        public void run() {
                            if (!res.success) Toast.makeText(CloneDetailsActivity.this, res.message, Toast.LENGTH_LONG).show();
                            rebuild();
                        }
                    });
                }
            });
            return;
        }
        if (!CloneInstaller.canInstall(this)) {
            confirm("Allow installs", "Android needs your permission before this app can install the clone. Switch on \"Allow from this source\", then come back and try again.",
                "Open settings", new Runnable() {
                    public void run() { startActivity(CloneInstaller.permissionIntent(CloneDetailsActivity.this)); }
                });
            return;
        }
        final android.content.Context app = getApplicationContext();
        final String clonePkg = ApkCloningEngine.clonePackageFor(p.packageName, p.id);
        repo.setState(p.id, CloneRepository.STATUS_INSTALLING, CloneRepository.METHOD_REAL, clonePkg);
        Toast.makeText(this, "Building the clone...", Toast.LENGTH_SHORT).show();
        exec.execute(new Runnable() {
            public void run() {
                final ApkCloningEngine.Result res = ApkCloningEngine.cloneReal(app, p.id, p.packageName, p.cloneName);
                if (!res.success) {
                    repo.setState(p.id, CloneRepository.STATUS_FAILED, CloneRepository.METHOD_REAL, clonePkg);
                    logs.add(p.id, res.message);
                } else {
                    logs.add(p.id, "Clone APK built and signed, sent to the system installer as " + clonePkg);
                }
                ui(new Runnable() {
                    public void run() {
                        if (!res.success) Toast.makeText(CloneDetailsActivity.this, res.message, Toast.LENGTH_LONG).show();
                        rebuild();
                    }
                });
            }
        });
    }

    private int approxBytes(CloneProfile p) {
        int n = 0;
        try {
            n += p.appName.getBytes("UTF-8").length + p.packageName.getBytes("UTF-8").length + p.cloneName.getBytes("UTF-8").length;
            n += (p.note == null ? 0 : p.note.getBytes("UTF-8").length) + 40;
            for (String s : logs.list(p.id, 1000)) n += s.getBytes("UTF-8").length;
        } catch (Exception ignored) { }
        return n;
    }

    private void showRename(final CloneProfile p) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(p.cloneName);
        input.setSelection(p.cloneName.length());
        new AlertDialog.Builder(this).setTitle("Rename clone").setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String n = input.getText().toString();
                    String err = CloneRepository.validateName(n);
                    if (err != null) { Toast.makeText(CloneDetailsActivity.this, err, Toast.LENGTH_LONG).show(); return; }
                    long r = repo.rename(p, n);
                    if (r == CloneRepository.DUPLICATE) {
                        Toast.makeText(CloneDetailsActivity.this, "A clone with this name already exists for " + p.appName + ".", Toast.LENGTH_LONG).show();
                    } else if (r < 0) {
                        Toast.makeText(CloneDetailsActivity.this, "Rename failed. Nothing was changed.", Toast.LENGTH_LONG).show();
                    } else {
                        logs.add(p.id, "Renamed to " + n.trim());
                        rebuild();
                    }
                }
            }).show();
    }

    private void showAssign(final CloneProfile p) {
        final List<NetworkProfile> all = nets.getAll();
        List<String> names = new ArrayList<String>();
        names.add("Default connection");
        for (NetworkProfile n : all) names.add((n.isVpn() ? "VPN: " : "Proxy: ") + n.name);
        new AlertDialog.Builder(this).setTitle("Assign network profile")
            .setItems(names.toArray(new String[0]), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    long nid = which == 0 ? -1 : all.get(which - 1).id;
                    if (repo.setNetwork(p.id, nid)) {
                        logs.add(p.id, which == 0 ? "Network reset to default" : "Network assigned: " + all.get(which - 1).name);
                        rebuild();
                        info("Saved as metadata", "The assignment is stored with this profile. Per-clone traffic routing is not implemented, "
                            + "so it does not change the traffic of any app. Android normally allows only one active VPN at a time.");
                    } else {
                        Toast.makeText(CloneDetailsActivity.this, "Could not save the assignment.", Toast.LENGTH_LONG).show();
                    }
                }
            }).show();
    }
}
