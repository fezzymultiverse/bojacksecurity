package com.fezzy.vault;

import android.app.Activity;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * V0.6.34: the vault's native storage WITHOUT Capacitor. Exposed to the page as window.BojackST (plain @JavascriptInterface, like
 * BojackNative and BojackPB), so the vault no longer depends on Capacitor registering BojackStore. Same folder layout and file format as
 * BojackStore (filesDir/vault/<store>/<hex of key>, written to .tmp, fsync'd, renamed), so either path can read what the other wrote.
 * The page calls call(id, method, jsonArgs); the answer comes back through window.__bstCb(id, ok, jsonText).
 */
public class BojackStoreJs {
    private final Activity act;
    private final WebView web;
    private final ConcurrentHashMap<String, FileOutputStream> outs = new ConcurrentHashMap<String, FileOutputStream>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    BojackStoreJs(Activity a, WebView w) { act = a; web = w; }

    @JavascriptInterface
    public String ping() { return "ok"; }

    @JavascriptInterface
    public void call(final String id, final String method, final String args) {
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject a = (args == null || args.length() == 0) ? new JSONObject() : new JSONObject(args);
                    JSONObject r = dispatch(method, a);
                    reply(id, true, r == null ? "" : r.toString());
                } catch (Throwable e) {
                    reply(id, false, method + " failed: " + e.getMessage());
                }
            }
        });
    }

    private void reply(String id, boolean ok, String text) {
        final String js = "window.__bstCb(" + JSONObject.quote(id) + "," + (ok ? "true" : "false") + "," + JSONObject.quote(text == null ? "" : text) + ")";
        try {
            act.runOnUiThread(new Runnable() {
                @Override public void run() {
                    try { web.evaluateJavascript(js, null); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private JSONObject dispatch(String m, JSONObject a) throws Exception {
        if ("sPut".equals(m)) return put(a);
        if ("sGet".equals(m)) return get(a);
        if ("sDel".equals(m)) return del(a);
        if ("sDelPrefix".equals(m)) return delPrefix(a);
        if ("sClear".equals(m)) return clear(a);
        if ("sMetaAll".equals(m)) return metaAll();
        if ("sCount".equals(m)) return count(a);
        if ("sUsage".equals(m)) return usage();
        if ("sCleanup".equals(m)) return cleanup();
        throw new Exception("unknown method");
    }

    private static String hex(String s) {
        byte[] b;
        try { b = s.getBytes("UTF-8"); } catch (Exception e) { b = s.getBytes(); }
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xff;
            sb.append("0123456789abcdef".charAt(v >> 4)).append("0123456789abcdef".charAt(v & 15));
        }
        return sb.toString();
    }

    private static String unhex(String h) {
        try {
            byte[] b = new byte[h.length() / 2];
            for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
            return new String(b, "UTF-8");
        } catch (Exception e) { return ""; }
    }

    private File dir(String store) throws Exception {
        if (!("meta".equals(store) || "files".equals(store) || "chunks".equals(store))) throw new Exception("bad store");
        File d = new File(new File(act.getFilesDir(), "vault"), store);
        if (!d.exists() && !d.mkdirs() && !d.exists()) throw new Exception("cannot create storage folder");
        return d;
    }

    private JSONObject put(JSONObject a) throws Exception {
        String store = a.optString("store", "");
        String key = a.optString("key", "");
        if (key.length() == 0) throw new Exception("no key");
        int part = a.optInt("part", 0);
        boolean last = a.optBoolean("last", false);
        String data = a.optString("data", "");
        File d = dir(store);
        String h = hex(key);
        String id = store + "/" + h;
        File tmp = new File(d, h + ".tmp");
        File fin = new File(d, h);
        try {
            byte[] b = Base64.decode(data, Base64.NO_WRAP);
            FileOutputStream o;
            if (part == 0) {
                FileOutputStream old = outs.remove(id);
                if (old != null) try { old.close(); } catch (Exception ignored) { }
                o = new FileOutputStream(tmp, false);
                outs.put(id, o);
            } else {
                o = outs.get(id);
                if (o == null) throw new Exception("write session lost");
            }
            o.write(b);
            if (last) {
                o.flush();
                o.getFD().sync();
                o.close();
                outs.remove(id);
                if (!tmp.renameTo(fin)) {
                    fin.delete();
                    if (!tmp.renameTo(fin)) throw new Exception("rename failed");
                }
            }
        } catch (Exception e) {
            FileOutputStream o2 = outs.remove(id);
            if (o2 != null) try { o2.close(); } catch (Exception ignored) { }
            throw e;
        }
        return null;
    }

    private JSONObject get(JSONObject a) throws Exception {
        String store = a.optString("store", "");
        String key = a.optString("key", "");
        long off = (long) a.optDouble("offset", 0.0);
        int len = a.optInt("length", 1048576);
        File f = new File(dir(store), hex(key));
        JSONObject r = new JSONObject();
        if (!f.exists()) { r.put("size", -1); r.put("n", 0); r.put("data", ""); return r; }
        long size = f.length();
        int want = (int) Math.max(0, Math.min((long) len, size - off));
        byte[] buf = new byte[want];
        int got = 0;
        if (want > 0) {
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            try {
                raf.seek(off);
                while (got < want) { int n = raf.read(buf, got, want - got); if (n <= 0) break; got += n; }
            } finally { raf.close(); }
        }
        r.put("size", size);
        r.put("n", got);
        r.put("data", Base64.encodeToString(buf, 0, got, Base64.NO_WRAP));
        return r;
    }

    private JSONObject del(JSONObject a) throws Exception {
        File d = dir(a.optString("store", ""));
        String h = hex(a.optString("key", ""));
        new File(d, h).delete();
        new File(d, h + ".tmp").delete();
        return null;
    }

    private JSONObject delPrefix(JSONObject a) throws Exception {
        File d = dir(a.optString("store", ""));
        String hp = hex(a.optString("prefix", ""));
        File[] fs = d.listFiles();
        int n = 0;
        if (fs != null) for (File f : fs) if (f.getName().startsWith(hp)) { if (f.delete()) n++; }
        JSONObject r = new JSONObject();
        r.put("removed", n);
        return r;
    }

    private JSONObject clear(JSONObject a) throws Exception {
        File d = dir(a.optString("store", ""));
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        return null;
    }

    private static byte[] readSmall(File f) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            int n = (int) Math.min(raf.length(), 262144);
            byte[] b = new byte[n];
            raf.readFully(b);
            return b;
        } finally { raf.close(); }
    }

    private JSONObject metaAll() throws Exception {
        File d = dir("files");
        String suf = hex(".m");
        JSONArray rows = new JSONArray();
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) {
            String nm = f.getName();
            if (!nm.endsWith(suf)) continue;
            try {
                JSONObject o = new JSONObject();
                byte[] b = readSmall(f);
                o.put("data", Base64.encodeToString(b, 0, b.length, Base64.NO_WRAP));
                rows.put(o);
            } catch (Exception ignored) { }
        }
        JSONObject r = new JSONObject();
        r.put("rows", rows);
        return r;
    }

    private JSONObject count(JSONObject a) throws Exception {
        File d = dir(a.optString("store", ""));
        String suffix = a.optString("suffix", "");
        String hs = suffix.length() == 0 ? "" : hex(suffix);
        int n = 0;
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) { String nm = f.getName(); if (!nm.endsWith(".tmp") && nm.endsWith(hs)) n++; }
        JSONObject r = new JSONObject();
        r.put("count", n);
        return r;
    }

    private static long sizeOf(File f) {
        if (f.isFile()) return f.length();
        long t = 0;
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) t += sizeOf(c);
        return t;
    }

    private JSONObject usage() throws Exception {
        JSONObject r = new JSONObject();
        r.put("bytes", sizeOf(new File(act.getFilesDir(), "vault")));
        return r;
    }

    private JSONObject cleanup() throws Exception {
        int n = 0;
        final long old = System.currentTimeMillis() - 600000L; /* never touch anything written in the last 10 minutes */
        try {
            File files = dir("files"), chunks = dir("chunks"), meta = dir("meta");
            File[] all = files.listFiles();
            if (all != null) for (File f : all) {
                String nm = f.getName();
                if (nm.endsWith(".tmp")) { if (f.lastModified() < old && f.delete()) n++; continue; }
                if (nm.endsWith(hex(".d"))) {
                    String id = unhex(nm).replaceAll("\\.d$", "");
                    if (f.lastModified() < old && !new File(files, hex(id + ".m")).exists() && f.delete()) n++;
                }
            }
            File[] cs = chunks.listFiles();
            if (cs != null) for (File f : cs) {
                String nm = f.getName();
                if (nm.endsWith(".tmp")) { if (f.lastModified() < old && f.delete()) n++; continue; }
                String key = unhex(nm);
                int i = key.indexOf(':');
                if (i > 0 && f.lastModified() < old && !new File(files, hex(key.substring(0, i) + ".m")).exists() && f.delete()) n++;
            }
            File[] ms = meta.listFiles();
            if (ms != null) for (File f : ms) if (f.getName().endsWith(".tmp") && f.lastModified() < old && f.delete()) n++;
        } catch (Exception ignored) { }
        JSONObject r = new JSONObject();
        r.put("removed", n);
        return r;
    }
}
