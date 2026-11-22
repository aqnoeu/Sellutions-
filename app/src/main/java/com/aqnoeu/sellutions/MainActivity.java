package com.aqnoeu.sellutions;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int ORANGE = Color.rgb(246, 107, 30);
    private static final String PREFS = "sellutions_local_settings";
    private LinearLayout body;
    private EditText templateInput;
    private String selectedStore = "Shopee";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        home();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView text(String message, int size, boolean bold) {
        TextView label = new TextView(this);
        label.setText(message);
        label.setTextColor(Color.rgb(32, 32, 32));
        label.setTextSize(size);
        if (bold) label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, dp(7), 0, dp(7));
        return label;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(ORANGE);
        background.setCornerRadius(dp(14));
        b.setBackground(background);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52));
        p.setMargins(0, dp(8), 0, dp(8));
        b.setLayoutParams(p);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void page(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        body = new LinearLayout(this);
        body.setPadding(dp(20), dp(24), dp(20), dp(24));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        setContentView(scroll);
        TextView heading = text(title, 27, true);
        body.addView(heading);
    }

    private void note(String message) {
        TextView info = text(message, 15, false);
        info.setTextColor(Color.DKGRAY);
        body.addView(info);
    }

    private void home() {
        page("Sellutions");
        note("Versão 0.1 — estrutura Android local de teste. Nenhuma loja, IA ou sessão WhatsApp está conectada ainda.");
        body.addView(button("Pesquisar produtos", this::products));
        body.addView(button("Meus modelos de publicação", this::templates));
        body.addView(button("WhatsApp e grupos", this::groups));
        body.addView(button("Status das conexões", this::connections));
    }

    private void products() {
        page("Pesquisar produtos");
        note("Escolha a loja. As consultas reais serão liberadas quando a API da sua conta estiver autorizada.");
        body.addView(button("Shopee", () -> { selectedStore = "Shopee"; productSearch(); }));
        body.addView(button("TikTok Shop", () -> { selectedStore = "TikTok Shop"; productSearch(); }));
        body.addView(button("Voltar", this::home));
    }

    private void productSearch() {
        page(selectedStore);
        EditText query = new EditText(this);
        query.setHint("Nome do produto");
        query.setSingleLine(true);
        body.addView(query);
        note("A busca online ainda não foi implementada. Não serão mostradas ofertas inventadas.");
        body.addView(button("Pesquisar", () -> Toast.makeText(this,
                "Aguardando habilitação da API " + selectedStore, Toast.LENGTH_LONG).show()));
        body.addView(button("Voltar", this::products));
    }

    private void templates() {
        page("Modelos de publicação");
        note("O texto é salvo somente neste aparelho. Use {produto}, {preco}, {loja} e {link} como variáveis. A IA Groq será integrada depois.");
        templateInput = new EditText(this);
        templateInput.setMinLines(7);
        templateInput.setGravity(Gravity.TOP);
        templateInput.setText(getPreferences().getString("template", "🔥 {produto}\n💰 {preco}\n🛍️ {loja}\n🔗 {link}"));
        body.addView(templateInput);
        body.addView(button("Salvar modelo", () -> {
            getPreferences().edit().putString("template", templateInput.getText().toString()).apply();
            Toast.makeText(this, "Modelo salvo localmente", Toast.LENGTH_SHORT).show();
        }));
        body.addView(button("Voltar", this::home));
    }

    private void groups() {
        page("WhatsApp e grupos");
        note("Publicação automática, listagem e administração de grupos ainda não foram implementadas. Precisamos validar uma solução que funcione inteiramente no Android e os riscos de conta.");
        note("Não é possível afirmar que um anúncio foi enviado sem confirmação real.");
        body.addView(button("Voltar", this::home));
    }

    private void connections() {
        page("Status das conexões");
        note("Shopee: credenciais API pendentes");
        note("TikTok Shop: autorização de app/escopos pendente");
        note("Groq: integração pendente; nenhum segredo é salvo neste protótipo");
        note("WhatsApp: integração Android local pendente de validação");
        body.addView(button("Voltar", this::home));
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override public void onBackPressed() {
        home();
    }
}
