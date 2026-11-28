package com.multiappcloner.pro.diag;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Small persistent event log kept in the app's own storage. Several processes (the app, the engine server
 * and every clone process) write to it, so each write appends one whole line and the file is rotated by size.
 * Everything is redacted before it is written.
 */
public final class DiagStore {
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_LINE = 4000;
    private final File file;
    private final File old;

    public DiagStore(File dir) {
        dir.mkdirs();
        this.file = new File(dir, "events.log");
        this.old = new File(dir, "events.old.log");
    }

    public void record(String category, String message) {
        try {
            String text = Redactor.redact(message == null ? "" : message).replace('\r', ' ').replace('\n', '⏎');
            if (text.length() > MAX_LINE) text = text.substring(0, MAX_LINE) + "...";
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + " " + category + " " + text + "\n";
            synchronized (DiagStore.class) {
                if (file.length() > MAX_BYTES) {
                    old.delete();
                    file.renameTo(old);
                }
                FileOutputStream fos = new FileOutputStream(file, true);
                try {
                    Writer w = new OutputStreamWriter(fos, "UTF-8");
                    w.write(line);
                    w.flush();
                } finally {
                    fos.close();
                }
            }
        } catch (Throwable ignored) {
            // diagnostics must never break the app
        }
    }

    /** Newest-last list of the stored lines, optionally filtered by category. */
    public List<String> read(String categoryOrNull, int maxLines) {
        List<String> all = new ArrayList<String>();
        readInto(old, all);
        readInto(file, all);
        List<String> out = new ArrayList<String>();
        for (String l : all) {
            if (categoryOrNull == null || l.length() > 15 && l.substring(15).startsWith(categoryOrNull + " ")) out.add(l.replace('⏎', '\n'));
        }
        int from = Math.max(0, out.size() - maxLines);
        return new ArrayList<String>(out.subList(from, out.size()));
    }

    public void clear() {
        synchronized (DiagStore.class) {
            file.delete();
            old.delete();
        }
    }

    private static void readInto(File f, List<String> into) {
        if (!f.exists()) return;
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String l;
                while ((l = r.readLine()) != null) into.add(l);
            } finally {
                r.close();
            }
        } catch (IOException ignored) { }
    }
}
