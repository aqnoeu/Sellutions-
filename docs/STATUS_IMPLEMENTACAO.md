# Sellutions — implementação técnica real

## Entregue em código-fonte (versão 0.1.0-dev)
- Estrutura mínima de aplicativo Android nativo (Java, Gradle, Android SDK).
- Tela inicial, pesquisa por loja (Shopee e TikTok Shop), status das conexões e área de grupos.
- Edição e persistência de **um modelo de mensagem** no armazenamento local do Android; variáveis `{produto}`, `{preco}`, `{loja}`, `{link}`.
- Sem login e sem servidor proprietário.
- Integrações não disponíveis são rotuladas como pendentes — **sem resultados de busca fictícios**.

## Ainda não entregue
- APK compilado, instalação/teste em dispositivo e pipeline de builds.
- Pesquisa real Shopee/TikTok Shop: falta acesso autorizado às APIs.
- IA Groq: falta integração e método apropriado de armazenamento da chave.
- Conexão WhatsApp, criação/administração de grupos e envio direto: falta validar solução Android local e compatibilidade com políticas.
- A aplicação dos **dois SVGs aprovados**: é necessário localizar os caminhos exatos dos arquivos no repositório e integrá-los sem redesenho.
- Pesquisa externa, ícone flutuante, captura, históricos, automações e demais lojas.

## Acesso às APIs
- Shopee: https://affiliate.shopee.com.br/open_api — obter App ID e Secret, solicitar habilitação caso não apareçam.
- TikTok Shop Partner Center: https://partner.tiktokshop.com/ — registrar app apropriado para afiliados/criadores e solicitar os escopos/autorizações específicos.
- **Não publicar credenciais no GitHub nem em mensagens.** Uma conta de afiliado ativa não garante autorização da API.

## Limitações de arquitetura
- O projeto é **local-first**, mas consultas Shopee/TikTok Shop e geração Groq requerem rede e serviços das respectivas empresas. Não ter backend próprio não é o mesmo que funcionar offline.
- Os repositórios Evolution API e WPPConnect são implementações orientadas a serviços Node.js/WhatsApp Web, não bibliotecas Android nativas plug-and-play.
- Até validar publicações diretas, manter o envio como requisito não implementado. Não indicar sucesso sem comprovante.

## Próximo ciclo
1. Confirmar disponibilidade e localização dos SVGs oficiais no GitHub.
2. Definir estratégia de integração Android local e proteger credenciais.
3. Habilitar APIs das duas lojas; adicionar cliente real e busca.
4. Implementar modelo de Groq no fluxo de edição.
5. Testar conexão WhatsApp de maneira controlada e identificar capacidades efetivamente disponíveis.

## Decisão de integrações — 22/11/2026
- **Groq:** uma API **geral, gerenciada pelo Sellutions**, compartilhada entre usuários. Os usuários não precisam fornecer chave Groq. A conexão efetiva depende de configurar uma chave válida e de escolher infraestrutura para protegê-la; **não inserir a chave no APK**, mesmo em aplicativo de uso pessoal, se será distribuído.
- **Shopee:** cada usuário associa sua **própria conta de afiliado**, com autorização ou credenciais individuais conforme o fluxo permitido pela Shopee; não usar os links de afiliado do desenvolvedor para terceiros.
- **TikTok Shop:** cada usuário associa sua **própria conta de afiliado**, conforme acesso e escopos concedidos pela plataforma.
- **WhatsApp:** cada usuário conecta a própria conta, sujeito à viabilidade de integração local Android e às políticas da plataforma.
- **App de teste:** Android, sem login Sellutions e com prioridade de busca interna.
- **Ponto em aberto:** o requisito de uma chave Groq geral segura é incompatível com distribuir essa chave embutida em um APK sem backend/proxy confiável; definir arquitetura que a mantenha protegida antes de disponibilizar a terceiros.
- **Status:** esta é uma decisão de arquitetura, não uma conexão de API já estabelecida.

## Ajuste do piloto fechado — 22/11/2026
- Distribuição inicial somente a **1 ou 2 pessoas**, via APK de teste, com repositório GitHub privado; **sem login Sellutions, servidor próprio, Cloudflare Workers ou publicação na Play Store nesta etapa**.
- **Groq:** mesma credencial geral pode ser usada nas duas instalações, mas deve ser **inserida localmente após instalação**, nunca embutida no APK, no código-fonte, nos logs ou na configuração pública. Guardar com Android Keystore/armazenamento cifrado quando implementado. Assim não há necessidade de proxy neste piloto; reconhecer exposição residual da chave no dispositivo e possibilidade de revogação/limites de uso.
- **Shopee/TikTok Shop:** credenciais e autorização individuais por instalação, condicionadas a acesso efetivo às APIs.
- **WhatsApp:** conta individual e integração local a validar; o piloto não elimina riscos de bloqueio nem limitações de APIs não oficiais.
- Em eventual expansão além do piloto, reavaliar backend seguro para a chave geral Groq, controle de uso, autenticação e revogação.
- **Esta decisão atualiza o planejamento; nenhuma API foi conectada por esta alteração.**

## Implementação baseada no mockup v3 — 22/11/2026
- **Fonte visual:** arquivo enviado pelo usuário `sellutions_mockup_v3.html`.
- **Interface Android implementada no código:** `app/src/main/assets/index.html`, baseada nas regras de estilo do mockup, com navegação inferior Início/Produtos/Ofertas/Grupos, tela de Preferências, painel principal, busca por loja e editor de modelos.
- **Shell Android:** `MainActivity.java` abre a interface HTML local em WebView com DOM storage e sem servidor ou login.
- **Dados do teste:** removidos os produtos fictícios e grupos predefinidos que constavam no mockup. A pesquisa avisa explicitamente quando uma API ainda não está conectada.
- **Funcionalidade local no código:** criar, editar, escolher e excluir até 15 modelos de texto; criar e remover referências locais de grupos; persistir preferências via armazenamento do WebView. Esses recursos ainda precisam de teste em dispositivo.
- **Build:** fluxo GitHub Actions `.github/workflows/android-build.yml` adicionado para produzir APK debug como artifact; o resultado da compilação e teste em aparelho **ainda não foi confirmado**.
- **Identidade vetorial:** preservado o estilo e cifrão provisório do mockup. A substituição pelo **SVG original do repositório permanece pendente**, porque os caminhos `logo.svg`, `icone.svg`, `assets/logo.svg`, `assets/icon.svg` não foram encontrados nas consultas; não recriar as artes.
- **Permanecem pendentes:** uso da API real Shopee/TikTok Shop, geração Groq, armazenamento seguro de chaves, WhatsApp, captura flutuante e entrega de APK validado.
- **Critério para conclusão da Fase 1:** compilação bem-sucedida, instalação e teste de navegação/modelos, e aplicação dos SVGs originais.
