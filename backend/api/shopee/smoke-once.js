'use strict';
const crypto=require('node:crypto');
const {config,rate,search,link}=require('../../lib/shopee');
const proof='97bee4364e0193a8f3880b86c6397b5784d62472fd5737cfaeff0544d2d3035f';
module.exports=async(req,res)=>{
 res.setHeader('Cache-Control','no-store');res.setHeader('Referrer-Policy','no-referrer');
 const nonce=req.query?.nonce;
 if(req.method!=='GET'||Date.now()>1795743529797||typeof nonce!=='string'||nonce.length!==64||crypto.createHash('sha256').update(nonce).digest('hex')!==proof)return res.status(404).json({error:'not_found'});
 try{
 const c=config(),r=await fetch(c.redis,{method:'POST',redirect:'error',signal:AbortSignal.timeout(5000),headers:{Authorization:'Bearer '+c.token,'Content-Type':'application/json'},body:JSON.stringify(['SET','sellutions:shopee:smoke:final:20261126','1','NX','EX',86400])});
 const j=await r.json();if(!r.ok||j.error||!Object.hasOwn(j,'result'))return res.status(503).json({error:'storage_unavailable'});if(j.result!=='OK')return res.status(409).json({error:'probe_consumed'});
 await rate(c);
 const data=await search(c,{keyword:'fone',limit:3});
 const sample=data.products.find(p=>p.product_url);
 let short_link='not_tested';if(sample){try{const l=await link(c,{url:sample.product_url});short_link=typeof l.affiliate_url==='string'?'success':'failed'}catch(e){short_link=e.code||'internal_error'}}
 return res.status(200).json({search:'success',count:data.products.length,prices:data.products.some(p=>p.price_min!=null),commissions:data.products.some(p=>p.commission_rate!=null),offer_links:data.products.some(p=>!!p.affiliate_url),short_link});
 }catch(e){return res.status(e.status||500).json({search:'failed',error:e.code||'internal_error'})}
};