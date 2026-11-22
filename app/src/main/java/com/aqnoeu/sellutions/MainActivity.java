package com.aqnoeu.sellutions;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView web;
    private boolean pageReady = false;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(255, 248, 240));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(255, 248, 240));
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
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (url.startsWith("file:///android_asset/")) {
                    pageReady = true;
                    receiveSharedLink(getIntent());
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
        setContentView(web);
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
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
