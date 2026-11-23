package com.aqnoeu.sellutions;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TikTok Creator pilot: never report connected without backend token verification. */
public final class AffiliateConnector {
    private final Activity activity;
    private final WebView web;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile String session;
    private volatile String backend;
    AffiliateConnector(Activity a,WebView w){ activity=a;web=w; }

    @JavascriptInterface public void configureBackend(String address){
        try {
            Uri u=Uri.parse(address.trim());
            if(!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null ||
                u.getUserInfo()!=null || u.getPort()!=-1 || u.getQuery()!=null || u.getFragment()!=null) {
                emit("error","URL HTTPS pública inválida."); return;
            }
            backend="https://"+u.getHost().toLowerCase();
            activity.getPreferences(Activity.MODE_PRIVATE).edit().putString("affiliate_endpoint",backend).apply();
            emit("configuration","Endpoint salvo. A conexão só será confirmada após autorização oficial.");
        } catch(Exception e){emit("error","Endpoint HTTPS inválido.");}
    }
    @JavascriptInterface public void init(){
        backend=activity.getPreferences(Activity.MODE_PRIVATE).getString("affiliate_endpoint",null);
        emit("configuration",backend==null?"Serviço TikTok ainda não configurado.":"Serviço configurado: "+backend);
    }
    @JavascriptInterface public void startTikTok(){
        if(backend==null){emit("error","Configure primeiro o serviço oficial de integração.");return;}
        worker.execute(()->{
            try{
                JSONObject result=request("/v1/tiktok/begin","POST",null);
                if(!"pending".equals(result.optString("status")))throw new Exception("Autorização não iniciada.");
                String next=result.getString("authorization_url");
                Uri uri=Uri.parse(next);
                if(!"https".equals(uri.getScheme()) ||
                    !"shop.tiktok.com".equalsIgnoreCase(uri.getHost()))
                    throw new Exception("Destino de autorização inválido.");
                session=result.getString("session");
                emit("pending","Autorização aberta. Conclua na plataforma oficial e depois toque em Verificar conexão.");
                activity.runOnUiThread(()->activity.startActivity(new Intent(Intent.ACTION_VIEW,uri)));
            }catch(Exception e){emit("error",e.getMessage());}
        });
    }
    @JavascriptInterface public void checkTikTok(){
        if(session==null){emit("error","Nenhuma autorização iniciada neste aparelho.");return;}
        worker.execute(()->{
            try{
                JSONObject r=request("/v1/tiktok/status","GET",session);
                String state=r.optString("status");
                if("connected".equals(state) && r.optString("open_id").length()>0)
                    emit("connected","TikTok Shop autorizado: "+r.optString("open_id"));
                else emit("pending","TikTok Shop: "+state+". Sem acesso de afiliado confirmado.");
            }catch(Exception e){emit("error",e.getMessage());}
        });
    }
    @JavascriptInterface public void disconnectTikTok(){
        if(session==null){emit("disconnected","Nenhuma sessão TikTok neste aparelho.");return;}
        worker.execute(()->{
            try {request("/v1/tiktok/disconnect","POST",session);}catch(Exception ignored){}
            session=null;emit("disconnected","Sessão local encerrada. Revogue também na plataforma, se necessário.");
        });
    }
    private JSONObject request(String path,String method,String auth)throws Exception{
        URL u=new URL(backend+path);
        HttpURLConnection c=(HttpURLConnection)u.openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestMethod(method);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");
        if(auth!=null)c.setRequestProperty("Authorization","Bearer "+auth);
        if("POST".equals(method)){
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
            try(OutputStream o=c.getOutputStream()){o.write("{}".getBytes(StandardCharsets.UTF_8));}
        }
        try{
            int status=c.getResponseCode();
            if(status<200||status>=300)throw new Exception("Serviço indisponível (HTTP "+status+").");
            try(InputStream i=c.getInputStream(); ByteArrayOutputStream b=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n;
                while((n=i.read(buf))!=-1){if(b.size()+n>65536)throw new Exception("Resposta excede limite");b.write(buf,0,n);}
                return new JSONObject(b.toString("UTF-8"));
            }
        } finally {c.disconnect();}
    }
    private void emit(String status,String message){
        activity.runOnUiThread(()->{
            if(web!=null)web.evaluateJavascript("onAffiliateConnector("+JSONObject.quote(status)+","+JSONObject.quote(message)+")",null);
        });
    }
    public void close(){worker.shutdownNow();}
}
