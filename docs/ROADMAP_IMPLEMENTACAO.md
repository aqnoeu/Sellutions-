# Sellutions — roteiro de implementação por fases

**Versão do planejamento:** 1.0 — 22/11/2026  
**Status:** planejamento, não implantação  
**Repositório:** privado `aqnoeu/Sellutions-`  
**Documento complementar:** [Visão e referências](SELLUTIONS_VISAO_E_REFERENCIAS.md)

## Princípios
- Construir o **aplicativo base antes das automações**.
- Priorizar **Shopee e TikTok Shop** entre as lojas; posteriormente Amazon, Mercado Livre e SHEIN. **Temu não integra o lançamento inicial**.
- Integrar **Groq para redigir e adaptar mensagens de publicação**, sem inventar preço, descontos ou outras propriedades.
- Permitir **criar, administrar e publicar em grupos de WhatsApp** conforme a viabilidade real da integração, permissões e regras da plataforma; funcionalidades não oficialmente disponíveis devem ser explicitamente identificadas e não prometidas como prontas.
- Utilizar **os dois SVGs já existentes no repositório** como ativos oficiais — (1) ícone completo e (2) cifrão isolado. **Localizar e inspecionar os caminhos reais antes da implementação**, sem recriá-los nem substituí-los por aproximações.
- Reaproveitamento de código de terceiros exige checar licença, compatibilidade e segurança. Repositórios citados são **fontes de estudo**, não pacotes aprovados automaticamente.
- Manter credenciais somente em infraestrutura segura, fora do aplicativo e do Git.

## Fase 0 — Auditoria e organização do projeto
**Objetivo:** preparar uma base verificável para o desenvolvimento.
- Inventariar os arquivos atuais, encontrar os **dois SVGs oficiais** e conservar seus vetores originais.
- Auditar as referências da seção 4 do documento de visão, verificando licenças e abordagem técnica.
- Escolher a arquitetura do aplicativo Android, backend, banco de dados e gerenciamento de sessões.
- Documentar contas, credenciais necessárias e limites de acesso das APIs (Shopee, TikTok Shop, Groq e WhatsApp).
- Elaborar protótipos das telas e estados de interface, incluindo erros e permissões.
**Critério de conclusão:** arquitetura aprovada, ativos identificados, dependências verificadas e tarefas rastreáveis no GitHub.

## Fase 1 — Aplicativo base e identidade visual
**Objetivo:** entregar o aplicativo navegável com identidade Sellutions.
- Configurar projeto, builds e fluxo de testes do Android.
- Criar telas: **Início/Produtos, Capturar/Importar, Revisar oferta, Anunciar, Grupos, Histórico, Conexões e Configurações**.
- Aplicar a marca pelos **dois SVGs existentes**, com laranja/branco (tema claro), preto/laranja (tema escuro) e gradiente original do ícone.
- Criar cards, filtros básicos, pré-visualização, alertas e tela de login/configuração.
- Preparar interfaces de serviço para conexão com marketplaces, Groq e WhatsApp, inicialmente com dados de teste **claramente marcados como demonstração**.
**Critério:** aplicativo instala, abre, navega sem erros e preserva o design real dos SVGs.

## Fase 2 — Backend, segurança e modelos de dados
**Objetivo:** sustentar integrações e histórico sem expor chaves.
- Modelar contas, conectores, produtos, links, imagens, ofertas, templates, grupos, destinos, envios e auditoria.
- Implementar backend de conexões, validação, logs, rate limiting e armazenamento seguro de credenciais.
- Prever desativação/reconexão de APIs e tratamento de falhas.
- Definir dados mínimos para produto: título, foto, preço, link, loja, horário da última verificação, origem e estado de validação.
**Critério:** app conecta ao backend, salva/recupera dados e não contém tokens no cliente.

