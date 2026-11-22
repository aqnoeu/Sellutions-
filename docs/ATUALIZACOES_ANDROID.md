# Atualização do Sellutions dentro do próprio aplicativo

## Objetivo
Após a **primeira instalação de um APK de lançamento devidamente assinado**, o usuário abre **Preferências → Atualizações do aplicativo → Verificar atualizações**. O Sellutions consulta a última Release privada, compara a versão, baixa o APK, verifica SHA-256 e identidade do pacote e chama o instalador do Android.

**Não será necessário procurar ou baixar novos APKs manualmente no navegador.** O Android ainda precisa da confirmação do usuário para concluir a instalação. Instalação silenciosa é proibida para apps comuns.

## Situação técnica
O código de atualização foi incorporado ao Android; menu, autorização, download verificado e abertura do instalador foram implementados. **O fluxo completo só estará operacional após configurar a assinatura de lançamento e publicar a primeira Release assinada**, e testá-lo com duas versões reais. Um APK debug assinado pelo GitHub Actions com chave temporária não pode ser atualizado por cima de um APK release assinado com outra chave.

O repositório permanece privado; as Releases também permanecem privadas. Cada aparelho precisa de **acesso somente leitura** configurado uma vez, por meio de token pessoal restrito ao repositório, associado à própria conta GitHub com permissão de leitura. O token é solicitado em diálogo nativo e cifrado por Android Keystore; não é incluído no APK, no HTML, no GitHub ou nos logs. Isso **não cria login Sellutions**.

## Preparação única pelo mantenedor

### 1. Criar e guardar uma chave de assinatura Android
Gerar em ambiente seguro uma chave `JKS` para a versão de lançamento, com senha forte e cópia de segurança protegida. Exemplo em computador com JDK:

```bash
keytool -genkeypair -v -keystore sellutions-release.jks -alias sellutions -keyalg RSA -keysize 3072 -validity 10000
base64 -w 0 sellutions-release.jks
```

No PowerShell, pode-se usar `[Convert]::ToBase64String([IO.File]::ReadAllBytes("sellutions-release.jks"))`.

Guardar o arquivo JKS e as senhas offline. **Não colocar o arquivo nem as senhas no GitHub, no chat ou no código.**

### 2. Configurar Actions secrets
No repositório `aqnoeu/Sellutions-`, acessar Settings → Secrets and variables → Actions e adicionar:

| Secret | Conteúdo |
| --- | --- |
| `SELLUTIONS_KEYSTORE_BASE64` | Conteúdo base64 do JKS de release |
| `SELLUTIONS_KEYSTORE_PASSWORD` | Senha do keystore |
| `SELLUTIONS_KEY_ALIAS` | Nome do alias, ex.: `sellutions` |
| `SELLUTIONS_KEY_PASSWORD` | Senha da chave |

Somente o proprietário precisa fazer essa configuração. **Não alterar a chave entre releases:** Android exige a mesma assinatura para atualizar uma instalação existente.

### 3. Publicar a primeira versão
O workflow `.github/workflows/android-release.yml` é executado ao publicar uma tag `vMAJOR.MINOR.PATCH` no GitHub, como `v0.1.0`. Ele:
1. Calcula `versionCode` a partir da tag;
2. Compila o APK de release;
3. Verifica a assinatura com `apksigner`;
4. Publica a Release privada, contendo `sellutions-release.apk`, SHA-256 e `versionCode=N` nas notas.

**Para evitar perder os dados dos modelos, instale o APK release como instalação inicial.** Quem tiver a versão de teste debug poderá precisar desinstalá-la antes de instalar a primeira release, porque ela foi assinada com outra chave. Faça backup de modelos antes dessa troca.

### 4. Configurar acesso em cada celular uma vez
1. Dar à conta GitHub do usuário acesso de leitura ao repositório privado (ou usar a própria conta que já possui acesso).
2. Criar token fine-grained com **somente Contents: read** para **apenas** `aqnoeu/Sellutions-`. Confirmar se a conta realmente tem acesso à Release privada.
3. No APK release, acessar **Preferências → Autorizar acesso às atualizações** e informar o token no diálogo nativo.
4. Selecionar **Verificar atualizações**. O token fica cifrado no Android Keystore.
5. O token pode ser revogado pelo GitHub e removido no próprio app.

**Atenção:** um token com acesso ao repositório privado também concede acesso de leitura ao código do repositório dentro do escopo concedido. Não compartilhe esse token com alguém que não deva ver o código. Para ampliar a distribuição sem compartilhar acesso ao código, será necessário um repositório separado exclusivamente para distribuição de binários ou um serviço de hospedagem próprio.

### 5. Próximas atualizações
Criar a tag seguinte, por exemplo `v0.1.1`, e aguardar o workflow publicar a Release. O app detectará um `versionCode` maior, baixará o arquivo e solicitará confirmação do Android para atualizar **por cima**, preservando os dados locais.

## Proteções incluídas
- HTTPS; token enviado somente à API `api.github.com`, nunca ao servidor de download após redirecionamento.
- Hash SHA-256 obrigatório da Release e verificação dos bytes baixados.
- Confirmação de `applicationId` e versão superior antes de executar o instalador.
- Download em cache privado, via FileProvider; sem solicitar acesso a todos os arquivos.
- Botões informam status; falhas de rede e acesso não são apresentadas como sucesso.
- Token cifrado com chave no Android Keystore.
- Assinatura da Release fixa, gerenciada pelos secrets de Actions.

## Limitações pendentes de teste real
- Não há nenhuma Release assinada/pública para o atualizador consultar até que os secrets e a primeira tag estejam configurados.
- O caminho Release A → Release B ainda precisa de teste end-to-end em dispositivo.
- O Android pode solicitar autorização de instalação de fontes externas e confirmação em cada atualização.
- Um APK debug produzido pelo workflow padrão usa assinatura temporária por executor e não possui continuidade de atualização.
