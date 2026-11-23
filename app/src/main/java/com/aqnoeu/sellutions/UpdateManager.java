package com.aqnoeu.sellutions;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Atualizador por Releases públicas do GitHub: sem token, sem login e sem servidor.
 * O APK só é aceito se tiver o mesmo pacote, versionCode maior e SHA-256 correto.
 * O Android confirma a instalação; não há instalação silenciosa.
 */
public final class UpdateManager {
    private static final String OWNER = "aqnoeu";
    private static final String REPOSITORY = "Sellutions-";
    private static final String API_URL = "https://api.github.com/repos/" + OWNER + "/" + REPOSITORY + "/releases/latest";
    private static final String KEY_ALIAS = "sellutions_updates_github"; // só para apagar o token antigo
    private static final String PREFS = "sellutions_update_access";
    private static final int CONNECT_TIMEOUT = 20000;
    private static final int READ_TIMEOUT = 30000;
    private final Activity activity;
    private final WebView web;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public UpdateManager(Activity activity, WebView web) {
        this.activity = activity;
        this.web = web;
    }

    /** Verificação manual (botão em Configurações): sempre informa o resultado. */
    public void check() { run(false); }

    /** Verificação ao abrir o app: só aparece algo se houver versão nova. */
    public void checkSilently() { run(true); }

