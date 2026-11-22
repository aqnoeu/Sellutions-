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
