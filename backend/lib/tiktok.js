'use strict';
const crypto = require('node:crypto');
const BASE='https://sellutions.vercel.app';
const AUTH='https://auth.tiktok-shops.com/api/v2/token/';
const API='https://open-api.tiktokglobalshop.com';
const READ='creator.affiliate_collaboration.read';
const INFO='creator.affiliate.info';
const now=()=>Math.floor(Date.now()/1000);
const random=()=>crypto.randomBytes(32).toString('base64url');
const hash=s=>crypto.createHash('sha256').update(s).digest('hex');
const valid=s=>typeof s==='string'&&/^[A-Za-z0-9_-]{43}$/.test(s);
const key=(type,id)=>'sellutions:tiktok:'+type+':'+hash(id);
class Failure extends Error {constructor(status,code){super(code);this.status=status;this.code=code}}
function cfg(env=process.env,requireEnabled=true){
  const {TIKTOK_APP_KEY:appKey,TIKTOK_APP_SECRET:secret,TIKTOK_REDIRECT_URI:redirect,TIKTOK_PILOT_CODE:pilot,TIKTOK_STORAGE_KEY:enc}=env;
  const redis=env.UPSTASH_REDIS_REST_URL||env.KV_REST_API_URL,redisToken=env.UPSTASH_REDIS_REST_TOKEN||env.KV_REST_API_TOKEN;
  if((requireEnabled&&env.TIKTOK_PILOT_ENABLED!=='true')||!appKey||!secret||redirect!==BASE+'/api/tiktok/callback'||!redis||!redisToken||!pilot||pilot.length<24||!enc||!/^[a-f0-9]{64}$/i.test(enc))throw new Failure(503,'pilot_not_configured');
  const u=new URL(redis);if(u.protocol!=='https:'||u.username||u.password||u.search||u.hash||u.pathname!=='/')throw new Failure(503,'pilot_not_configured');
  return {appKey,secret,redis:u.origin,redisToken,pilot,enc:Buffer.from(enc,'hex')};
}
function same(a,b){return typeof a==='string'&&typeof b==='string'&&crypto.timingSafeEqual(Buffer.from(hash(a),'hex'),Buffer.from(hash(b),'hex'))}
function seal(v,c,aad){const iv=crypto.randomBytes(12), cipher=crypto.createCipheriv('aes-256-gcm',c.enc,iv);cipher.setAAD(Buffer.from(aad));const out=Buffer.concat([cipher.update(JSON.stringify(v)),cipher.final()]);return [iv,cipher.getAuthTag(),out].map(b=>b.toString('base64url')).join('.')}
function unseal(s,c,aad){const [iv,tag,body]=s.split('.').map(v=>Buffer.from(v,'base64url'));const d=crypto.createDecipheriv('aes-256-gcm',c.enc,iv);d.setAAD(Buffer.from(aad));d.setAuthTag(tag);return JSON.parse(Buffer.concat([d.update(body),d.final()]).toString())}
async function jsonFetch(url,opts){try{const r=await fetch(url,{...opts,redirect:'error',signal:AbortSignal.timeout(10000),cache:'no-store'});if(!r.ok)throw Error();return await r.json()}catch{throw new Failure(502,'upstream_unavailable')}}
async function redis(c,...cmd){const j=await jsonFetch(c.redis,{method:'POST',headers:{Authorization:'Bearer '+c.redisToken,'Content-Type':'application/json'},body:JSON.stringify(cmd)});if(j.error||!Object.hasOwn(j,'result'))throw new Failure(502,'storage_unavailable');return j.result}
async function put(c,k,data,ttl,mode){return redis(c,'SET',k,seal(data,c,k),'EX',ttl,mode)}
async function get(c,k){const s=await redis(c,'GET',k);return s?unseal(s,c,k):null}
async function take(c,k){const s=await redis(c,'GETDEL',k);return s?unseal(s,c,k):null}
function tokenData(j,expected){const d=j?.data;if(j?.code!==0||!d||d.user_type!==1||typeof d.open_id!=='string'||!d.open_id||(expected&&d.open_id!==expected)||!Array.isArray(d.granted_scopes)||!d.granted_scopes.every(s=>typeof s==='string')||typeof d.access_token!=='string'||!d.access_token||typeof d.refresh_token!=='string'||!d.refresh_token||!Number.isSafeInteger(d.access_token_expire_in)||!Number.isSafeInteger(d.refresh_token_expire_in)||d.access_token_expire_in<=now()||d.refresh_token_expire_in<=now())throw new Failure(403,'creator_token_invalid');
  if(!d.granted_scopes.includes(INFO)||!d.granted_scopes.includes(READ))throw new Failure(403,'creator_scopes_missing');
  return {status:'connected',open_id:d.open_id,scopes:d.granted_scopes,access:d.access_token,refresh:d.refresh_token,accessExp:d.access_token_expire_in,refreshExp:d.refresh_token_expire_in};
}
async function exchange(c,code,refresh=false,expected){const p=new URLSearchParams({app_key:c.appKey,app_secret:c.secret,grant_type:refresh?'refresh_token':'authorized_code',[refresh?'refresh_token':'auth_code']:code});const j=await jsonFetch(AUTH+(refresh?'refresh':'get')+'?'+p,{method:'GET'});return tokenData(j,expected)}
function sign(path,query,body,secret){const args=Object.keys(query).filter(k=>k!=='sign'&&k!=='access_token').sort().map(k=>k+String(query[k])).join('');return crypto.createHmac('sha256',secret).update(secret+path+args+body+secret).digest('hex')}
async function api(c,t,path,query={},body){const q={...query,app_key:c.appKey,timestamp:String(now())};const raw=body===undefined?'':JSON.stringify(body);q.sign=sign(path,q,raw,c.secret);const j=await jsonFetch(API+path+'?'+new URLSearchParams(q),{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json','x-tts-access-token':t.access},...(body===undefined?{}:{body:raw})});if(j.code!==0){if([105001,105002,101000].includes(j.code))throw new Failure(401,'reauthorization_required');if(j.code===105005)throw new Failure(403,'creator_scopes_missing');throw new Failure(502,'tiktok_api_error')}return j.data||{}}
async function begin(c,invite){if(!same(invite,c.pilot))throw new Failure(403,'pilot_code_invalid');if(!await redis(c,'SET','sellutions:tiktok:begin:rate','1','NX','EX',5))throw new Failure(429,'try_again_later');const session=random(),state=random(),k=key('session',session);if(!await put(c,k,{status:'pending'},600,'NX')||!await put(c,key('state',state),{k},600,'NX'))throw new Failure(502,'storage_unavailable');const u=new URL('https://shop.tiktok.com/alliance/creator/auth');u.searchParams.set('app_key',c.appKey);u.searchParams.set('state',state);return {status:'pending',session,authorization_url:u.toString()}}
async function complete(c,q){if(!valid(q.state))throw new Failure(400,'invalid_state');const pending=await take(c,key('state',q.state));if(!pending||!await get(c,pending.k))throw new Failure(400,'invalid_state');if(q.error||typeof q.code!=='string'||!q.code||q.code.length>2048||(q.app_key!==undefined&&q.app_key!==c.appKey)){await redis(c,'DEL',pending.k);throw new Failure(400,'authorization_denied')}
  try{const t=await exchange(c,q.code);await api(c,t,'/affiliate_creator/202405/profiles');const ttl=Math.min(86400*30,t.refreshExp-now());if(!await put(c,pending.k,t,ttl,'XX'))throw new Failure(409,'session_expired');return true}catch(e){await redis(c,'DEL',pending.k);throw e}}
async function current(c,session){if(!valid(session))throw new Failure(401,'invalid_session');const k=key('session',session);let t=await get(c,k);if(!t)throw new Failure(401,'session_expired');if(t.status!=='connected')return t;
  if(t.refreshExp<=now()){await redis(c,'DEL',k);throw new Failure(401,'reauthorization_required')}
  if(t.accessExp>now()+60)return t;
  const lock=k+':refresh';if(!await redis(c,'SET',lock,'1','NX','EX',30))throw new Failure(409,'refresh_in_progress');
  try{t=await get(c,k);if(!t||t.status!=='connected')throw new Failure(401,'session_expired');if(t.accessExp>now()+60)return t;let updated;try{updated=await exchange(c,t.refresh,true,t.open_id)}catch(e){if(e.status===403)await redis(c,'DEL',k);throw e}if(!await put(c,k,updated,Math.min(86400*30,updated.refreshExp-now()),'XX'))throw new Failure(401,'session_expired');return updated}finally{await redis(c,'DEL',lock)}
}
function sessionFrom(req){const h=req.headers.authorization||'';const m=/^Bearer ([A-Za-z0-9_-]{43})$/.exec(h);if(!m)throw new Failure(401,'invalid_session');return m[1]}
function headers(res){res.setHeader('Cache-Control','no-store');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('X-Content-Type-Options','nosniff')}
function fail(res,e){return res.status(e instanceof Failure?e.status:500).json({status:'error',error:e instanceof Failure?e.code:'internal_error'})}
function bodyOf(req){return typeof req.body==='object'&&req.body!==null?req.body:{}}
function product(p){const price=p.sales_price||p.original_price||{};return {id:typeof p.id==='string'?p.id:'',title:typeof p.title==='string'?p.title.slice(0,300):'',price:typeof price.minimum_amount==='string'?price.minimum_amount:'',currency:typeof price.currency==='string'?price.currency:'',commission_rate:typeof p.commission_rate==='number'||typeof p.commission_rate==='string'?p.commission_rate:null}}
async function device(req,res,action){headers(res);try{const c=cfg();if(action==='begin'&&req.method==='POST')return res.status(200).json(await begin(c,bodyOf(req).pilot_code));const sid=sessionFrom(req);if(action==='disconnect'&&req.method==='POST'){await redis(c,'DEL',key('session',sid));return res.status(200).json({status:'disconnected'})}const t=await current(c,sid);if(action==='status'&&req.method==='GET'){if(t.status==='connected')await api(c,t,'/affiliate_creator/202405/profiles');return res.status(200).json({status:t.status,...(t.status==='connected'?{open_id:t.open_id,scopes:t.scopes}:{})})}if(t.status!=='connected')throw new Failure(409,'authorization_pending');if(action==='search'&&req.method==='POST'){const q=bodyOf(req).query;if(typeof q!=='string'||!q.trim()||q.length>200)throw new Failure(400,'invalid_query');const data=await api(c,t,'/affiliate_creator/202405/open_collaborations/products/search',{page_size:'20'},{title_keywords:q.trim().split(/\s+/).slice(0,20)});return res.status(200).json({status:'ok',products:Array.isArray(data.products)?data.products.slice(0,20).map(product):[]})}throw new Failure(405,'method_not_allowed')}catch(e){return fail(res,e)}}
async function storageCheck(c){const k=key('probe',random()),v=random();try{if(!await put(c,k,{v},30,'NX'))return false;const result=await take(c,k);return result?.v===v&&(await get(c,k))===null}finally{await redis(c,'DEL',k)}}
module.exports={storageCheck,cfg,random,hash,key,same,seal,unseal,tokenData,sign,begin,complete,current,device,headers,fail,Failure,product,valid};
