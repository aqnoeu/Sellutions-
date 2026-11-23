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
import android.util.Base64;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;
import android.text.InputType;
import androidx.core.content.FileProvider;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Atualizador para Releases privadas do repositório de distribuição.
 * Sem chaves dentro do APK: cada instalação autoriza o acesso uma vez.
 * O Android confirma a instalação; não há instalação silenciosa.
 */
public final class UpdateManager {
    private static final String OWNER = "aqnoeu";
    private static final String REPOSITORY = "Sellutions-";
    private static final String API_URL = "https://api.github.com/repos/" + OWNER + "/" + REPOSITORY + "/releases/latest";
    private static final String KEY_ALIAS = "sellutions_updates_github";
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

    public void showCredentialsDialog() {
        activity.runOnUiThread(() -> {
            EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setHint("github_pat_...");
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            int p = (int) (22 * activity.getResources().getDisplayMetrics().density);
            android.widget.FrameLayout wrapper = new android.widget.FrameLayout(activity);
            android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(-1,-2);
            lp.setMargins(p, p/2, p, p/2);
            wrapper.addView(input, lp);
            new AlertDialog.Builder(activity)
                .setTitle("Autorizar atualizações")
                .setMessage("O Sellutions precisa de acesso de leitura ao repositório privado para consultar e baixar atualizações. Cole um token fine-grained da sua conta GitHub com permissão Contents: read somente para aqnoeu/Sellutions-. A configuração é feita uma única vez.")
                .setView(wrapper)
                .setPositiveButton("Salvar acesso", (d,w) -> {
                    String token = input.getText().toString().trim();
                    if (token.isEmpty()) { status("Informe um token para acessar as versões privadas."); return; }
                    try {
                        saveToken(token);
                        status("Acesso salvo. Verificando atualizações...");
                        check();
                    }
                    catch (Exception e) { status("Não foi possível proteger o token no dispositivo."); }
                })
                .setNeutralButton("Remover acesso", (d,w) -> {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
                    status("Acesso removido deste aparelho.");
                })
                .setNegativeButton("Cancelar", null).show();
        });
    }

    public void check() {
        status("Verificando atualizações...");
        executor.execute(() -> {
            try {
                String token = readToken();
                if (token == null || token.isEmpty()) {
                    status("Primeiro acesso: autorize a consulta às versões privadas.");
                    showCredentialsDialog();
                    return;
                }
                JSONObject release = requestJson(API_URL, token);
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                    throw new Exception("Nenhuma versão estável disponível.");
                }
                int latest = parseCode(release.optString("body"));
                int installed = getInstalledCode();
                String version = release.optString("tag_name", "versão desconhecida");
                if (latest <= installed) {
                    status("Sellutions atualizado. Versão instalada: " + getInstalledVersion() + ".");
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
                long assetId = apk.getLong("id");
                String expectedHash = digest.substring(7).toLowerCase(Locale.ROOT);
                status("Nova versão " + version + " disponível.");
                final long finalId = assetId;
                activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                    .setTitle("Atualização disponível")
                    .setMessage("Versão instalada: " + getInstalledVersion() + "\nNova versão: " + version + "\n\nBaixar e abrir a instalação?")
                    .setPositiveButton("Atualizar", (d,w) -> download(finalId,expectedHash, token))
                    .setNegativeButton("Depois", null).show());
            } catch(Exception e) { status(friendlyError(e)); }
        });
    }

    private void download(long assetId, String digest, String token) {
        status("Baixando atualização...");
        executor.execute(() -> {
            File file = new File(activity.getCacheDir(), "sellutions-update.apk");
            try {
                String url = "https://api.github.com/repos/" + OWNER + "/" + REPOSITORY + "/releases/assets/" + assetId;
                HttpURLConnection connection = open(url, token, "application/octet-stream");
                try {
                    int statusCode = connection.getResponseCode();
                    // GitHub may respond with a time-limited download URL.
                    int redirects=0;
                    while ((statusCode==301 || statusCode==302 || statusCode==303 || statusCode==307 || statusCode==308) && redirects++<5) {
                        String next = connection.getHeaderField("Location");
                        connection.disconnect();
                        if (next==null || !next.startsWith("https://")) throw new Exception("Redirecionamento inválido.");
                        // Never forward the GitHub token to storage/CDN domains.
                        connection = open(next, null, "application/octet-stream");
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
                    if(!hash.toString().equals(digest)) throw new Exception("Hash da atualização incorreto; arquivo rejeitado.");
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

    private HttpURLConnection open(String address, String token, String accept) throws Exception {
        URL target=new URL(address);
        if(!"https".equalsIgnoreCase(target.getProtocol())) throw new Exception("Conexão não segura.");
        HttpURLConnection con=(HttpURLConnection)target.openConnection();
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Accept",accept);
        con.setRequestProperty("User-Agent","Sellutions-Android-Updater");
        con.setRequestProperty("X-GitHub-Api-Version","2022-11-28");
        if(token!=null) {
            if(!"api.github.com".equalsIgnoreCase(target.getHost()))
                throw new Exception("Destino inválido para autenticação.");
            con.setRequestProperty("Authorization","Bearer "+token);
        }
        return con;
    }

    private JSONObject requestJson(String url,String token) throws Exception {
        HttpURLConnection conn=open(url,token,"application/vnd.github+json");
        try {
            int code=conn.getResponseCode();
            if(code==401 || code==403) throw new Exception("Acesso inválido ou sem permissão para o repositório.");
            if(code==404) throw new Exception("Nenhuma versão publicada ou token sem acesso.");
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

    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if(!store.containsAlias(KEY_ALIAS)){
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            gen.generateKey();
        }
        return (SecretKey)store.getKey(KEY_ALIAS,null);
    }
    private void saveToken(String token) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] ciphertext=cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("value",Base64.encodeToString(ciphertext,Base64.NO_WRAP)).apply();
    }
    private String readToken() throws Exception {
        android.content.SharedPreferences prefs=activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String value=prefs.getString("value",null);
        String iv=prefs.getString("iv",null);
        if(value==null || iv==null)return null;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(value,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }

    public void close() {executor.shutdown();}
}
