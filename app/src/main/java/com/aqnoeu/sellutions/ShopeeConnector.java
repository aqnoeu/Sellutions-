package com.aqnoeu.sellutions;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Code-free catalog connection; credentials remain exclusively on the backend. */
public final class ShopeeConnector {
    private static final String BASE="https://sellutions.vercel.app/api/shopee/device?action=";
    private final Activity activity;
    private final WebView web;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean connected;
    private volatile boolean closed;
    private volatile int generation;
    ShopeeConnector(Activity activity, WebView web){this.activity=activity;this.web=web;}

    @JavascriptInterface public void connect(){
        if(closed)return;
        final int g=++generation;connected=false;state("connecting","Verificando a Shopee…");
        worker.execute(()->{
            try{
                JSONObject result=request("status",new JSONObject());
                if(!"connected".equals(result.optString("status")))throw new Exception("Conexão não confirmada.");
                if(g==generation&&!closed){connected=true;state("connected","Shopee conectada. Você já pode pesquisar produtos.");}
            }catch(Exception e){if(g==generation&&!closed)state("error",message(e));}
        });
    }
    @JavascriptInterface public void disconnect(){
        generation++;connected=false;state("disconnected","Acesso encerrado neste aparelho.");
    }
    @JavascriptInterface public void search(String keyword,int page,int id){
        final int g=generation;
        if(!connected){result("onShopeeCatalog",id,new JSONObject(),"Conecte a Shopee primeiro.");return;}
        if(keyword==null||keyword.trim().isEmpty()||keyword.length()>100||page<1||page>100){result("onShopeeCatalog",id,new JSONObject(),"Pesquisa inválida.");return;}
        worker.execute(()->{
            try{JSONObject r=request("search",new JSONObject().put("keyword",keyword.trim()).put("page",page).put("limit",10));
                if(g==generation&&!closed)result("onShopeeCatalog",id,r,"");
            }catch(Exception e){if(g==generation&&!closed){expire(e);result("onShopeeCatalog",id,new JSONObject(),message(e));}}
        });
    }
    @JavascriptInterface public void generateLink(String url,int id){
        final int g=generation;
        if(!connected){result("onShopeeLink",id,new JSONObject(),"Conecte a Shopee primeiro.");return;}
        if(!validLink(url)){result("onShopeeLink",id,new JSONObject(),"Informe um link HTTPS válido da Shopee.");return;}
        worker.execute(()->{
            try{JSONObject r=request("link",new JSONObject().put("url",url));
                if(!validLink(r.optString("affiliate_url")))throw new Exception("Link de afiliado inválido.");
                if(g==generation&&!closed)result("onShopeeLink",id,r,"");
            }catch(Exception e){if(g==generation&&!closed){expire(e);result("onShopeeLink",id,new JSONObject(),message(e));}}
        });
    }
    @JavascriptInterface public void copyLink(String link){
        if(!validLink(link))return;
        activity.runOnUiThread(()->{
            if(closed)return;
            ClipboardManager cm=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Link Shopee",link));
        });
    }
    private static boolean validLink(String s){
        try{if(s==null||s.length()>2048)return false;URL u=new URL(s);String h=u.getHost().toLowerCase(java.util.Locale.ROOT);
            return "https".equals(u.getProtocol())&&u.getUserInfo()==null&&u.getPort()==-1&&(h.equals("shopee.com.br")||h.endsWith(".shopee.com.br")||h.equals("shope.ee"));
        }catch(Exception e){return false;}
    }
    private static final class HttpError extends Exception {
        final int status;HttpError(int status){this.status=status;}
    }
    private void expire(Exception e){if(e instanceof HttpError&&((HttpError)e).status==401){connected=false;state("disconnected","Conexão encerrada. Conecte novamente.");}}
    private static String message(Exception e){
        if(e instanceof HttpError){switch(((HttpError)e).status){
            case 400:return "Dados inválidos. Confira a pesquisa ou o link.";
            case 401:return "Conexão não autorizada pelo servidor.";
            case 403:return "A Shopee recusou a autorização da API.";
            case 429:return "Muitas consultas. Aguarde alguns segundos e tente novamente.";
            case 503:return "Conector Shopee temporariamente indisponível.";
            default:return "Não foi possível consultar a Shopee. Tente novamente.";
        }}
        return "Não foi possível confirmar a resposta. Verifique sua conexão e tente novamente.";
    }
    private JSONObject request(String action,JSONObject data)throws Exception{
        try{return requestOnce(action,data);}catch(HttpError e){if(e.status!=429)throw e;Thread.sleep(3200);return requestOnce(action,data);}
    }
    private JSONObject requestOnce(String action,JSONObject data)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(BASE+action).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(25000);c.setRequestMethod("POST");c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);
        try{
            try(OutputStream o=c.getOutputStream()){o.write(data.toString().getBytes(StandardCharsets.UTF_8));}
            int status=c.getResponseCode();if(status<200||status>=300)throw new HttpError(status);
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>262144)throw new Exception("Resposta muito grande");out.write(buf,0,n);}
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{c.disconnect();}
    }
    private void state(String status,String text){eval("onShopeeConnection("+JSONObject.quote(status)+","+JSONObject.quote(text)+")");}
    private void result(String fn,int id,JSONObject data,String error){eval(fn+"("+id+","+JSONObject.quote(data.toString())+","+JSONObject.quote(error)+")");}
    private void eval(String js){activity.runOnUiThread(()->{if(!closed&&!activity.isFinishing())web.evaluateJavascript(js,null);});}
    public void close(){closed=true;generation++;connected=false;worker.shutdownNow();}
}