    private void run(boolean silent) {
        if (!silent) status("Verificando atualizações...");
        executor.execute(() -> {
            try {
                removeLegacyToken();
                JSONObject release = requestJson(API_URL);
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                    throw new Exception("Nenhuma versão estável disponível.");
                }
                int latest = parseCode(release.optString("body"));
                int installed = getInstalledCode();
                String version = release.optString("tag_name", "versão desconhecida");
                if (latest <= installed) {
                    if (!silent) status("Sellutions atualizado. Versão instalada: " + getInstalledVersion() + ".");
                    return;
                }
                JSONArray assets = release.getJSONArray("assets");
                JSONObject apk = null;
                for (int i=0;i<assets.length();i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    if ("sellutions-release.apk".equals(asset.optString("name"))) { apk=asset;break; }
                }
                if (apk == null) throw new Exception("A versão não contém o APK de atualização.");
                String digest = apk.optString("digest");
                if (!digest.matches("sha256:[a-fA-F0-9]{64}")) throw new Exception("A versão não contém hash SHA-256 válido.");
                final long assetId = apk.getLong("id");
                final String expectedHash = digest.substring(7).toLowerCase(Locale.ROOT);
                status("Nova versão " + version + " disponível.");
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    new AlertDialog.Builder(activity)
                        .setTitle("Atualização disponível")
                        .setMessage("Versão instalada: " + getInstalledVersion() + "\nNova versão: " + version + "\n\nBaixar e abrir a instalação?")
                        .setPositiveButton("Atualizar", (d,w) -> download(assetId, expectedHash))
                        .setNegativeButton("Depois", null).show();
                });
            } catch(Exception e) {
                if (!silent) status(friendlyError(e));
            }
        });
    }

    /** Apaga o token pessoal salvo por versões anteriores; ele não é mais usado. */
    private void removeLegacyToken() {
        try {
            android.content.SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (prefs.contains("value")) prefs.edit().clear().apply();
            java.security.KeyStore store = java.security.KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {}
    }

    private void download(long assetId, String expectedHash) {
        status("Baixando atualização...");
        executor.execute(() -> {
            File file = new File(activity.getCacheDir(), "sellutions-update.apk");
            try {
                String url = "https://api.github.com/repos/" + OWNER + "/" + REPOSITORY + "/releases/assets/" + assetId;
                HttpURLConnection connection = open(url, "application/octet-stream");
                try {
                    int statusCode = connection.getResponseCode();
                    // GitHub may respond with a time-limited download URL.
                    int redirects=0;
                    while ((statusCode==301 || statusCode==302 || statusCode==303 || statusCode==307 || statusCode==308) && redirects++<5) {
                        String next = connection.getHeaderField("Location");
                        connection.disconnect();
                        if (next==null || !next.startsWith("https://")) throw new Exception("Redirecionamento inválido.");
                        connection = open(next, "application/octet-stream");
                        statusCode=connection.getResponseCode();
                    }
                    if(statusCode!=200) throw new Exception("Falha no download (HTTP " + statusCode + ").");
                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                    long total=0;
                    try(InputStream input=connection.getInputStream(); OutputStream output=new FileOutputStream(file)) {
                        byte[] buffer=new byte[32768];
                        int n;
                        while((n=input.read(buffer))!=-1){
                            output.write(buffer,0,n);
                            sha.update(buffer,0,n);
                            total+=n;
                            if(total>150*1024*1024L) throw new Exception("APK excede o tamanho permitido.");
                        }
                    }
                    StringBuilder hash=new StringBuilder();
                    for(byte b:sha.digest()) hash.append(String.format(Locale.ROOT,"%02x",b&0xff));
                    if(!hash.toString().equals(expectedHash)) throw new Exception("Hash da atualização incorreto; arquivo rejeitado.");
                    PackageInfo packageInfo = activity.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),0);
                    if(packageInfo==null || !activity.getPackageName().equals(packageInfo.packageName))
                        throw new Exception("O APK não pertence ao Sellutions.");
                    long downloadedVersion = Build.VERSION.SDK_INT>=28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
                    if(downloadedVersion<=getInstalledCode()) throw new Exception("O APK não é mais recente que a versão instalada.");
                    status("Download verificado. Abra a instalação no Android.");
                    install(file);
                } finally {connection.disconnect();}
            } catch(Exception e) {
                file.delete();
                status(friendlyError(e));
            }
        });
    }

    private void install(File file) {
        activity.runOnUiThread(() -> {
            try {
                if (Build.VERSION.SDK_INT>=26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                    status("Autorize a instalação de apps pelo Sellutions nas configurações Android; depois verifique a atualização novamente.");
                    Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+activity.getPackageName()));
                    activity.startActivity(settings);
                    return;
                }
                Uri uri=FileProvider.getUriForFile(activity, activity.getPackageName()+".fileprovider",file);
                Intent intent=new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri,"application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch(Exception e) { status("Não foi possível abrir o instalador Android."); }
        });
    }

    private HttpURLConnection open(String address, String accept) throws Exception {
        URL target=new URL(address);
        if(!"https".equalsIgnoreCase(target.getProtocol())) throw new Exception("Conexão não segura.");
        HttpURLConnection con=(HttpURLConnection)target.openConnection();
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Accept",accept);
        con.setRequestProperty("User-Agent","Sellutions-Android-Updater");
        con.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
        return con;
    }

    private JSONObject requestJson(String url) throws Exception {
        HttpURLConnection conn=open(url,"application/vnd.github+json");
        try {
            int code=conn.getResponseCode();
            if(code==403 || code==429) throw new Exception("O GitHub limitou as consultas. Tente novamente em alguns minutos.");
            if(code==404) throw new Exception("Nenhuma versão pública encontrada.");
            if(code!=200) throw new Exception("Falha ao consultar versões (HTTP "+code+").");
            try(InputStream input=conn.getInputStream()) {
                byte[] data=readLimited(input,1024*1024);
                return new JSONObject(new String(data,StandardCharsets.UTF_8));
            }
        } finally {conn.disconnect();}
    }

    private byte[] readLimited(InputStream in,int limit)throws Exception {
        java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
        byte[] b=new byte[8192]; int n; int total=0;
        while((n=in.read(b))!=-1) {total+=n; if(total>limit)throw new Exception("Resposta muito grande."); buf.write(b,0,n);}
        return buf.toByteArray();
    }

    private int parseCode(String body)throws Exception {
        Matcher m=Pattern.compile("(?m)^versionCode\\s*=\\s*(\\d+)\\s*$").matcher(body);
        if(!m.find())throw new Exception("A versão não informa versionCode.");
        return Integer.parseInt(m.group(1));
    }
    private int getInstalledCode()throws Exception {
        PackageInfo p=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
        return (int)(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode);
    }
    private String getInstalledVersion() {
        try {return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}
        catch(Exception e) {return "desconhecida";}
    }
    private String friendlyError(Exception e) {
        String message=e.getMessage();
        if(message==null || message.trim().isEmpty())return "Não foi possível verificar atualizações. Confira a conexão.";
        return message;
    }
    private void status(String message) {
        activity.runOnUiThread(()->{
            if(web!=null)web.evaluateJavascript("window.sellutionsUpdateStatus("+JSONObject.quote(message)+")",null);
            else Toast.makeText(activity,message,Toast.LENGTH_LONG).show();
        });
    }

    public void close() {executor.shutdown();}
}
