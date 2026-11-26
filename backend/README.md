# Sellutions backend — TikTok creator pilot
Vercel project root: backend. Canonical callback: https://sellutions.vercel.app/api/tiktok/callback.
Production stays disabled by default; no real authorization until review, creator allowlist, storage and consent are verified.
## API
- POST /v1/tiktok/begin: JSON pilot_code; returns opaque session and official creator authorization URL.
- GET /api/tiktok/callback: one-use state, token exchange, identity/scope checks, creator profile verification.
- GET /v1/tiktok/status: Bearer opaque session; verifies creator API access and refreshes near expiry.
- POST /v1/tiktok/search: Bearer session and JSON query; first 20 open-collaboration products.
- POST /v1/tiktok/disconnect: deletes server-side session; creator must separately revoke app permission at TikTok.
## Setup (server only)
Existing TIKTOK_APP_KEY, TIKTOK_APP_SECRET, TIKTOK_REDIRECT_URI. Add a dedicated private Redis REST database (UPSTASH_REDIS_REST_URL, UPSTASH_REDIS_REST_TOKEN), independent randomly generated 32-byte hex TIKTOK_STORAGE_KEY and high-entropy TIKTOK_PILOT_CODE (at least 24 chars). Never commit them. Only after review, set TIKTOK_PILOT_ENABLED=true and deploy. No database provisioned by this change; check price and retention before enabling. Encrypted tokens use AES-256-GCM with record-bound AAD; Redis stores hashed session/state keys. Pending records expire after ten minutes; active records expire within 30 days or at refresh-token expiry. The mobile session is volatile in this pilot; do not claim it survives app restart. Do not enable before reviewing logs/retention and adding rate limiting appropriate for the pilot. Never log callback query, authorization headers or upstream token URLs.
## Scope
Requires creator.affiliate.info and creator.affiliate_collaboration.read. Other scopes and tracking-link API need endpoint verification. No seller-token substitution. Search results do not generate affiliate links. Shopee remains separate.
## Tests
node --test backend/test/tiktok.test.js. Mock tests do not establish real TikTok approval. Android device test and real OAuth with approved creator are mandatory before a release.
## References
https://partner.tiktokshop.com/docv2/page/678e3a362dccb8030ea6f98c
https://partner.tiktokshop.com/docv2/page/search-open-collaboration-products-202405
https://partner.tiktokshop.com/docv2/page/affiliate-integration
https://upstash.com/docs/redis/features/restapi
