package com.fezzy.vault;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.webkit.MimeTypeMap;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.Message;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * V0.6.33: Bojack privacy browser, written fresh (nothing from the removed Prodigy-based browser).
 * The page (www/index.html) draws every bar and panel; this class only owns the web pages.
 * Exposed to the vault page as window.BojackPB (plain @JavascriptInterface, no Capacitor, no androidx.webkit).
 * Web pages never get any Java object. Events go back through window.PB.ev(json).
 * Downloads go to filesDir/browser-inbox (private); the page seals them into the vault, then deletes them.
 */
public class PrivBrowser {
    static PrivBrowser inst;

    private final Activity act;
    private final WebView main;
    private final Map<String, WebView> tabs = new LinkedHashMap<>();
    private final ExecutorService ex = Executors.newFixedThreadPool(3);
    private final Map<String, AtomicBoolean> cancels = new java.util.concurrent.ConcurrentHashMap<>();
    private final File inbox;
    private FrameLayout pane;
    private String cur = "";
    private int hx, hy, hw, hh;
    private boolean visible = true;
    private long dlSeq = 0;
    private View fsView;
    private WebChromeClient.CustomViewCallback fsCb;
    private volatile boolean fs = false;

    // settings (JS pushes them with cfg())
    private boolean js = true, desk = false, ad = true, https = true, tpc = false, ck = true, img = true,
            dark = false, newtab = true, ban = true, webrtc = true, spoof = true;
    private int zoom = 100;

    private static final int MAX_TABS = 10;
    private static final long BLOB_MAX = 96L * 1024 * 1024;

    private static final Set<String> BLOCK = new HashSet<>();
    static {
        String l = "doubleclick.net googlesyndication.com googleadservices.com google-analytics.com googletagmanager.com googletagservices.com "
                + "adservice.google.com pagead2.googlesyndication.com 2mdn.net adsystem.com adnxs.com adsrvr.org advertising.com adform.net "
                + "criteo.com criteo.net taboola.com outbrain.com revcontent.com mgid.com pubmatic.com rubiconproject.com openx.net openx.com "
                + "casalemedia.com indexexchange.com smartadserver.com amazon-adsystem.com media.net moatads.com adsafeprotected.com "
                + "scorecardresearch.com quantserve.com quantcount.com chartbeat.com chartbeat.net hotjar.com hotjar.io fullstory.com "
                + "mouseflow.com crazyegg.com clarity.ms segment.io segment.com mixpanel.com amplitude.com heap.io heapanalytics.com "
                + "branch.io appsflyer.com adjust.com kochava.com singular.net app-measurement.com facebook.net connect.facebook.net "
                + "graph.facebook.com pixel.facebook.com analytics.twitter.com ads-twitter.com static.ads-twitter.com ads.linkedin.com "
                + "px.ads.linkedin.com snap.licdn.com analytics.tiktok.com ads.tiktok.com business-api.tiktok.com bat.bing.com "
                + "ads.yahoo.com analytics.yahoo.com adtech.com advertising.yahoo.com gemini.yahoo.com yieldmo.com sharethrough.com "
                + "33across.com bidswitch.net contextweb.com lijit.com sovrn.com tapad.com bluekai.com krxd.net demdex.net omtrdc.net "
                + "everesttech.net agkn.com rlcdn.com liveramp.com id5-sync.com adition.com adscale.de yandex.ru mc.yandex.ru "
                + "adriver.ru top-fwz1.mail.ru counter.yadro.ru popads.net popcash.net propellerads.com exoclick.com juicyads.com "
                + "trafficjunky.net trafficfactory.biz clickadu.com hilltopads.net adsterra.com onclickads.net zedo.com "
                + "serving-sys.com sizmek.com flashtalking.com innovid.com tremorhub.com spotxchange.com spotx.tv teads.tv "
                + "newrelic.com nr-data.net bugsnag.com sentry-cdn.com optimizely.com vwo.com abtasty.com tealiumiq.com tiqcdn.com "
                + "ensighten.com cdn.cookielaw.org onetrust.com trustarc.com sharethis.com addthis.com disqus.com disquscdn.com";
        for (String d : l.split(" ")) if (!d.isEmpty()) BLOCK.add(d);
    }

