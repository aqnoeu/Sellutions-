'use strict';
const crypto=require('node:crypto');
const {config,rate,authorization}=require('../../lib/shopee');
const proof='a61a8f0419077f04163ff357eff36913ae070ec23f2c0f6b1e17242dca270a19';
async function call(c,query,variables){
 const body=JSON.stringify(variables===undefined?{query}:{query,variables});
 const r=await fetch('https://open-api.affiliate.shopee.com.br/graphql',{method:'POST',redirect:'error',signal:AbortSignal.timeout(10000),headers:{Authorization:authorization(c,body),'Content-Type':'application/json'},body});
 const j=await r.json();const err=j.errors?.[0];const msg=String(err?.message||'');
 return {data:j.data,summary:{http:r.status,code:Number(err?.extensions?.code??err?.code??0)||null,category:!err?'none':/signature|credential|access|permission|authoriz|authentic/i.test(msg)?'authorization':/variable|argument|type|field|syntax|parse/i.test(msg)?'query':/param/i.test(msg)?'parameter':/limit|thrott/i.test(msg)?'rate':/ip|whitelist/i.test(msg)?'network_policy':'other'}};
}
module.exports=async(req,res)=>{
 res.setHeader('Cache-Control','no-store');res.setHeader('Referrer-Policy','no-referrer');
 const nonce=req.query?.nonce;
 if(req.method!=='GET'||Date.now()>1795743529797||typeof nonce!=='string'||nonce.length!==64||crypto.createHash('sha256').update(nonce).digest('hex')!==proof)return res.status(404).json({error:'not_found'});
 try{
 const c=config(),r=await fetch(c.redis,{method:'POST',redirect:'error',signal:AbortSignal.timeout(5000),headers:{Authorization:'Bearer '+c.token,'Content-Type':'application/json'},body:JSON.stringify(['SET','sellutions:shopee:smoke:diag:20261126','1','NX','EX',86400])});
 const j=await r.json();if(!r.ok||j.error||!Object.hasOwn(j,'result'))return res.status(503).json({error:'storage_unavailable'});if(j.result!=='OK')return res.status(409).json({error:'probe_consumed'});
 await rate(c);
 const fields='nodes{itemId productName productLink offerLink imageUrl priceMin priceMax priceDiscountRate commissionRate} pageInfo{page limit hasNextPage}';
 const a=await call(c,'query Products($keyword:String!, $page:Int!, $limit:Int!){productOfferV2(keyword:$keyword,page:$page,limit:$limit){'+fields+'}}',{keyword:'fone',page:1,limit:3});
 const b=await call(c,'{productOfferV2(keyword:"fone",sortType:1,page:1,limit:3){'+fields+'}}');
 const data=b.data?.productOfferV2||a.data?.productOfferV2;const sample=data?.nodes?.find(p=>p.productLink);
 let link={tested:false};if(sample&&/^https:\/\/([a-z0-9-]+\.)?shopee\.com\.br\//i.test(sample.productLink)){
 const l=await call(c,'mutation {generateShortLink(input:{originUrl:'+JSON.stringify(sample.productLink)+'}){shortLink}}');link={tested:true,...l.summary,success:typeof l.data?.generateShortLink?.shortLink==='string'};
 }
 return res.status(200).json({variables:a.summary,literal:b.summary,count:Array.isArray(data?.nodes)?data.nodes.length:null,prices:data?.nodes?.some(p=>p.priceMin!=null)||false,commissions:data?.nodes?.some(p=>p.commissionRate!=null)||false,offer_links:data?.nodes?.some(p=>!!p.offerLink)||false,link});
 }catch(e){return res.status(502).json({error:e.code||'diagnostic_failed'})}
};