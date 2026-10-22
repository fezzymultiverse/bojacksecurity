package com.fezzy.vault;

import android.os.Bundle;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    /** V0.6.31: what happened at start-up. Shown in Settings > System info (window.BojackNative.status()). */
    static String regLog = "";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Each plugin is registered on its own, so one failure can never hide the others, and the reason is kept.
        try { registerPlugin(BojackKeepAlive.class); regLog += "KeepAlive queued; "; } catch (Throwable e) { regLog += "KeepAlive register FAILED: " + e + "; "; }
        try { registerPlugin(BojackStore.class); regLog += "Store queued; "; } catch (Throwable e) { regLog += "Store register FAILED: " + e + "; "; }
        super.onCreate(savedInstanceState);
        // Keep the WebView renderer at foreground priority even when the app is not visible, so a long
        // import is not starved or killed the moment you switch to another app.
        try {
            WebView w = getBridge().getWebView();
            if (android.os.Build.VERSION.SDK_INT >= 26) w.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        } catch (Exception ignored) { }
        // What Capacitor really loaded, then the direct bridge that reports start-up status without Capacitor.
        try {
            regLog += "Capacitor loaded: KeepAlive=" + (getBridge().getPlugin("BojackKeepAlive") != null)
                    + " Store=" + (getBridge().getPlugin("BojackStore") != null) + "; ";
        } catch (Throwable e) { regLog += "Capacitor check FAILED: " + e + "; "; }
        try {
            getBridge().getWebView().addJavascriptInterface(new BojackJs(this), "BojackNative");
            regLog += "direct bridge on; ";
        } catch (Throwable e) { regLog += "direct bridge FAILED: " + e + "; "; }
        // V0.6.34: vault storage that does not need Capacitor to register BojackStore (window.BojackST). Same files on disk as BojackStore.
        try {
            getBridge().getWebView().addJavascriptInterface(new BojackStoreJs(this, getBridge().getWebView()), "BojackST");
            regLog += "store bridge on; ";
        } catch (Throwable e) { regLog += "store bridge FAILED: " + e + "; "; }
        // V0.6.33: the privacy browser owns its own web pages; the vault page draws its bars (window.BojackPB).
        try {
            getBridge().getWebView().addJavascriptInterface(new PrivBrowser(this, getBridge().getWebView()), "BojackPB");
            regLog += "browser bridge on";
        } catch (Throwable e) { regLog += "browser bridge FAILED: " + e; }
    }

    @Override
    public void onDestroy() {
        try { PrivBrowser.destroyAll(); } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