## Fase 3 — Primeiros marketplaces: Shopee e TikTok Shop
**Objetivo:** importar produtos reais das duas lojas prioritárias.
- **Shopee:** avaliar o repositório de referência de afiliados, acesso autorizado a catálogo, informação comercial e criação/uso de links de afiliados.
- **TikTok Shop:** examinar API e requisitos efetivos de acesso e autorização da conta; importar metadados e links permitidos.
- Montar conectores separados, com estados **conectado, precisa de autorização, indisponível e erro**.
- Buscar/importar produto por URL e permitir edição/revisão dos campos quando a API não trouxer dados suficientes.
- Conservar a origem e evitar dados fabricados, especialmente preços e promoções.
**Critério:** importar pelo menos um produto real e autorizado de cada loja disponível, com URL correta e dados verificáveis. Bloqueios de acesso devem ser registrados, não mascarados por mock.

## Fase 4 — Groq e geração de texto das publicações
**Objetivo:** redigir anúncios com base no produto importado.
- Integrar a API Groq no backend, com chave segura e mensagem de status.
- Criar prompts estruturados com **produto, valor, desconto (quando comprovado), loja, CTA e link**.
- Produzir variantes (curta, detalhada, promocional), com edição antes do envio e opção de regenerar.
- Criar modelos salvos e preferências de estilo; controlar repetições.
- Impedir invenções de descontos, estoque, frete grátis, urgência e avaliações.
**Critério:** produto real → Groq → texto editável → prévia pronta, com falhas de API tratadas.

## Fase 5 — WhatsApp: conexão, grupos e publicação assistida
**Objetivo:** concluir a jornada do produto ao grupo, sem depender de uma integração simulada.
- Avaliar **capacidades oficiais do WhatsApp Business Platform** e, separadamente, **Evolution API** e **WPPConnect** quanto à licença, manutenção, riscos e compatibilidade com políticas do WhatsApp.
- A conexão deve indicar claramente qual tipo de conta, sessão, permissões e recursos foram realmente habilitados.
- Criar uma área para **listar/organizar grupos** e associar grupos a categorias de ofertas.
- Projetar telas e fluxos para **criação e administração de grupos** (nome, participantes, seleção, exclusão/arquivamento), implementando só operações realmente disponíveis e autorizadas pela integração escolhida.
- Fluxo preferido de envio: **Anunciar → escolher um ou mais grupos → revisar → publicar/compartilhar → obter confirmação real por destino**.
- Se API de grupos/publicação direta não for suportada pela opção oficial, oferecer **compartilhamento assistido pelo WhatsApp** e explicar as limitações; nunca simular envio concluído.
- Exigir autorização do usuário e considerar regras anti-spam, privacidade, consentimento e risco de bloqueio.
**Critério:** texto e link chegam a um destino de teste pelo mecanismo permitido, e o app distingue sucesso confirmado, ação externa pendente e erro. Criação/administração de grupos só são marcadas como concluídas se testadas de ponta a ponta.

## Fase 6 — Captura facilitada e compartilhamento do Android
**Objetivo:** capturar o produto enquanto se navega em outros apps.
- Receber links pelo menu **Compartilhar** do Android.
- Aceitar screenshot/imagem com confirmação do usuário e extrair informações verificáveis.
- Avaliar **botão flutuante** com permissão de sobreposição expressa, desligamento simples e interface discreta.
- Preparar prévia do produto e ligação direta com Groq e Anunciar.
**Critério:** importar via link ou captura, revisar e passar ao fluxo de anúncio, sem perder contexto.

## Fase 7 — Completar marketplaces e afiliados
**Objetivo:** ampliar o catálogo para as lojas planejadas.
- Adicionar **Amazon, Mercado Livre e SHEIN**, uma integração por vez e com autorização/termos adequados.
- Consolidar conta de afiliado por loja, URLs válidas, detecção de duplicatas e informações de comissão quando legitimamente disponíveis.
- Filtros por loja, categoria, preço e tipo de oferta.
**Critério:** conectores independentes, estados claros e dados reais confirmados para cada loja habilitada.

## Fase 8 — Gestão completa de ofertas e grupos
**Objetivo:** melhorar o uso cotidiano.
- Organizar favoritos, produtos anunciados, catálogos, tags e modelos de texto.
- Configurar conjuntos de grupos, regras de seleção e histórico por destino.
- Criar relatórios básicos e reenvio manual com prevenção de duplicidade.
- Refinar ferramentas de gestão dos grupos apenas dentro das capacidades comprovadas da integração.
**Critério:** o usuário consegue saber o que anunciou, onde e quando, e editar modelos/grupos sem perder dados.