    private static final String COSMETIC_AD = "ins.adsbygoogle,.adsbygoogle,[id^='google_ads'],[id^='div-gpt-ad'],iframe[src*='doubleclick.net'],iframe[src*='googlesyndication'],"
            + ".ad-container,.ad-banner,.ad-slot,.advert,.advertisement,[data-ad-slot],[aria-label='Advertisement']{display:none!important}";
    private static final String COSMETIC_BAN = "#onetrust-banner-sdk,#onetrust-consent-sdk,#CybotCookiebotDialog,#cookie-banner,#cookie-notice,#cookieConsent,"
            + ".cc-window,.cc-banner,.fc-consent-root,.truste_overlay,#sp_message_container_,.qc-cmp2-container,[id*='cookie-banner'],[class*='cookie-banner'],"
            + "[id*='cookie-consent'],[class*='cookie-consent'],[class*='CookieConsent']{display:none!important}";

    public PrivBrowser(Activity a, WebView mainView) {
        act = a;
        main = mainView;
        inbox = new File(a.getFilesDir(), "browser-inbox");
        try { inbox.mkdirs(); } catch (Throwable ignored) { }
        inst = this;
    }

    // ---------- helpers ----------
    private void ui(Runnable r) { act.runOnUiThread(r); }

    private static JSONObject o(Object... kv) {
        JSONObject j = new JSONObject();
        try { for (int i = 0; i + 1 < kv.length; i += 2) j.put(String.valueOf(kv[i]), kv[i + 1]); } catch (Throwable ignored) { }
        return j;
    }

    private void emit(JSONObject j) {
        final String s = "try{window.PB&&PB.ev(" + j.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + ")}catch(e){}";
        ui(new Runnable() { public void run() { try { main.evaluateJavascript(s, null); } catch (Throwable ignored) { } } });
    }

    private static boolean blockedHost(String host) {
        if (host == null) return false;
        host = host.toLowerCase();
        while (true) {
            if (BLOCK.contains(host)) return true;
            int i = host.indexOf('.');
            if (i < 0 || host.indexOf('.', i + 1) < 0) return false;
            host = host.substring(i + 1);
        }
    }

    private String idOf(WebView w) {
        for (Map.Entry<String, WebView> e : tabs.entrySet()) if (e.getValue() == w) return e.getKey();
        return "";
    }

    private static String cleanName(String n) {
        if (n == null) n = "";
        n = n.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        while (n.startsWith(".")) n = n.substring(1);
        if (n.length() > 120) n = n.substring(n.length() - 120);
        return n.isEmpty() ? "file" : n;
    }

    private static String extFor(String mime) {
        if (mime == null) return "";
        mime = mime.toLowerCase();
        if (mime.contains("jpeg")) return ".jpg";
        if (mime.contains("png")) return ".png";
        if (mime.contains("gif")) return ".gif";
        if (mime.contains("webp")) return ".webp";
        if (mime.contains("mp4")) return ".mp4";
        if (mime.contains("webm")) return ".webm";
        if (mime.contains("mpeg") && mime.startsWith("audio")) return ".mp3";
        if (mime.contains("ogg")) return ".ogg";
        if (mime.contains("wav")) return ".wav";
        if (mime.contains("aac") || mime.contains("m4a")) return ".m4a";
        if (mime.contains("pdf")) return ".pdf";
        if (mime.contains("zip")) return ".zip";
        if (mime.contains("plain")) return ".txt";
        return "";
    }

    private File uniqueFile(String name) {
        File f = new File(inbox, name);
        int i = 1;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        while (f.exists()) f = new File(inbox, base + "-" + (i++) + ext);
        return f;
    }

    private File inboxFile(String name) {
        try {
            if (name == null || name.contains("/") || name.contains("\\") || name.equals("..")) return null;
            File f = new File(inbox, name);
            return f.getParentFile() != null && f.getParentFile().equals(inbox) ? f : null;
        } catch (Throwable e) { return null; }
    }

