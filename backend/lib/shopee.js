'use strict';
const crypto=require('node:crypto');
const URL_API='https://open-api.affiliate.shopee.com.br/graphql';
class Failure extends Error {constructor(status,code){super(code);this.status=status;this.code=code}}
function config(env=process.env){
  const id=env.SHOPEE_APP_ID,secret=env.SHOPEE_APP_SECRET,pilot=env.SHOPEE_PILOT_CODE||env.TIKTOK_PILOT_CODE;
  const redis=env.UPSTASH_REDIS_REST_URL||env.KV_REST_API_URL,token=env.UPSTASH_REDIS_REST_TOKEN||env.KV_REST_API_TOKEN;
  if(!/^\d+$/.test(id||'')||!secret||!pilot||pilot.length<24||!redis||!token)throw new Failure(503,'shopee_not_configured');
  let u;try{u=new URL(redis)}catch{throw new Failure(503,'shopee_not_configured')}
  if(u.protocol!=='https:'||u.username||u.password||u.search||u.hash||u.pathname!=='/')throw new Failure(503,'shopee_not_configured');
  return {id,secret,pilot,redis:u.origin,token};
}
const digest=s=>crypto.createHash('sha256').update(s,'utf8').digest('hex');
function authorization(c,payload,timestamp=Math.floor(Date.now()/1000)){
  return 'SHA256 Credential='+c.id+', Timestamp='+timestamp+', Signature='+digest(c.id+timestamp+payload+c.secret);
}
function authorize(req,c){
  const h=req.headers.authorization||'';
  if(typeof h!=='string'||h.length>512||!crypto.timingSafeEqual(Buffer.from(digest(h)),Buffer.from(digest('Bearer '+c.pilot))))throw new Failure(401,'unauthorized');
}
async function rate(c,fetcher=fetch){
  try{const r=await fetcher(c.redis,{method:'POST',redirect:'error',cache:'no-store',signal:AbortSignal.timeout(5000),headers:{Authorization:'Bearer '+c.token,'Content-Type':'application/json'},body:JSON.stringify(['SET','sellutions:shopee:rate','1','NX','EX',3])});
    if(!r.ok)throw Error();const j=await r.json();if(j.error||!Object.hasOwn(j,'result'))throw Error();if(j.result!=='OK')throw new Failure(429,'try_again_later');
  }catch(e){if(e instanceof Failure)throw e;throw new Failure(503,'storage_unavailable')}
}
async function graphql(c,query,variables,fetcher=fetch){
  const body=JSON.stringify(variables===undefined?{query}:{query,variables});let r,j;
  try{r=await fetcher(URL_API,{method:'POST',redirect:'error',signal:AbortSignal.timeout(10000),headers:{Authorization:authorization(c,body),'Content-Type':'application/json'},body});j=await r.json()}catch{throw new Failure(502,'shopee_unavailable')}
  if(r.status===429)throw new Failure(429,'shopee_rate_limit');
  if(r.status===401||r.status===403)throw new Failure(403,'shopee_authorization_failed');
  if(Array.isArray(j?.errors)&&j.errors.length){
    const code=Number(j.errors[0]?.extensions?.code??j.errors[0]?.code);
    if(code===10020||code===10035)throw new Failure(403,'shopee_authorization_failed');
    if(code===10030)throw new Failure(429,'shopee_rate_limit');
    throw new Failure(502,'shopee_graphql_error');
  }
  if(!r.ok||!j?.data||typeof j.data!=='object')throw new Failure(502,'shopee_invalid_response');
  return j.data;
}
const searchQuery=({keyword,page,limit})=>'{productOfferV2(keyword:'+JSON.stringify(keyword)+',sortType:1,page:'+page+',limit:'+limit+'){nodes{itemId productName productLink offerLink imageUrl priceMin priceMax priceDiscountRate commissionRate} pageInfo{page limit hasNextPage}}}';
const linkQuery=url=>'mutation {generateShortLink(input:{originUrl:'+JSON.stringify(url)+'}){shortLink}}';
function searchArgs(body){
  const keyword=typeof body.keyword==='string'?body.keyword.trim():'',page=body.page??1,limit=body.limit??10;
  if(!keyword||keyword.length>100||!Number.isInteger(page)||page<1||page>100||!Number.isInteger(limit)||limit<1||limit>20)throw new Failure(400,'invalid_search');
  return {keyword,page,limit};
}
function shopeeUrl(raw){
  if(typeof raw!=='string'||raw.length>2048)throw new Failure(400,'invalid_shopee_url');
  let u;try{u=new URL(raw)}catch{throw new Failure(400,'invalid_shopee_url')}
  if(u.protocol!=='https:'||u.username||u.password||u.port||!(u.hostname==='shopee.com.br'||u.hostname.endsWith('.shopee.com.br')||u.hostname==='shope.ee'||u.hostname==='s.shopee.com.br'))throw new Failure(400,'invalid_shopee_url');
  return u.toString();
}
async function search(c,body,fetcher=fetch){
  const result=(await graphql(c,searchQuery(searchArgs(body)),undefined,fetcher)).productOfferV2;
  if(!Array.isArray(result?.nodes))throw new Failure(502,'shopee_invalid_response');
  const products=result.nodes.filter(p=>p&&typeof p.productName==='string').map(p=>({id:String(p.itemId??''),title:p.productName,product_url:p.productLink??null,affiliate_url:p.offerLink??null,image_url:p.imageUrl??null,price_min:p.priceMin??null,price_max:p.priceMax??null,discount_rate:p.priceDiscountRate??null,commission_rate:p.commissionRate??null,currency:'BRL'}));
  return {products,has_next_page:result.pageInfo?.hasNextPage===true};
}
async function link(c,body,fetcher=fetch){
  const result=(await graphql(c,linkQuery(shopeeUrl(body.url)),undefined,fetcher)).generateShortLink;
  if(typeof result?.shortLink!=='string')throw new Failure(502,'shopee_invalid_response');
  try{shopeeUrl(result.shortLink)}catch{throw new Failure(502,'shopee_invalid_response')}
  return {affiliate_url:result.shortLink};
}
async function handler(req,res){
  res.setHeader('Cache-Control','no-store');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('X-Content-Type-Options','nosniff');
  if(req.method!=='POST'){res.setHeader('Allow','POST');return res.status(405).json({error:'method_not_allowed'})}
  try{
    const c=config();authorize(req,c);
    const action=req.query?.action,body=req.body&&typeof req.body==='object'&&!Array.isArray(req.body)?req.body:{};
    if(!['status','search','link'].includes(action))throw new Failure(400,'invalid_action');
    await rate(c);
    if(action==='status'){const result=await search(c,{keyword:'fone',limit:1});return res.status(200).json({status:'connected',product_search:true,offers_found:result.products.length})}
    return res.status(200).json(action==='search'?await search(c,body):await link(c,body));
  }catch(e){return res.status(e instanceof Failure?e.status:500).json({status:'error',error:e instanceof Failure?e.code:'internal_error'})}
}
module.exports={config,authorization,authorize,rate,graphql,searchArgs,shopeeUrl,search,link,handler};
