// OAuth callback reservation. Never echo or store authorization codes.
module.exports = function handler(req, res) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  if (req.method !== 'GET') {
    res.setHeader('Allow', 'GET');
    return res.status(405).json({error:'method_not_allowed'});
  }
  const q = req.query || {};
  if (Object.keys(q).length) {
    // Fail closed until state validation and token exchange are implemented.
    return res.status(503).json({status:'authorization_not_configured',message:'Nenhuma conta foi conectada.'});
  }
  return res.status(200).json({service:'Sellutions',provider:'tiktok-shop',status:'callback_available',authorization:'not_configured'});
};
