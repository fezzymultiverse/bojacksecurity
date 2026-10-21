package com.fezzy.vault;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.annotation.ActivityCallback;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** JS bridge: start/stop the foreground service and toggle FLAG_KEEP_SCREEN_ON (a real wake lock, unlike the WebView API). */
@CapacitorPlugin(name = "BojackKeepAlive")
public class BojackKeepAlive extends Plugin {

    @PluginMethod
    public void start(PluginCall call) {
        try {
            String text = call.getString("text", "Securing your files");
            Intent i = new Intent(getContext(), KeepAliveService.class);
            i.putExtra(KeepAliveService.EXTRA_TEXT, text);
            ContextCompat.startForegroundService(getContext(), i);
            call.resolve();
        } catch (Exception e) {
            call.reject("start failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        try {
            getContext().stopService(new Intent(getContext(), KeepAliveService.class));
        } catch (Exception ignored) { }
        call.resolve();
    }

    @PluginMethod
    public void keepScreenOn(final PluginCall call) {
        final boolean on = Boolean.TRUE.equals(call.getBoolean("on", false));
        try {
            getActivity().runOnUiThread(new Runnable() {
                @Override public void run() {
                    try {
                        if (on) getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        else getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    } catch (Exception ignored) { }
                    call.resolve();
                }
            });
        } catch (Exception e) {
            call.resolve();
        }
    }

    /** Opens a system Settings screen. Only android.settings.* actions are allowed; falls back to the main Settings screen. */
    @PluginMethod
    public void openSettings(PluginCall call) {
        String action = call.getString("action", "android.settings.SETTINGS");
        if (action == null || !action.startsWith("android.settings.")) {
            call.reject("action not allowed");
            return;
        }
        boolean pkg = Boolean.TRUE.equals(call.getBoolean("pkg", false));
        try {
            Intent i = new Intent(action);
            if (pkg) i.setData(Uri.parse("package:" + getContext().getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            try {
                if (pkg) {
                    Intent g = new Intent("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION");
                    g.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(g);
                    call.resolve();
                    return;
                }
            } catch (Exception ignored) { }
            try {
                Intent f = new Intent(Settings.ACTION_SETTINGS);
                f.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(f);
                call.resolve();
            } catch (Exception e2) {
                call.reject("openSettings failed: " + e2.getMessage());
            }
        }
    }

    /** True when the app may write to shared Documents (Android 11+ needs "All files access"). */
    @PluginMethod
    public void hasAllFiles(PluginCall call) {
        boolean ok = true;
        try { if (Build.VERSION.SDK_INT >= 30) ok = Environment.isExternalStorageManager(); } catch (Exception e) { ok = true; }
        JSObject r = new JSObject();
        r.put("granted", ok);
        call.resolve(r);
    }

    /** Opens mailto:, https: or http: links in the matching Android app (mail, WhatsApp, browser). */
    @PluginMethod
    public void openUrl(PluginCall call) {
        String url = call.getString("url", "");
        if (url == null || !(url.startsWith("mailto:") || url.startsWith("https://") || url.startsWith("http://"))) {
            call.reject("url not allowed");
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("openUrl failed: " + e.getMessage());
        }
    }

    // ---- V0.6.20: native file picker + native chunk reads (the WebView <input type=file> path was slow and got the app killed) ----
    private final ConcurrentHashMap<String, ParcelFileDescriptor> openFds = new ConcurrentHashMap<String, ParcelFileDescriptor>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    /** System document picker, multi-select. Returns {files:[{uri,name,size,type,lastModified}]}. Write grant is requested so originals can be deleted later. */
    @PluginMethod
    public void pickFiles(PluginCall call) {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(call, i, "pickResult");
        } catch (Exception e) {
            call.reject("pickFiles failed: " + e.getMessage());
        }
    }

    @ActivityCallback
    private void pickResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSArray arr = new JSArray();
        try {
            Intent d = result.getData();
            if (result.getResultCode() == Activity.RESULT_OK && d != null) {
                ArrayList<Uri> uris = new ArrayList<Uri>();
                ClipData cd = d.getClipData();
                if (cd != null) {
                    for (int k = 0; k < cd.getItemCount(); k++) uris.add(cd.getItemAt(k).getUri());
                } else if (d.getData() != null) {
                    uris.add(d.getData());
                }
                ContentResolver cr = getContext().getContentResolver();
                for (Uri u : uris) {
                    String name = "file";
                    long size = 0, mod = 0;
                    Cursor c = null;
                    try {
                        c = cr.query(u, null, null, null, null);
                        if (c != null && c.moveToFirst()) {
                            int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                            int si = c.getColumnIndex(OpenableColumns.SIZE);
                            int mi = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED);
                            if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni);
                            if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
                            if (mi >= 0 && !c.isNull(mi)) mod = c.getLong(mi);
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (c != null) try { c.close(); } catch (Exception ignored) { }
                    }
                    if (size <= 0) {
                        try { size = pfdOf(u.toString()).getStatSize(); } catch (Exception ignored) { }
                    }
                    String type = cr.getType(u);
                    JSObject o = new JSObject();
                    o.put("uri", u.toString());
                    o.put("name", name);
                    o.put("size", size);
                    o.put("type", type == null ? "" : type);
                    o.put("lastModified", mod);
                    arr.put(o);
                }
            }
        } catch (Exception e) {
            call.reject("pick failed: " + e.getMessage());
            return;
        }
        JSObject r = new JSObject();
        r.put("files", arr);
        call.resolve(r);
    }

