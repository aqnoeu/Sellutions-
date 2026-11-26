package com.aqnoeu.sellutions;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.WindowInsets;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView web;
    private boolean pageReady = false;
    private boolean updateChecked = false;
    private UpdateManager updates;
    private AffiliateConnector affiliateConnector;
    private ShopeeConnector shopeeConnector;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(255, 248, 240));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        // Own the entire Activity window, then inset the WebView *layout*, not its
        // internal padding. WebView padding does not resize its CSS viewport on
        // edge-to-edge Android 15/16, which previously overlapped both bars.
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        web = new WebView(this);
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(web, webParams);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.statusBars()
                    | WindowInsets.Type.navigationBars()
                    | WindowInsets.Type.displayCutout());
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
                if (lp.leftMargin != bars.left || lp.topMargin != bars.top
                        || lp.rightMargin != bars.right || lp.bottomMargin != bars.bottom) {
                    lp.setMargins(bars.left, bars.top, bars.right, bars.bottom);
                    web.setLayoutParams(lp);
                }
                return insets;
            });
            root.post(root::requestApplyInsets);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        web.setBackgroundColor(Color.WHITE);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void shareText(String message) {
                if (message == null || message.trim().isEmpty()) return;
                runOnUiThread(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, message);
                    startActivity(Intent.createChooser(send, "Compartilhar oferta"));
                });
            }
        }, "AndroidShare");
        updates = new UpdateManager(this, web);
        affiliateConnector = new AffiliateConnector(this, web);
        web.addJavascriptInterface(affiliateConnector, "AndroidAffiliate");
        shopeeConnector = new ShopeeConnector(this, web);
        web.addJavascriptInterface(shopeeConnector, "AndroidShopee");
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void check() { updates.check(); }
            @JavascriptInterface public String version() {
                try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
                catch (Exception e) { return "desconhecida"; }
            }
        }, "AndroidUpdates");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (url.startsWith("file:///android_asset/")) {
                    pageReady = true;
                    receiveSharedLink(getIntent());
                    if (!updateChecked && updates != null) { updateChecked = true; updates.checkSilently(); }
                }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && request.isForMainFrame()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) {}
                }
                return true;
            }
        });
        setContentView(root);
        // The local origin permits template persistence without exposing a remote server.
        // Asset loading is restricted to the packaged HTML file.
        web.loadUrl("file:///android_asset/index.html");
    }

    private void receiveSharedLink(Intent intent) {
        if (!pageReady || intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        if (!"text/plain".equals(intent.getType())) return;
        String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (shared == null) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("https://[^\\s<>]+").matcher(shared);
        if (matcher.find()) {
            String url = matcher.group();
            web.evaluateJavascript("fillAffiliateLink(" + JSONObject.quote(url) + ")", null);
        }
        intent.setAction(null); // Avoid reimport on activity recreation.
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receiveSharedLink(intent);
    }

    @Override public void onBackPressed() {
        if (web != null) {
            web.evaluateJavascript(
                "(function(){var x=document.getElementById('overlay');" +
                "if(x&&x.classList.contains('open')){closeSheet();return;}" +
                "openTab('home');})()", null);
        } else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (updates != null) { updates.close(); updates = null; }
        if (affiliateConnector != null) { affiliateConnector.close(); affiliateConnector = null; }
        if (shopeeConnector != null) { shopeeConnector.close(); shopeeConnector = null; }
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
