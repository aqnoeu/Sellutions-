'use strict';
/**
 * Sellutions: TikTok Shop Affiliate Creator OAuth service (pilot).
 * No Shopee seller OAuth: that would NOT authorize affiliate commissions.
 *
 * Requires approved TikTok Shop Affiliate Creator app + registered callback.
 * Volatile memory only. This is a development service, not a production token vault.
 */
const http = require('node:http');
const crypto = require('node:crypto');
const {URL} = require('node:url');
const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || '127.0.0.1'; // pilot: not remotely accessible by default
const ORIGIN = process.env.PUBLIC_ORIGIN || '';
const APP_KEY = process.env.TIKTOK_SHOP_APP_KEY || '';
const APP_SECRET = process.env.TIKTOK_SHOP_APP_SECRET || '';
const REQUIRED_SCOPES = (process.env.TIKTOK_CREATOR_SCOPES || '').split(',').map(x => x.trim()).filter(Boolean);
const AUTHORIZE = 'https://shop.tiktok.com/alliance/creator/auth';
const EXCHANGE = 'https://auth.tiktok-shops.com/api/v2/token/get';
const TTL = 10 * 60 * 1000;
const sessions = new Map(); // development only, volatile. Store tokens only server-side.
const random = () => crypto.randomBytes(32).toString('base64url');
const safeEqual = (a,b) => typeof a === 'string' && typeof b === 'string' &&
  a.length === b.length && crypto.timingSafeEqual(Buffer.from(a),Buffer.from(b));
function send(res, code, body) {
  res.writeHead(code, {'Content-Type':'application/json; charset=utf-8', 'Cache-Control':'no-store',
    'X-Content-Type-Options':'nosniff'});
  res.end(JSON.stringify(body));
}
function configured() {
  return Boolean(APP_KEY && APP_SECRET && REQUIRED_SCOPES.length > 0 &&
    /^https:\/\/[^/]+$/.test(ORIGIN));
}
function begin() {
  if(!configured()) throw new Error('not_configured');
  const session=random(), state=random();
  const u = new URL(AUTHORIZE);
  u.searchParams.set('app_key',APP_KEY);
  u.searchParams.set('state',state);
  sessions.set(session,{state, status:'pending',created:Date.now(),tokens:null});
  return {session,authorization_url:u.toString(),status:'pending'};
}
function get(session) {
  const s=sessions.get(session);
  if(!s || Date.now()-s.created>TTL && s.status==='pending') return null;
  return s;
}
function findState(state) {
  for (const [session,s] of sessions) if (safeEqual(s.state,state)) return [session,s];
  return null;
}
async function exchange(code) {
  const url = new URL(EXCHANGE);
  for (const [k,v] of Object.entries({app_key:APP_KEY,app_secret:APP_SECRET,auth_code:code,grant_type:'authorized_code'})) url.searchParams.set(k,v);
  const response=await fetch(url,{headers:{Accept:'application/json'},signal:AbortSignal.timeout(15000)});
  if(!response.ok) throw new Error('token_http_'+response.status);
  const data=await response.json();
  if (data.code !== 0 || !data.data?.access_token || !data.data?.refresh_token)
    throw new Error('token_exchange_denied');
  const t=data.data;
  if (Number(t.user_type) !== 1 || !t.open_id) throw new Error('not_creator_account');
  const granted=Array.isArray(t.granted_scopes)?t.granted_scopes:
    String(t.granted_scopes||'').split(',').map(x=>x.trim());
  if(REQUIRED_SCOPES.some(scope=>!granted.includes(scope))) throw new Error('missing_creator_scopes');
  return {access_token:t.access_token,refresh_token:t.refresh_token,open_id:t.open_id,
    user_type:1,granted_scopes:granted,expires_in:t.access_token_expire_in||null};
}
async function handler(req,res) {
  try {
    const url=new URL(req.url,'http://local');
    if(req.method==='GET' && url.pathname==='/health')
      return send(res,200,{service:'sellutions-affiliate',configured:configured(),tikTokCreator:'approval_required'});
    if(req.method==='POST' && url.pathname==='/v1/tiktok/begin'){
      if(!configured()) return send(res,503,{error:'tiktok_partner_app_not_configured'});
      return send(res,200,begin());
    }
    if(req.method==='GET' && url.pathname==='/v1/tiktok/status'){
      const session=req.headers['authorization']?.replace(/^Bearer /,'');
      const s=get(session);
      if(!s)return send(res,401,{error:'session_unknown_or_expired'});
      return send(res,200,{status:s.status,open_id:s.status==='connected'?s.tokens.open_id:undefined,
        granted_scopes:s.status==='connected'?s.tokens.granted_scopes:undefined});
    }
    if(req.method==='POST' && url.pathname==='/v1/tiktok/disconnect'){
      const session=req.headers['authorization']?.replace(/^Bearer /,'');
      if(!get(session))return send(res,401,{error:'session_unknown_or_expired'});
      sessions.delete(session);
      return send(res,200,{status:'disconnected',note:'Revoke authorization separately at TikTok Shop'});
    }
    if(req.method==='GET' && url.pathname==='/v1/tiktok/callback'){
      const state=url.searchParams.get('state'),code=url.searchParams.get('code');
      const hit=state?findState(state):null;
      if(!hit)return send(res,400,{error:'invalid_oauth_state'});
      const [,s]=hit;
      if(s.status!=='pending')return send(res,409,{error:'oauth_state_already_used'});
      s.state=''; // one time use even on exchange failure
      if(!code||url.searchParams.has('error')){
        s.status='denied';return send(res,400,{error:'authorization_denied'});
      }
      try{
        s.tokens=await exchange(code);
        s.status='connected';
        return send(res,200,{message:'TikTok Shop autorizado. Volte ao Sellutions.',status:'connected'});
      } catch(e) {
        s.status='failed';
        return send(res,502,{error:e.message});
      }
    }
    return send(res,404,{error:'not_found'});
  }catch(e){return send(res,500,{error:'internal_error'});}
}
if(require.main===module) http.createServer((req,res)=>{handler(req,res);}).listen(PORT,HOST,()=>console.log('Sellutions OAuth pilot listening on '+HOST+':'+PORT));
module.exports={handler,begin,configured,exchange,sessions};
