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
import android.os.Handler;
import android.os.Looper;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private WebView web;
    private boolean pageReady = false;
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
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
            @JavascriptInterface public void searchOffers(String term, int maxPrice) {
                String query = term == null ? "" : term.trim();
                if (query.isEmpty() || query.length() > 100) {
                    deliverOffers("{\"error\":\"Digite uma pesquisa de até 100 caracteres.\"}");
                    return;
                }
                int price = Math.max(0, Math.min(maxPrice, 100000));
                network.execute(() -> fetchOffers(query, price));
            }
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


    private void deliverOffers(String json) {
        ui.post(() -> {
            if (web != null && pageReady) {
                web.evaluateJavascript("onOffersResponse(" + JSONObject.quote(json) + ")", null);
            }
        });
    }
    private static String readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (bytes.size() + n > 1024 * 1024) throw new IllegalStateException("Resposta muito grande");
            bytes.write(buffer, 0, n);
        }
        return bytes.toString("UTF-8");
    }
    private void fetchOffers(String term, int maxPrice) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://cloo.be/mcp");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            JSONObject args = new JSONObject();
            args.put("termo", term);
            args.put("limite", 30);
            if (maxPrice > 0) args.put("preco_maximo", maxPrice);
            JSONObject params = new JSONObject();
            params.put("name", "buscar_ofertas");
            params.put("arguments", args);
            JSONObject payload = new JSONObject();
            payload.put("jsonrpc", "2.0");
            payload.put("id", 1);
            payload.put("method", "tools/call");
            payload.put("params", params);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("Serviço de ofertas indisponível (HTTP " + code + ")");
            String result;
            try (InputStream input = connection.getInputStream()) { result = readLimited(input); }
            String type = connection.getContentType();
            if (type != null && type.contains("text/event-stream")) {
                String last = null;
                for (String line : result.split("\\r?\\n")) {
                    if (line.startsWith("data:")) {
                        String value = line.substring(5).trim();
                        if (!value.isEmpty() && !"[DONE]".equals(value)) last = value;
                    }
                }
                if (last == null) throw new IllegalStateException("Nenhuma oferta recebida do serviço");
                result = last;
            }
            new JSONObject(result);
            deliverOffers(result);
        } catch (Exception e) {
            try {
                JSONObject response = new JSONObject();
                response.put("error", "Falha na pesquisa: " + e.getMessage());
                deliverOffers(response.toString());
            } catch (Exception ignored) { deliverOffers("{\"error\":\"Erro na conexão.\"}"); }
        } finally {
            if (connection != null) connection.disconnect();
        }
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
        network.shutdownNow();
        super.onDestroy();
    }
}
