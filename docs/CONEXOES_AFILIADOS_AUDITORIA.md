# Sellutions — Conectores de afiliados (auditoria técnica de 22/11/2026)

## Contrato de produto — não negociável
O fluxo final é **Conexões → Conectar Shopee/TikTok → login na plataforma → consentir → perfil conectado → pesquisar catálogo → gerar link oficial identificado com a conta autorizada → publicar → consultar dados e comissões autorizados**. Usuários não devem procurar chaves de desenvolvedor. O app não pode trocar links por links de terceiros, usar comissões de agregadores nem afirmar conexão sem autenticação efetiva.

## O que a pesquisa verificou
- **TikTok Shop Creator**: possui fluxo autorizado por conta de criador com `https://shop.tiktok.com/alliance/creator/auth?app_key=...&state=...`, callback com `code` e `state`, troca de token, checagem de `user_type=1` e `granted_scopes`. APIs de busca por colaboração aberta existem e exigem autorização de criador. O Sellutions precisa de registro e aprovação própria no Partner Center, escopos específicos, domínio HTTPS/callback e armazenamento seguro. Login comum do TikTok ou TikTok Ads **não** equivale a autorização de afiliado TikTok Shop.
- **Shopee Affiliate BR**: GraphQL oficial `https://open-api.affiliate.shopee.com.br/graphql` usa App ID e Secret da conta de afiliados para consultas, geração de link e relatórios. **Não foi identificado fluxo público comprovado de autorização OAuth de afiliados por login simples**. OAuth de vendedor Shopee e OAuth de um servidor MCP não dão, por si, comissões ao afiliado. É necessário obter acesso específico ou parceria/integração de terceiros autorizada pela Shopee, sem esconder exigências ou capturar senhas/cookies.
- Repositório `jeffmonteiroo/shopee-br-affiliate-mcp`: consulta API de afiliados com chave individual, não implementa login OAuth de afiliado Shopee sem App ID/Secret.
- Repositórios de achadinhos: fluxos de catálogo/postagem não provam que links emitidos pertençam ao afiliado conectado; não são substitutos da autorização oficial.

## Código disponível na branch `feat/affiliate-oauth-connectors`
1. `server/tiktok-creator-oauth.cjs` — **piloto local** de OAuth Creator, `state` aleatório de uso único, callback, troca de token, verificação de tipo de conta e escopos, status por sessão e desconexão local. Não expõe tokens em respostas. Inicia apenas em `127.0.0.1` por padrão.
2. `server/affiliate-ownership.cjs` — validação independente da plataforma para impedir publicação como 'link de afiliado verificado' quando o dono da conta, a plataforma ou o identificador de afiliado diferem.
3. `server/affiliate-ownership.test.cjs` + GitHub Actions — testes de isolamento, mismatch, bloqueio de agregadores e de conexões não autorizadas.

## O que **não** está pronto para o usuário
- Nenhuma conta TikTok Shop ou Shopee foi autorizada no Sellutions; o servidor ainda não foi implantado e os valores `TIKTOK_SHOP_APP_KEY`, `TIKTOK_SHOP_APP_SECRET`, `TIKTOK_CREATOR_SCOPES` e `PUBLIC_ORIGIN` não estão configurados.
- A UI Android **não** chama esse serviço ainda: exibir um botão de 'Conectar' sem ambiente e callback validados seria enganoso.
- Não foram implementados: renovação de tokens, banco persistente de contas com criptografia, autenticação de usuário Sellutions, assinatura das chamadas de APIs TikTok, busca real de catálogo, emissão de links oficiais, relatórios de comissão, reconciliação de vendas e desconexão/revogação no provedor.
- Não apresentar o piloto como produção nem expô-lo à internet: sua memória volátil e falta de identidade permanente não servem para multiusuários.

## Bloqueadores externos reais e os passos verificáveis
| Plataforma | Para liberar conexão de contas sem chave por usuário | O que validar antes de marcar conectado |
| --- | --- | --- |
| TikTok Shop | Aplicação Affiliate Creator aprovada no Partner Center, `app_key` e `app_secret` de desenvolvedor, permissões `creator.*`, URL de retorno HTTPS e backend seguro | Callback+state+token válido `user_type=1`, escopos, busca real e link oficial atribuível ao criador |
| Shopee BR | Autorização oficial de afiliado que permita delegar acesso (caso seja disponibilizada) **ou** fluxo aprovado que associe credenciais individuais ao afiliado, sem expô-las no APK | Testar busca, link e relatório em conta própria de afiliado; não usar OAuth de vendedor |

### Referências oficiais e de código
- https://partner.tiktokshop.com/docv2/page/creator-authorization-guide
- https://partner.tiktokshop.com/docv2/page/affiliate-integration
- https://partner.tiktokshop.com/docv2/page/search-open-collaboration-products-202405
- https://developers.tiktok.com/blog/2024-tiktok-shop-affiliate-apis-launch-developer-opportunity
- https://affiliate.shopee.com.br/open_api
- https://github.com/jeffmonteiroo/shopee-br-affiliate-mcp

**Definição de concluído:** uma conta de teste do próprio usuário autoriza o Sellutions, API entrega resultados reais, um link gerado identifica a mesma conta e o registro corresponde à conta autorizada. Só aí apresentar 'Conectado' e liberar publicação remunerada por aquela integração.
