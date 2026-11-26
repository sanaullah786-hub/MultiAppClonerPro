package com.multiappcloner.pro;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Plain HTTPS file download into the app's own cache. */
public final class Downloader {
    public interface Progress { void onProgress(long done, long total); }

    public static final long MAX_BYTES = 3L * 1024 * 1024 * 1024;
    private volatile boolean cancelled;

    public void cancel() { cancelled = true; }

    public void download(String link, File dest, Progress cb) throws IOException {
        URL url = new URL(link.trim());
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Only https:// links are allowed.");
        HttpURLConnection c = null;
        for (int hop = 0; hop < 6; hop++) {
            c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MultiAppClonerPro");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("Bad redirect.");
                url = new URL(url, loc);
                if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("The link redirects to a non-https address.");
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("Server answered HTTP " + code + ".");
            }
            break;
        }
        long total = c.getContentLengthLong();
        if (total > MAX_BYTES) throw new IOException("The file is larger than 3 GB.");
        File parent = dest.getParentFile();
        if (parent != null) parent.mkdirs();
        InputStream in = c.getInputStream();
        OutputStream os = new FileOutputStream(dest);
        boolean ok = false;
        try {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int r;
            long lastCb = 0;
            while ((r = in.read(buf)) > 0) {
                if (cancelled) throw new IOException("Download cancelled.");
                done += r;
                if (done > MAX_BYTES) throw new IOException("The file is larger than 3 GB.");
                os.write(buf, 0, r);
                long now = System.currentTimeMillis();
                if (cb != null && now - lastCb > 400) { cb.onProgress(done, total); lastCb = now; }
            }
            if (cb != null) cb.onProgress(done, total);
            ok = true;
        } finally {
            in.close();
            os.close();
            c.disconnect();
            if (!ok) dest.delete();
        }
    }
}
