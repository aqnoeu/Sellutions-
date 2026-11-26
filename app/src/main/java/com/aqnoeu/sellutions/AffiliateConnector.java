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
    private static final String OFFICIAL_BACKEND="https://sellutions.vercel.app";
    private volatile String backend=OFFICIAL_BACKEND;
    AffiliateConnector(Activity a,WebView w){ activity=a;web=w; }

    @JavascriptInterface public void configureBackend(String address){
        if(!OFFICIAL_BACKEND.equals(address==null?"":address.trim())){emit("error","Use somente o servidor oficial do Sellutions.");return;}
        backend=OFFICIAL_BACKEND;
        emit("configuration","Servidor oficial configurado.");
    }
    @JavascriptInterface public void init(){
        backend=OFFICIAL_BACKEND;
        emit("configuration","Servidor oficial configurado. Autorize sua conta no TikTok quando a integração estiver liberada.");
    }
    @JavascriptInterface public void startTikTok(){
        worker.execute(()->{
            try{
                emit("pending","Abrindo autorização oficial do TikTok…");
                JSONObject result=request("/v1/tiktok/begin","POST",null,new JSONObject());
                if(!"pending".equals(result.optString("status")))throw new Exception("Autorização não iniciada.");
                Uri uri=Uri.parse(result.getString("authorization_url"));
                if(!"https".equals(uri.getScheme())||!"shop.tiktok.com".equalsIgnoreCase(uri.getHost())||!"/alliance/creator/auth".equals(uri.getPath())||uri.getUserInfo()!=null||uri.getPort()!=-1)
                    throw new Exception("Destino de autorização inválido.");
                session=result.getString("session");
                emit("pending","Conclua a autorização no TikTok e depois toque em Verificar conexão.");
                activity.runOnUiThread(()->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){emit("error","Não foi possível abrir o navegador.");}});
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
                    emit("connected","Conta de criador autorizada e verificada pelo servidor.");
                else emit("pending","TikTok Shop: "+state+". Sem acesso de afiliado confirmado.");
            }catch(Exception e){emit("error",e.getMessage());}
        });
    }
    @JavascriptInterface public void disconnectTikTok(){
        if(session==null){emit("disconnected","Nenhuma sessão TikTok neste aparelho.");return;}
        worker.execute(()->{
            try {request("/v1/tiktok/disconnect","POST",session);session=null;emit("disconnected","Sessão do Sellutions encerrada. Para revogar acesso no TikTok, use a plataforma oficial.");}catch(Exception e){emit("error","Não foi possível confirmar a desconexão no servidor. Tente novamente.");}
        });
    }
    @JavascriptInterface public void searchTikTok(String query){
        if(session==null){catalog(new JSONObject(),"Conecte o TikTok Shop em Preferências.");return;}
        worker.execute(()->{try{
            JSONObject result=request("/v1/tiktok/search","POST",session,new JSONObject().put("query",query));
            catalog(result,"");
        }catch(Exception e){catalog(new JSONObject(),e.getMessage());}});
    }
    private void catalog(JSONObject result,String error){
        activity.runOnUiThread(()->{if(web!=null)web.evaluateJavascript("onTikTokCatalog("+JSONObject.quote(result.toString())+","+JSONObject.quote(error)+")",null);});
    }
    private JSONObject request(String path,String method,String auth)throws Exception{return request(path,method,auth,new JSONObject());}
    private JSONObject request(String path,String method,String auth,JSONObject data)throws Exception{
        URL u=new URL(backend+path);
        HttpURLConnection c=(HttpURLConnection)u.openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestMethod(method);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");
        if(auth!=null)c.setRequestProperty("Authorization","Bearer "+auth);
        if("POST".equals(method)){
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");
            try(OutputStream o=c.getOutputStream()){o.write(data.toString().getBytes(StandardCharsets.UTF_8));}
        }
        try{
            int status=c.getResponseCode();
            if(status<200||status>=300){if(status==503)throw new Exception("Integração TikTok ainda não liberada.");if(status==403)throw new Exception("Acesso não autorizado ou permissões insuficientes.");if(status==401)throw new Exception("Sessão expirada. Conecte novamente.");if(status==409)throw new Exception("Autorização pendente ou renovação em andamento.");throw new Exception("Serviço indisponível (HTTP "+status+").");}
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
