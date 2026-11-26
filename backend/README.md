# Sellutions backend
Vercel project root: `backend`. Framework: Other (no dependencies).
- GET /api/health: service status.
- GET /api/tiktok/callback: stable redirect endpoint for registration. It intentionally rejects all query parameters until authorization is configured.
- No tokens, secrets, or user data are persisted. Do not send real authorization requests until the OAuth flow is implemented and approved.
- Next steps: confirm creator API permissions and exact OAuth protocol; implement authenticated initiation, one-time state validation, token exchange and encrypted per-account storage; configure secrets only in server environment; test end to end. Shopee integration is not yet implemented.