    private ParcelFileDescriptor pfdOf(String uri) throws Exception {
        ParcelFileDescriptor p = openFds.get(uri);
        if (p == null) {
            p = getContext().getContentResolver().openFileDescriptor(Uri.parse(uri), "r");
            if (p == null) throw new Exception("cannot open file");
            ParcelFileDescriptor prev = openFds.putIfAbsent(uri, p);
            if (prev != null) { try { p.close(); } catch (Exception ignored) { } p = prev; }
        }
        return p;
    }

    /** Reads {uri, offset, length} and returns {data: base64, n: bytesRead}. Positional reads, so four can run at once. */
    @PluginMethod
    public void readChunk(final PluginCall call) {
        final String uri = call.getString("uri");
        final Double offD = call.getDouble("offset", 0.0);
        final Integer lenI = call.getInt("length", 0);
        if (uri == null || lenI == null || lenI <= 0 || lenI > 8 * 1024 * 1024) { call.reject("bad read request"); return; }
        final long off = offD == null ? 0L : offD.longValue();
        final int len = lenI;
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    byte[] buf = new byte[len];
                    int got = 0;
                    try {
                        ParcelFileDescriptor p = pfdOf(uri);
                        FileInputStream fis = new FileInputStream(p.getFileDescriptor()); // never closed here: closing would close the shared fd
                        ByteBuffer bb = ByteBuffer.wrap(buf);
                        while (got < len) {
                            int n = fis.getChannel().read(bb, off + got);
                            if (n <= 0) break;
                            got += n;
                        }
                    } catch (Exception seekFail) {
                        // provider without random access: reopen as a stream and skip
                        got = 0;
                        InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(uri));
                        try {
                            long skipped = 0;
                            while (skipped < off) { long s = in.skip(off - skipped); if (s <= 0) break; skipped += s; }
                            while (got < len) { int n = in.read(buf, got, len - got); if (n <= 0) break; got += n; }
                        } finally { try { in.close(); } catch (Exception ignored) { } }
                    }
                    JSObject r = new JSObject();
                    r.put("data", Base64.encodeToString(buf, 0, got, Base64.NO_WRAP));
                    r.put("n", got);
                    call.resolve(r);
                } catch (Exception e) {
                    call.reject("read failed: " + e.getMessage());
                }
            }
        });
    }

    /** Releases the open file handles for these uris. */
    @PluginMethod
    public void closeFiles(PluginCall call) {
        try {
            JSArray a = call.getArray("uris");
            if (a != null) for (int k = 0; k < a.length(); k++) {
                ParcelFileDescriptor p = openFds.remove(a.getString(k));
                if (p != null) try { p.close(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        call.resolve();
    }

    /** Deletes the picked originals (the Move option). Returns {deleted, failed}. */
    @PluginMethod
    public void deleteUris(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                int ok = 0, bad = 0;
                try {
                    JSArray a = call.getArray("uris");
                    ContentResolver cr = getContext().getContentResolver();
                    if (a != null) for (int k = 0; k < a.length(); k++) {
                        String s = a.getString(k);
                        ParcelFileDescriptor p = openFds.remove(s);
                        if (p != null) try { p.close(); } catch (Exception ignored) { }
                        boolean done = false;
                        try { done = DocumentsContract.deleteDocument(cr, Uri.parse(s)); } catch (Exception ignored) { }
                        if (!done) { try { done = cr.delete(Uri.parse(s), null, null) > 0; } catch (Exception ignored) { } }
                        if (done) ok++; else bad++;
                    }
                } catch (Exception ignored) { }
                JSObject r = new JSObject();
                r.put("deleted", ok);
                r.put("failed", bad);
                call.resolve(r);
            }
        });
    }
}
