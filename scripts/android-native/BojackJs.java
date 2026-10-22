package com.fezzy.vault;

import android.app.Activity;
import android.webkit.JavascriptInterface;

/**
 * Plain Android bridge, exposed to the page as window.BojackNative.
 * It does NOT depend on Capacitor, so Settings > System info can still show what happened at start-up
 * (status()) even when Capacitor failed to register a plugin.
 */
public class BojackJs {
    private final Activity act;

    BojackJs(Activity a) { act = a; }

    @JavascriptInterface
    public String status() { return MainActivity.regLog; }
}
