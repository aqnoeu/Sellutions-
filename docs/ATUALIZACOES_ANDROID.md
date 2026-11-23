# Sellutions — Atualização dentro do APK (piloto privado)

## Estado operacional
A infraestrutura de publicação foi ativada em 23/11/2026:
- `v0.1.0`: primeiro APK de release com assinatura persistente e SHA-256;
- `v0.1.1`: segundo APK de release com **mesma assinatura** e `versionCode` superior; a publicação verifica ambos antes de disponibilizar a atualização.
- Workflow: `.github/workflows/android-release.yml`.
- Artefatos da Release: `sellutions-release.apk` e `sellutions-release.apk.sha256`.
- Repositório **privado**; as Releases são acessíveis apenas a pessoas autorizadas.

## Como instalar
Abrir https://github.com/aqnoeu/Sellutions-/releases/latest com uma conta que tenha permissão ao repositório e baixar `sellutions-release.apk` uma única vez para fazer a instalação inicial.

**Atenção:** uma versão debug assinada com outra chave não aceita instalação por cima; caso tenha o debug instalado, será necessário migrar os dados desejados e instalar a primeira release separadamente.

## Como autorizar as próximas atualizações (uma vez)
O APK consulta Releases privadas sem tornar o código-fonte público. No dispositivo:
1. Criar, na própria conta GitHub autorizada a ler `aqnoeu/Sellutions-`, um token fine-grained de leitura para esse único repositório, com `Contents: read`. Não utilizar senha de acesso ao GitHub.
2. Abrir **Configurações → Atualizações do aplicativo → Autorizar acesso às atualizações**.
3. Colar o token no diálogo. Ele será guardado cifrado no Android Keystore.
4. Tocar **Verificar atualizações**. Uma versão posterior será baixada e verificada pelo próprio aplicativo, que abrirá o instalador do Android.
5. Confirmar a instalação do Android; a atualização será instalada sobre a anterior (mesmo pacote e assinatura).

O usuário não precisa baixar os próximos APKs manualmente. O Android pode solicitar permissão para instalar APKs de origem externa e confirmação em cada atualização.

## Publicação automatizada
Alterações em `app/**`, `assets/brand/**`, `scripts/check-ui.cjs` ou no workflow de release acionam a compilação; o próximo `v0.1.N` é escolhido automaticamente e a Release privada publica o APK e SHA-256. A chave é sempre a mesma.

## Assinatura do piloto: armazenamento temporário
Para destravar o piloto sem cadastro administrativo manual de Secrets, o workflow gerou uma chave **uma única vez** e criou um backup numa **Release privada e em rascunho**, `signing-vault`, com:
- `android-signing.jks`;
- `android-signing.pass`.

Em execuções seguintes, o workflow recupera ambos usando o `GITHUB_TOKEN` do próprio repositório. **Essa opção funciona para o piloto, mas não tem a segurança ideal:** qualquer pessoa com permissão suficiente para acessar os assets da Release de rascunho pode obter a chave de assinatura. Não publicar a Release de rascunho, não divulgar os arquivos, não excluir o backup sem migração planejada.

Para aumentar a segurança no futuro, mover **exatamente a mesma chave** e sua senha para `SELLUTIONS_KEYSTORE_BASE64`, `SELLUTIONS_KEYSTORE_PASSWORD`, `SELLUTIONS_KEY_ALIAS` e `SELLUTIONS_KEY_PASSWORD` em GitHub Actions Secrets e atualizar o workflow para usá-los. **Não gerar outra chave**, ou Android poderá exigir reinstalação.

## Verificações de segurança do APK
- Mesmo certificado SHA-256 da versão anterior, verificado durante o build;
- `versionCode` Android crescente, verificado no build;
- `SHA-256` do arquivo publicado e exigido pelo atualizador;
- `applicationId` idêntico e `versionCode` maior verificados pelo APK antes de abrir o instalador;
- Download HTTPS autenticado somente para `api.github.com`, sem transmitir token ao CDN após redirecionamento;
- Instalação oficial do Android, sempre sujeita à confirmação do usuário.

## Limites
A instalação da Release privada inicial exige login GitHub no navegador. O atualizador do APK exige autorização de leitura da Release privada uma vez no dispositivo. A confirmação de instalação é exigência do Android.


## Teste de atualização efetivamente executado
Em 23/11/2026, o workflow [45087589108](https://github.com/aqnoeu/Sellutions-/actions/runs/45087589108) terminou **com sucesso** em um emulador Android API 35:
1. APK `v0.1.0` instalado: `adb install` retornou `Success`;
2. APK `v0.1.1` instalado **por cima**, sem desinstalação: `adb install -r` retornou `Success`;
3. `PackageManager` confirmou `versionCode=1001`;
4. Certificado e SHA-256 foram verificados durante a publicação assinada.

Esse teste verifica a continuidade da instalação Android entre duas Releases reais. **Não substitui** o teste de autorização pessoal e download privado dentro do aplicativo em um celular físico, que exige um token de leitura configurado pelo usuário.

APK da versão assinada atual: https://github.com/aqnoeu/Sellutions-/releases/download/v0.1.1/sellutions-release.apk