    // ---------- pane / layout ----------
    private void ensurePane() {
        if (pane != null) return;
        pane = new FrameLayout(act);
        pane.setBackgroundColor(0xFF000000);
        act.addContentView(pane, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pane.setVisibility(visible ? View.VISIBLE : View.GONE);
        place();
    }

    private void place() {
        if (pane == null || !(pane.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        try {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pane.getLayoutParams();
            int[] w = new int[2], p = new int[2];
            main.getLocationOnScreen(w);
            if (pane.getParent() instanceof View) ((View) pane.getParent()).getLocationOnScreen(p);
            float d = act.getResources().getDisplayMetrics().density;
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.leftMargin = Math.round(w[0] - p[0] + hx * d);
            lp.topMargin = Math.round(w[1] - p[1] + hy * d);
            lp.width = Math.max(1, Math.round(hw * d));
            lp.height = Math.max(1, Math.round(hh * d));
            pane.setLayoutParams(lp);
        } catch (Throwable ignored) { }
    }

    @JavascriptInterface
    public void layout(final int x, final int y, final int w, final int h) {
        ui(new Runnable() { public void run() { hx = x; hy = y; hw = w; hh = h; ensurePane(); place(); } });
    }

    @JavascriptInterface
    public void show(final boolean on) {
        ui(new Runnable() { public void run() { visible = on; if (pane != null) pane.setVisibility(on ? View.VISIBLE : View.GONE); } });
    }

    // ---------- settings ----------
    @JavascriptInterface
    public void cfg(final String json) {
        ui(new Runnable() { public void run() {
            try {
                JSONObject j = new JSONObject(json);
                js = j.optBoolean("js", js); desk = j.optBoolean("desk", desk); ad = j.optBoolean("ad", ad);
                https = j.optBoolean("https", https); tpc = j.optBoolean("tpc", tpc); ck = j.optBoolean("ck", ck);
                img = j.optBoolean("img", img); dark = j.optBoolean("dark", dark); newtab = j.optBoolean("newtab", newtab);
                ban = j.optBoolean("ban", ban); webrtc = j.optBoolean("webrtc", webrtc); spoof = j.optBoolean("spoof", spoof);
                zoom = Math.max(50, Math.min(200, j.optInt("zoom", zoom)));
                CookieManager.getInstance().setAcceptCookie(ck);
                for (WebView w : tabs.values()) applyCfg(w);
            } catch (Throwable e) { emit(o("t", "err", "m", "settings: " + e)); }
        } });
    }

    private String uaFor(WebView w) {
        String ua = WebSettings.getDefaultUserAgent(act);
        if (desk) return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
        return ua.replace("; wv)", ")");
    }

    @SuppressWarnings("deprecation")
    private void applyCfg(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(js);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(newtab);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setBlockNetworkImage(!img);
        s.setTextZoom(zoom);
        s.setUserAgentString(uaFor(w));
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(desk);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 29) s.setForceDark(dark ? WebSettings.FORCE_DARK_ON : WebSettings.FORCE_DARK_OFF);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, ck && tpc);
    }

    // ---------- tabs ----------
    private WebView makeView(final String id) {
        final WebView w = new WebView(act);
        w.setBackgroundColor(0xFF000000);
        w.setVisibility(View.GONE);
        applyCfg(w);
        w.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                String sc = r.getUrl().getScheme() == null ? "" : r.getUrl().getScheme().toLowerCase();
                if (sc.equals("https") || sc.equals("blob") || sc.equals("data") || sc.equals("about")) return false;
                if (sc.equals("http")) {
                    if (https) { v.loadUrl("https:" + u.substring(5)); return true; }
                    return false;
                }
                emit(o("t", "blocked", "id", idOf(v), "url", u));
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                try {
                    if (ad && blockedHost(r.getUrl().getHost()))
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                } catch (Throwable ignored) { }
                return null;
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap f) {
                emit(o("t", "start", "id", idOf(v), "url", url));
                String pre = "(function(){try{var n=navigator,d=function(k,v){try{Object.defineProperty(n,k,{get:function(){return v},configurable:true})}catch(e){}};"
                        + "d('globalPrivacyControl',true);d('doNotTrack','1');"
                        + (spoof ? "d('hardwareConcurrency',4);d('deviceMemory',4);d('getBattery',undefined);" : "")
                        + (webrtc ? "window.RTCPeerConnection=undefined;window.webkitRTCPeerConnection=undefined;window.RTCDataChannel=undefined;" : "")
                        + "}catch(e){}})()";
                v.evaluateJavascript(pre, null);
            }
            @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) {
                emit(o("t", "nav", "id", idOf(v), "url", url == null ? "" : url, "back", v.canGoBack(), "fwd", v.canGoForward()));
            }
            @Override public void onPageFinished(WebView v, String url) {
                String css = (ad ? COSMETIC_AD : "") + (ban ? COSMETIC_BAN : "");
                if (!css.isEmpty())
                    v.evaluateJavascript("(function(){try{var s=document.createElement('style');s.textContent=" + JSONObject.quote(css) + ";(document.head||document.documentElement).appendChild(s)}catch(e){}})()", null);
                emit(o("t", "done", "id", idOf(v), "url", url, "title", v.getTitle() == null ? "" : v.getTitle(),
                        "back", v.canGoBack(), "fwd", v.canGoForward()));
            }
            @Override @SuppressWarnings("deprecation")
            public void onReceivedError(WebView v, int code, String desc, String url) {
                emit(o("t", "error", "id", idOf(v), "url", url == null ? "" : url, "code", code, "m", desc == null ? "" : desc));
            }
            @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
                h.cancel();
                emit(o("t", "error", "id", idOf(v), "url", e.getUrl() == null ? "" : e.getUrl(), "code", -99, "m", "The site's security certificate is not trusted, so it was not opened."));
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { emit(o("t", "prog", "id", idOf(v), "p", p)); }
            @Override public void onReceivedTitle(WebView v, String t) { emit(o("t", "title", "id", idOf(v), "title", t == null ? "" : t)); }
            @Override public void onPermissionRequest(PermissionRequest r) { r.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) { cb.invoke(origin, false, false); }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                cb.onReceiveValue(null);
                emit(o("t", "toast", "m", "Uploading files from the browser is not supported"));
                return true;
            }
            @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, Message msg) {
                if (!newtab || !gesture || tabs.size() >= MAX_TABS) return false;
                final String nid = "t" + System.nanoTime();
                WebView nw = makeView(nid);
                tabs.put(nid, nw);
                if (pane != null) pane.addView(nw, new FrameLayout.LayoutParams(-1, -1));
                ((WebView.WebViewTransport) msg.obj).setWebView(nw);
                msg.sendToTarget();
                emit(o("t", "newtab", "id", nid, "from", idOf(v)));
                return true;
            }
            @Override public void onShowCustomView(View view, CustomViewCallback cb) {
                if (fsView != null) { cb.onCustomViewHidden(); return; }
                fsView = view; fsCb = cb; fs = true;
                view.setBackgroundColor(0xFF000000);
                act.addContentView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() { exitFsNow(); }
        });
        w.setDownloadListener(new android.webkit.DownloadListener() {
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                download(idOf(w), url, nameFromDisposition(cd, url, mime), mime);
            }
        });
        w.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                try {
                    WebView.HitTestResult h = ((WebView) v).getHitTestResult();
                    int t = h.getType();
                    String ex1 = h.getExtra();
                    if (ex1 == null || ex1.isEmpty()) return false;
                    String kind = t == WebView.HitTestResult.IMAGE_TYPE ? "img"
                            : t == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ? "imglink"
                            : t == WebView.HitTestResult.SRC_ANCHOR_TYPE ? "link" : "";
                    if (kind.isEmpty()) return false;
                    emit(o("t", "ctx", "id", idOf((WebView) v), "kind", kind, "url", ex1));
                    return true;
                } catch (Throwable e) { return false; }
            }
        });
        w.setFindListener(new WebView.FindListener() {
            public void onFindResultReceived(int active, int matches, boolean done) {
                emit(o("t", "find", "active", active, "n", matches));
            }
        });
        return w;
    }

    private static String nameFromDisposition(String cd, String url, String mime) {
        String n = "";
        try {
            if (cd != null) {
                int i = cd.toLowerCase().indexOf("filename*=");
                if (i >= 0) {
                    String v = cd.substring(i + 10).split(";")[0].trim();
                    int q = v.indexOf("''");
                    n = Uri.decode(q >= 0 ? v.substring(q + 2) : v);
                } else {
                    i = cd.toLowerCase().indexOf("filename=");
                    if (i >= 0) n = cd.substring(i + 9).split(";")[0].trim().replace("\"", "");
                }
            }
            if (n.isEmpty()) {
                String p = Uri.parse(url).getLastPathSegment();
                n = p == null ? "" : Uri.decode(p);
            }
        } catch (Throwable ignored) { }
        n = cleanName(n);
        if (n.equals("file") || n.indexOf('.') < 0) n = n + extFor(mime);
        return n;
    }

    private void exitFsNow() {
        try {
            if (fsView != null && fsView.getParent() instanceof ViewGroup) ((ViewGroup) fsView.getParent()).removeView(fsView);
            if (fsCb != null) fsCb.onCustomViewHidden();
        } catch (Throwable ignored) { }
        fsView = null; fsCb = null; fs = false;
    }

    @JavascriptInterface
    public boolean fsExit() {
        if (!fs) return false;
        ui(new Runnable() { public void run() { exitFsNow(); } });
        return true;
    }

    @JavascriptInterface
    public void tabNew(final String id, final String url) {
        ui(new Runnable() { public void run() {
            try {
                ensurePane();
                if (tabs.containsKey(id) || tabs.size() >= MAX_TABS) return;
                WebView w = makeView(id);
                tabs.put(id, w);
                pane.addView(w, new FrameLayout.LayoutParams(-1, -1));
                if (url != null && !url.isEmpty()) load(w, url);
            } catch (Throwable e) { emit(o("t", "err", "m", "new tab: " + e)); }
        } });
    }

    @JavascriptInterface
    public void tabShow(final String id) {
        ui(new Runnable() { public void run() {
            cur = id;
            for (Map.Entry<String, WebView> e : tabs.entrySet()) e.getValue().setVisibility(e.getKey().equals(id) ? View.VISIBLE : View.GONE);
            WebView w = tabs.get(id);
            if (w != null) emit(o("t", "done", "id", id, "url", w.getUrl() == null ? "" : w.getUrl(), "title", w.getTitle() == null ? "" : w.getTitle(),
                    "back", w.canGoBack(), "fwd", w.canGoForward()));
        } });
    }

    @JavascriptInterface
    public void tabClose(final String id) {
        ui(new Runnable() { public void run() {
            WebView w = tabs.remove(id);
            if (w == null) return;
            try { w.stopLoading(); w.loadUrl("about:blank"); if (pane != null) pane.removeView(w); w.destroy(); } catch (Throwable ignored) { }
        } });
    }

    private void load(WebView w, String url) {
        String u = url;
        if (https && u.startsWith("http://")) u = "https://" + u.substring(7);
        java.util.HashMap<String, String> h = new java.util.HashMap<>();
        h.put("DNT", "1");
        h.put("Sec-GPC", "1");
        w.loadUrl(u, h);
    }

    @JavascriptInterface
    public void go(final String id, final String url) {
        ui(new Runnable() { public void run() { WebView w = tabs.get(id); if (w != null) load(w, url); } });
    }

    @JavascriptInterface
    public void nav(final String id, final String what) {
        ui(new Runnable() { public void run() {
            WebView w = tabs.get(id);
            if (w == null) return;
            if (what.equals("back") && w.canGoBack()) w.goBack();
            else if (what.equals("fwd") && w.canGoForward()) w.goForward();
            else if (what.equals("reload")) w.reload();
            else if (what.equals("stop")) w.stopLoading();
            else if (what.equals("top")) w.scrollTo(0, 0);
        } });
    }

    @JavascriptInterface
    public void run(final String id, final String script, final String key) {
        ui(new Runnable() { public void run() {
            WebView w = tabs.get(id);
            if (w == null) { emit(o("t", "res", "k", key, "v", "null")); return; }
            w.evaluateJavascript(script, new ValueCallback<String>() {
                public void onReceiveValue(String v) { emit(o("t", "res", "k", key, "v", v == null ? "null" : v)); }
            });
        } });
    }

    @JavascriptInterface
    public void find(final String id, final String q, final int dir) {
        ui(new Runnable() { public void run() {
            WebView w = tabs.get(id);
            if (w == null) return;
            if (q == null || q.isEmpty()) w.clearMatches();
            else if (dir == 0) w.findAllAsync(q);
            else w.findNext(dir > 0);
        } });
    }

    @JavascriptInterface
    public void clearData(final boolean everything) {
        ui(new Runnable() { public void run() {
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.removeAllCookies(null);
                cm.flush();
                WebStorage.getInstance().deleteAllData();
                for (WebView w : tabs.values()) { w.clearCache(true); w.clearHistory(); w.clearFormData(); }
                emit(o("t", "toast", "m", "Browser data cleared"));
            } catch (Throwable e) { emit(o("t", "err", "m", "clear: " + e)); }
        } });
    }

    @JavascriptInterface
    public void closeAll() {
        ui(new Runnable() { public void run() {
            exitFsNow();
            for (WebView w : tabs.values()) {
                try { w.stopLoading(); w.loadUrl("about:blank"); if (pane != null) pane.removeView(w); w.destroy(); } catch (Throwable ignored) { }
            }
            tabs.clear();
            cur = "";
            for (AtomicBoolean b : cancels.values()) b.set(true);
            if (pane != null) pane.setVisibility(View.GONE);
            visible = false;
        } });
    }

    static void destroyAll() {
        try { if (inst != null) inst.closeAll(); } catch (Throwable ignored) { }
    }

    @JavascriptInterface
    public void ext(final String url) {
        ui(new Runnable() { public void run() {
            try {
                String sc = Uri.parse(url).getScheme();
                if (sc == null || !(sc.equals("http") || sc.equals("https"))) return;
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                act.startActivity(Intent.createChooser(i, "Open with"));
            } catch (Throwable e) { emit(o("t", "toast", "m", "No app can open this link")); }
        } });
    }

    // ---------- downloads (private inbox) ----------
    @JavascriptInterface
    public void download(final String tabId, final String url, final String name, final String mime) {
        if (url == null) return;
        if (url.startsWith("blob:")) { blobDownload(tabId, url, name); return; }
        final String id = "d" + (++dlSeq) + "x" + (System.currentTimeMillis() % 100000);
        final AtomicBoolean cancel = new AtomicBoolean(false);
        cancels.put(id, cancel);
        final String ref = tabs.get(tabId) != null && tabs.get(tabId).getUrl() != null ? tabs.get(tabId).getUrl() : "";
        final String ua = desk ? uaFor(null) : WebSettings.getDefaultUserAgent(act).replace("; wv)", ")");
        emit(o("t", "dl", "id", id, "name", name, "state", "run", "got", 0, "total", 0));
        ex.execute(new Runnable() { public void run() {
            File f = null;
            try {
                if (url.startsWith("data:")) {
                    f = uniqueFile(cleanName(name));
                    int c = url.indexOf(',');
                    String head = url.substring(0, c < 0 ? 0 : c);
                    String body = c < 0 ? "" : url.substring(c + 1);
                    byte[] b = head.contains(";base64") ? Base64.decode(body, Base64.DEFAULT) : Uri.decode(body).getBytes("UTF-8");
                    FileOutputStream o2 = new FileOutputStream(f);
                    try { o2.write(b); } finally { o2.close(); }
                    emit(o("t", "dl", "id", id, "name", f.getName(), "state", "ok", "got", b.length, "total", b.length));
                    return;
                }
                String u = url;
                if (https && u.startsWith("http://")) u = "https://" + u.substring(7);
                if (!u.startsWith("https://") && !u.startsWith("http://")) throw new Exception("Unsupported link");
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", ua);
                c.setRequestProperty("DNT", "1");
                c.setRequestProperty("Sec-GPC", "1");
                if (!ref.isEmpty() && ref.startsWith("https://")) c.setRequestProperty("Referer", ref);
                String ck2 = CookieManager.getInstance().getCookie(u);
                if (ck2 != null && ck) c.setRequestProperty("Cookie", ck2);
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("Server answered " + code);
                long total = c.getContentLength();
                String ct = c.getContentType() == null ? "" : c.getContentType().split(";")[0].trim();
                String nm = name;
                String cdh = c.getHeaderField("Content-Disposition");
                if (cdh != null) { String n2 = nameFromDisposition(cdh, u, ct); if (!n2.equals("file")) nm = n2; }
                if (nm.indexOf('.') < 0) nm = nm + extFor(ct);
                f = uniqueFile(cleanName(nm));
                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(f);
                long got = 0, last = 0;
                try {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (cancel.get()) throw new Exception("Cancelled");
                        out.write(buf, 0, n);
                        got += n;
                        long now = System.currentTimeMillis();
                        if (now - last > 400) { last = now; emit(o("t", "dl", "id", id, "name", f.getName(), "state", "run", "got", got, "total", total)); }
                    }
                } finally { try { out.close(); } catch (Throwable ignored) { } try { in.close(); } catch (Throwable ignored) { } }
                emit(o("t", "dl", "id", id, "name", f.getName(), "state", "ok", "got", got, "total", got));
            } catch (Throwable e) {
                if (f != null) f.delete();
                emit(o("t", "dl", "id", id, "name", name, "state", "err", "err", String.valueOf(e.getMessage() == null ? e : e.getMessage())));
            } finally { cancels.remove(id); }
        } });
    }

    @JavascriptInterface
    public void dlCancel(String id) {
        AtomicBoolean b = cancels.get(id);
        if (b != null) b.set(true);
    }

    /** blob: URLs only exist inside the page, so the page is asked for the bytes in slices (no Java object is given to the page). */
    private void blobDownload(final String tabId, final String url, final String name) {
        final String id = "d" + (++dlSeq) + "x" + (System.currentTimeMillis() % 100000);
        ui(new Runnable() { public void run() {
            final WebView w = tabs.get(tabId);
            if (w == null) return;
            emit(o("t", "dl", "id", id, "name", name, "state", "run", "got", 0, "total", 0));
            String get = "(function(u){try{var x=new XMLHttpRequest();x.open('GET',u,false);x.overrideMimeType('text/plain; charset=x-user-defined');x.send();"
                    + "if(x.status&&x.status!==200)return -1;window.__pbB=x.responseText;return window.__pbB.length}catch(e){return -1}})(" + JSONObject.quote(url) + ")";
            w.evaluateJavascript(get, new ValueCallback<String>() {
                public void onReceiveValue(String v) {
                    long len = -1;
                    try { len = Long.parseLong(v.trim()); } catch (Throwable ignored) { }
                    if (len < 0 || len > BLOB_MAX) {
                        w.evaluateJavascript("delete window.__pbB", null);
                        emit(o("t", "dl", "id", id, "name", name, "state", "err", "err", len > BLOB_MAX ? "Too large for a page-made file (96 MB max)" : "The page would not hand over the file"));
                        return;
                    }
                    File f = uniqueFile(cleanName(name));
                    blobSlice(w, id, f, 0, len);
                }
            });
        } });
    }

    private void blobSlice(final WebView w, final String id, final File f, final long from, final long len) {
        final long CH = 786432; // multiple of 3 so base64 pieces join cleanly
        final long to = Math.min(len, from + CH);
        String js2 = "(function(a,b){var s=window.__pbB;if(!s)return '';var r='',i,e=Math.min(b,s.length);for(i=a;i<e;i+=8192){var q=s.substring(i,Math.min(i+8192,e)),arr=new Array(q.length),j;"
                + "for(j=0;j<q.length;j++)arr[j]=q.charCodeAt(j)&255;r+=String.fromCharCode.apply(null,arr)}return btoa(r)})(" + from + "," + to + ")";
        w.evaluateJavascript(js2, new ValueCallback<String>() {
            public void onReceiveValue(final String v) {
                ex.execute(new Runnable() { public void run() {
                    try {
                        String b64 = v == null ? "" : v.replace("\"", "");
                        if (b64.isEmpty() && to > from) throw new Exception("Page lost the file");
                        FileOutputStream out = new FileOutputStream(f, from > 0);
                        try { out.write(Base64.decode(b64, Base64.DEFAULT)); } finally { out.close(); }
                        if (to >= len) {
                            emit(o("t", "dl", "id", id, "name", f.getName(), "state", "ok", "got", len, "total", len));
                            ui(new Runnable() { public void run() { w.evaluateJavascript("delete window.__pbB", null); } });
                        } else {
                            emit(o("t", "dl", "id", id, "name", f.getName(), "state", "run", "got", to, "total", len));
                            ui(new Runnable() { public void run() { blobSlice(w, id, f, to, len); } });
                        }
                    } catch (Throwable e) {
                        f.delete();
                        emit(o("t", "dl", "id", id, "name", f.getName(), "state", "err", "err", String.valueOf(e.getMessage())));
                        ui(new Runnable() { public void run() { w.evaluateJavascript("delete window.__pbB", null); } });
                    }
                } });
            }
        });
    }

    // ---------- inbox (read by the vault page) ----------
    @JavascriptInterface
    public String inboxList() {
        JSONArray a = new JSONArray();
        try {
            File[] fs2 = inbox.listFiles();
            if (fs2 != null) for (File f : fs2) if (f.isFile()) a.put(o("name", f.getName(), "size", f.length(), "mod", f.lastModified()));
        } catch (Throwable ignored) { }
        return a.toString();
    }

    @JavascriptInterface
    public String readChunk(String name, double off, int len) {
        RandomAccessFile r = null;
        try {
            File f = inboxFile(name);
            if (f == null || !f.isFile() || len <= 0 || len > 4 * 1048576) return "";
            r = new RandomAccessFile(f, "r");
            r.seek((long) off);
            byte[] b = new byte[len];
            int n = r.read(b);
            if (n <= 0) return "";
            return Base64.encodeToString(b, 0, n, Base64.NO_WRAP);
        } catch (Throwable e) { return ""; }
        finally { try { if (r != null) r.close(); } catch (Throwable ignored) { } }
    }

    // ---------- V0.6.35: copy an inbox file into the phone's public Downloads folder ----------
    /** Copies filesDir/browser-inbox/<name> into the phone's Downloads folder (MediaStore on Android 10+, no permission needed;
     *  the old public folder on Android 9 and older). The inbox copy stays, so the vault sealing that follows still works.
     *  Result comes back as an event: t=pub, state ok|err, name (inbox name), saved (name in Downloads) or err. */
    @JavascriptInterface
    public void savePhone(final String name) {
        final File src = inboxFile(name);
        if (src == null || !src.isFile()) {
            emit(o("t", "pub", "name", String.valueOf(name), "state", "err", "err", "The file is no longer in the browser inbox"));
            return;
        }
        ex.execute(new Runnable() { public void run() {
            InputStream in = null;
            OutputStream out = null;
            Uri uri = null;
            File pubFile = null;
            try {
                String ext = "";
                int dot = name.lastIndexOf('.');
                if (dot > 0 && dot < name.length() - 1) ext = name.substring(dot + 1).toLowerCase();
                String mime = ext.isEmpty() ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
                String saved = name;
                in = new FileInputStream(src);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver cr = act.getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) throw new Exception("Android would not create the file in Downloads");
                    out = cr.openOutputStream(uri);
                    if (out == null) throw new Exception("Android would not open the file in Downloads");
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.flush();
                    out.close();
                    out = null;
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    cr.update(uri, done, null, null);
                    Cursor c = null;
                    try {
                        c = cr.query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
                        if (c != null && c.moveToFirst() && c.getString(0) != null) saved = c.getString(0);
                    } catch (Throwable ignored) {
                    } finally { try { if (c != null) c.close(); } catch (Throwable ignored) { } }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (dir == null) throw new Exception("No Downloads folder on this phone");
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create the Downloads folder");
                    String base = name, e2 = "";
                    if (dot > 0) { base = name.substring(0, dot); e2 = name.substring(dot); }
                    pubFile = new File(dir, name);
                    int i = 1;
                    while (pubFile.exists()) pubFile = new File(dir, base + "-" + (i++) + e2);
                    out = new FileOutputStream(pubFile);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.flush();
                    out.close();
                    out = null;
                    saved = pubFile.getName();
                    try { MediaScannerConnection.scanFile(act, new String[]{pubFile.getAbsolutePath()}, new String[]{mime}, null); } catch (Throwable ignored) { }
                }
                emit(o("t", "pub", "name", name, "state", "ok", "saved", saved));
            } catch (Throwable e) {
                try { if (out != null) out.close(); } catch (Throwable ignored) { }
                try { if (uri != null) act.getContentResolver().delete(uri, null, null); } catch (Throwable ignored) { }
                try { if (pubFile != null) pubFile.delete(); } catch (Throwable ignored) { }
                emit(o("t", "pub", "name", name, "state", "err", "err", String.valueOf(e.getMessage() == null ? e : e.getMessage())));
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) { }
            }
        } });
    }

    @JavascriptInterface
    public boolean inboxDel(String name) {
        try { File f = inboxFile(name); return f != null && f.delete(); } catch (Throwable e) { return false; }
    }
}
