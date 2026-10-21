package com.fezzy.vault;

import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * V0.6.21: the vault's own storage, inside the app's private internal storage (filesDir/vault/<store>/).
 * Only ciphertext ever lands here. Every write goes to a .tmp file, is fsync'd, then renamed over the real file,
 * so a crash or a forced restart can never leave a half-written file behind. The app's "Data" size in Android
 * settings grows with the vault. JS packs each record into bytes and moves them in 1 MB slices.
 */
@CapacitorPlugin(name = "BojackStore")
public class BojackStore extends Plugin {
    private final ConcurrentHashMap<String, FileOutputStream> outs = new ConcurrentHashMap<String, FileOutputStream>();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

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
        File d = new File(new File(getContext().getFilesDir(), "vault"), store);
        if (!d.exists() && !d.mkdirs() && !d.exists()) throw new Exception("cannot create storage folder");
        return d;
    }

    /** One slice of a record: part 0 starts a fresh .tmp, the last part fsyncs and renames it into place. */
    @PluginMethod
    public void sPut(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                String id = null;
                try {
                    String store = call.getString("store");
                    String key = call.getString("key");
                    Integer part = call.getInt("part", 0);
                    Boolean last = call.getBoolean("last", false);
                    String data = call.getString("data", "");
                    if (key == null) { call.reject("no key"); return; }
                    File d = dir(store);
                    String h = hex(key);
                    id = store + "/" + h;
                    File tmp = new File(d, h + ".tmp");
                    File fin = new File(d, h);
                    byte[] b = Base64.decode(data, Base64.NO_WRAP);
                    FileOutputStream o;
                    if (part == null || part == 0) {
                        FileOutputStream old = outs.remove(id);
                        if (old != null) try { old.close(); } catch (Exception ignored) { }
                        o = new FileOutputStream(tmp, false);
                        outs.put(id, o);
                    } else {
                        o = outs.get(id);
                        if (o == null) { call.reject("write session lost"); return; }
                    }
                    o.write(b);
                    if (Boolean.TRUE.equals(last)) {
                        o.flush();
                        o.getFD().sync();
                        o.close();
                        outs.remove(id);
                        if (!tmp.renameTo(fin)) {
                            fin.delete();
                            if (!tmp.renameTo(fin)) throw new Exception("rename failed");
                        }
                    }
                    call.resolve();
                } catch (Exception e) {
                    if (id != null) {
                        FileOutputStream o2 = outs.remove(id);
                        if (o2 != null) try { o2.close(); } catch (Exception ignored) { }
                    }
                    call.reject("write failed: " + e.getMessage());
                }
            }
        });
    }

    /** Reads {store,key,offset,length} -> {data, n, size}. size is -1 when the record does not exist. */
    @PluginMethod
    public void sGet(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    String store = call.getString("store");
                    String key = call.getString("key");
                    Double offD = call.getDouble("offset", 0.0);
                    Integer len = call.getInt("length", 1048576);
                    File f = new File(dir(store), hex(key));
                    JSObject r = new JSObject();
                    if (!f.exists()) { r.put("size", -1); r.put("n", 0); r.put("data", ""); call.resolve(r); return; }
                    long size = f.length();
                    long off = offD == null ? 0L : offD.longValue();
                    int want = (int) Math.max(0, Math.min(len == null ? 1048576 : len, size - off));
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
                    call.resolve(r);
                } catch (Exception e) {
                    call.reject("read failed: " + e.getMessage());
                }
            }
        });
    }

    @PluginMethod
    public void sDel(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    File d = dir(call.getString("store"));
                    String h = hex(call.getString("key"));
                    new File(d, h).delete();
                    new File(d, h + ".tmp").delete();
                    call.resolve();
                } catch (Exception e) { call.reject("delete failed: " + e.getMessage()); }
            }
        });
    }

    /** Deletes every record whose key starts with prefix (used to drop all chunks of one file). */
    @PluginMethod
    public void sDelPrefix(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    File d = dir(call.getString("store"));
                    String hp = hex(call.getString("prefix", ""));
                    File[] fs = d.listFiles();
                    int n = 0;
                    if (fs != null) for (File f : fs) if (f.getName().startsWith(hp)) { if (f.delete()) n++; }
                    JSObject r = new JSObject();
                    r.put("removed", n);
                    call.resolve(r);
                } catch (Exception e) { call.reject("delete failed: " + e.getMessage()); }
            }
        });
    }

    @PluginMethod
    public void sClear(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    File d = dir(call.getString("store"));
                    File[] fs = d.listFiles();
                    if (fs != null) for (File f : fs) f.delete();
                    call.resolve();
                } catch (Exception e) { call.reject("clear failed: " + e.getMessage()); }
            }
        });
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

    /** All small "head" records (key ends with .m) of the files store in one call, so listing the vault is one bridge round trip. */
    @PluginMethod
    public void sMetaAll(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    File d = dir("files");
                    String suf = hex(".m");
                    JSArray rows = new JSArray();
                    File[] fs = d.listFiles();
                    if (fs != null) for (File f : fs) {
                        String nm = f.getName();
                        if (!nm.endsWith(suf)) continue;
                        try {
                            JSObject o = new JSObject();
                            byte[] b = readSmall(f);
                            o.put("data", Base64.encodeToString(b, 0, b.length, Base64.NO_WRAP));
                            rows.put(o);
                        } catch (Exception ignored) { }
                    }
                    JSObject r = new JSObject();
                    r.put("rows", rows);
                    call.resolve(r);
                } catch (Exception e) { call.reject("list failed: " + e.getMessage()); }
            }
        });
    }

    /** Number of records in a store; with suffix (e.g. ".m") only keys ending with it. */
    @PluginMethod
    public void sCount(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    File d = dir(call.getString("store"));
                    String suffix = call.getString("suffix", "");
                    String hs = suffix == null || suffix.length() == 0 ? "" : hex(suffix);
                    int n = 0;
                    File[] fs = d.listFiles();
                    if (fs != null) for (File f : fs) { String nm = f.getName(); if (!nm.endsWith(".tmp") && nm.endsWith(hs)) n++; }
                    JSObject r = new JSObject();
                    r.put("count", n);
                    call.resolve(r);
                } catch (Exception e) { call.reject("count failed: " + e.getMessage()); }
            }
        });
    }

    private static long sizeOf(File f) {
        if (f.isFile()) return f.length();
        long t = 0;
        File[] fs = f.listFiles();
        if (fs != null) for (File c : fs) t += sizeOf(c);
        return t;
    }

    /** Bytes the vault occupies inside the app's private storage. */
    @PluginMethod
    public void sUsage(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                try {
                    JSObject r = new JSObject();
                    r.put("bytes", sizeOf(new File(getContext().getFilesDir(), "vault")));
                    call.resolve(r);
                } catch (Exception e) { call.reject("usage failed: " + e.getMessage()); }
            }
        });
    }

    /** Boot-time tidy-up: removes .tmp leftovers and pieces of an upload that never finished (no head record). */
    @PluginMethod
    public void sCleanup(final PluginCall call) {
        pool.execute(new Runnable() {
            @Override public void run() {
                int n = 0;
                final long old = System.currentTimeMillis() - 600000L; /* V0.6.22: never touch anything written in the last 10 minutes (an upload or migration may still be writing it) */
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
                JSObject r = new JSObject();
                r.put("removed", n);
                call.resolve(r);
            }
        });
    }
}
