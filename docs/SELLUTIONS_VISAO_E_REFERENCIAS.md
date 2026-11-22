# Sellutions — visão do produto e referências técnicas

> Especificação-base consolidada da conversa “Analisar vídeo” (22/11/2026), incluindo a confirmação posterior de **TikTok Shop**. Este documento é uma orientação de implementação, não um script executável.

## 1. Objetivo
Criar o **Sellutions**, aplicativo próprio para facilitar a descoberta, captura e preparação de ofertas de produtos para publicação no WhatsApp e, futuramente, em outras redes sociais. Fluxo desejado: **encontrar → selecionar/capturar → preparar → anunciar**. Deve ser simples, rápido e sem configurações complexas na tela principal.

### Marketplaces iniciais
- **Shopee, Amazon, Mercado Livre, SHEIN e TikTok Shop**.
- **Temu fica fora da versão inicial**.
- Conectores individuais devem respeitar as APIs, permissões e regras de cada plataforma.

## 2. Jornada principal
1. O usuário descobre um produto em catálogo interno, aplicativo ou website.
2. Importa por link, compartilhamento ou captura de tela/imagem; considerar também **botão flutuante** no Android, com permissão e controle explícitos.
3. Sellutions identifica o produto e tenta extrair foto, título, preço, desconto, loja e URL; dados não verificáveis devem ser confirmados, nunca inventados.
4. Quando viável e autorizado, gera ou associa o **link de afiliado** da conta do usuário.
5. Gera uma **mensagem de oferta com IA**, editável e com visualização prévia.
6. Usuário toca **Anunciar**, escolhe **um ou mais grupos do WhatsApp** e conclui por um método viável e compatível com as regras da plataforma.
7. Registra histórico, erros por destino e possibilidade de retomar a publicação.

### Recursos complementares
- Busca, filtros, categorias e seleção de produtos;
- Modelos de texto, marcação de produtos enviados e prevenção de duplicidades;
- Agendamentos e automações em área separada da jornada principal;
- Administração de grupos, lojas, contas e permissões;
- Relatórios e histórico de publicações.

**Limite técnico:** não pressupor que enviar automaticamente a grupos de WhatsApp seja oficialmente permitido. Verificar APIs disponíveis, políticas, segurança e risco de bloqueio. O protótipo anterior simulava algumas funções, não comprova integrações reais.

## 3. Marca e interface
- **Cores:** laranja e branco no modo claro; preto no modo escuro, mantendo acentos da marca.
- **Logotipo:** cifrão ($) estilizado, em versão isolada e ícone completo com fundo gradiente.
- **Design:** leve, moderno, minimalista e intuitivo; evidenciar cards de produtos e botão **Anunciar**.
- Reduzir cliques e telas; modelos, configurações e automações devem ficar discretos, sem competir com a ação principal.
- SVGs aprovados no repositório são fonte de verdade; não reconstruir a marca a partir de descrições.

## 4. Repositórios discutidos na conversa como inspiração

| Repositório | URL | O que examinar |
| --- | --- | --- |
| Shopee BR Affiliate MCP | https://github.com/jeffmonteiroo/shopee-br-affiliate-mcp | API de afiliados Shopee, consulta de produtos e links |
| Sistema de Achadinhos | https://github.com/goul4rt/sistema-de-achadinhos | Busca, garimpo, filtros, ofertas |
| bot-achadinhos | https://github.com/vinniciusnascimento/bot-achadinhos | Montagem de mensagens com IA e publicação (Telegram como referência, não integração pronta com WhatsApp) |
| ScribbleGrab | https://github.com/bakermhajna/ScribbleGrab | Captura/seleção na experiência Android; verificar aderência |
| Acha-Tech | https://github.com/andrefeeelipe/Acha-Tech | Painel de ofertas, publicação, agendamento e afiliados |
| Evolution API | https://github.com/EvolutionAPI/evolution-api | Investigar integração WhatsApp e respectivos riscos/limites |
| WPPConnect | https://github.com/wppconnect-team/wppconnect | Recursos de mensagens e grupos e limitações de API não oficial |

### Referências adicionais citadas, sem URLs exatos recuperados
- `loja-integrada` — abordagem de várias lojas;
- `multi-marketplace` — catálogo multiloja;
- `ofertas-grupos` — ofertas e grupos;
- `amazon-price-tracker` — acompanhamento de preços Amazon;
- Uma terceira API HTTP de WhatsApp, sem nome preservado.

**Não associar URLs inventados a essas referências.** Pesquisar sua autoria e confirmar o repositório correto antes de incorporá-las ao código.

### Critério de reaproveitamento
Cada repositório é **referência de estudo**, não dependência aprovada. Antes de reutilizar código, verificar existência, licença, atualização, arquitetura, segurança, permissões e compatibilidade com os termos das plataformas. Evitar copiar telas/identidade de terceiros.

## 5. Diretrizes de implementação
1. O código e a marca pertencem ao projeto principal **privado** `aqnoeu/Sellutions-`.
2. Separar módulos: captura, conectores de lojas, catálogo, afiliados, composição de oferta/IA, publicação, grupos, histórico e configurações.
3. Guardar chaves e tokens de integração apenas em ambiente seguro; nunca no repositório ou app cliente.
4. Manter captura e publicação sob controle consciente do usuário; permissões apenas quando necessárias.
5. Primeiro validar uma jornada de ponta a ponta com dados reais; ampliar lojas e automações progressivamente.
6. Testar acessibilidade, tema claro/escuro, telas pequenas e falhas de conectividade.
7. Não confundir protótipo/mockup com recurso entregue; documentar claramente o que está implementado, simulado ou pendente.

## 6. Critérios de aceitação do MVP
- O usuário consegue importar um produto por link e revisar seus dados;
- O aplicativo prepara e permite editar a mensagem;
- O usuário consegue selecionar o destino e concluir o compartilhamento pelo método efetivamente suportado;
- A interface preserva identidade Sellutions e é responsiva;
- Há feedback verdadeiro de sucesso ou erro, sem afirmar envio não confirmado;
- A arquitetura prevê os cinco marketplaces iniciais, mesmo que cada integração precise de credenciais e homologações próprias.

> **Próximo passo técnico:** auditar referências e definir qual integração será implementada primeiro. Não implantar automação WhatsApp não oficial sem análise específica.