## Fase 9 — Agendamento e automações opcionais
**Objetivo:** automatizar somente tarefas autorizadas e suportadas.
- Fila de ofertas, agendamento e controle de tentativas.
- Revalidação de preço/link antes de postar e bloqueio de ofertas desatualizadas.
- Limites por grupo, confirmação humana quando necessária e pausa imediata das automações.
- Não implementar envio massivo, contorno de limitações ou comportamento de spam.
**Critério:** tarefas suportadas executam com logs verificáveis; ações que dependem de intervenção externa são marcadas como pendentes.

## Fase 10 — Validação, segurança e lançamento
**Objetivo:** preparar a versão de uso real.
- Testar fluxos reais de ponta a ponta com Shopee e TikTok Shop, Groq e WhatsApp.
- Revisar privacidade, permissões, licença de dependências, armazenamento de tokens e segurança das APIs.
- Testar experiência Android, acessibilidade, modo escuro, captura, conexões instáveis e recuperação de erros.
- Publicar documentação, instruções de configuração, changelog e pacote de instalação.
- Separar recursos entregues, experimentais e indisponíveis.
**Critério:** versão estável, instalável e com critérios de aceitação documentados.

---

## Marco inicial prioritário — MVP

O primeiro MVP utilizável corresponde às **Fases 0 a 5**, com: aplicativo base personalizado pelos SVGs oficiais, importação autorizada por **Shopee e TikTok Shop**, composição por **Groq**, e publicação/compartilhamento para **WhatsApp** mediante a solução comprovadamente suportada. A gestão avançada de grupos não deve bloquear o MVP se a plataforma não permitir a API necessária; registrar a dependência.

## Referências por área

| Área | Referências a analisar |
| --- | --- |
| Shopee / afiliação | [Shopee BR Affiliate MCP](https://github.com/jeffmonteiroo/shopee-br-affiliate-mcp) |
| Catálogo / ofertas | [Sistema de Achadinhos](https://github.com/goul4rt/sistema-de-achadinhos), [Acha-Tech](https://github.com/andrefeeelipe/Acha-Tech) |
| Mensagens com IA | [bot-achadinhos](https://github.com/vinniciusnascimento/bot-achadinhos) — referência de lógica, não implementação Groq garantida |
| Captura Android | [ScribbleGrab](https://github.com/bakermhajna/ScribbleGrab) |
| WhatsApp / grupos | [Evolution API](https://github.com/EvolutionAPI/evolution-api), [WPPConnect](https://github.com/wppconnect-team/wppconnect) — **exigem análise de riscos, permissões e termos** |
| TikTok Shop / Groq | Conectores a projetar segundo documentação e acesso efetivo; nenhuma implementação desses serviços foi comprovada nos repositórios da lista |

As referências adicionais `loja-integrada`, `multi-marketplace`, `ofertas-grupos`, `amazon-price-tracker` e outra API HTTP de WhatsApp continuam **sem URL verificada**. Não inventar endereços ou afirmar sua adequação.

## Checklist de acompanhamento (a marcar somente após testes)
- [ ] Auditoria dos SVGs e repositórios concluída
- [ ] App base Android navegável
- [ ] Backend e autenticação seguros
- [ ] Shopee integrada e testada com dados reais
- [ ] TikTok Shop integrada e testada com dados reais
- [ ] Groq gerando texto de oferta editável
- [ ] WhatsApp conectado e compartilhamento/publicação testado
- [ ] Gestão de grupos testada conforme possibilidades reais
- [ ] Captura por compartilhamento e imagem testada
- [ ] Amazon, Mercado Livre e SHEIN integrados
- [ ] Histórico, modelos e relatórios
- [ ] Agendamento/automações seguras
- [ ] Versão estável entregue

**Regra:** nenhuma caixa deve ser marcada apenas porque a tela existe, porque um protótipo simula a função ou porque há um projeto de exemplo.
