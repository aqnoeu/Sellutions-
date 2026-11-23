'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const {PLATFORM,authorizeAffiliateLink,canPublish}=require('./affiliate-ownership.cjs');
const connection={ownerUserId:'user-1',platform:PLATFORM.SHOPEE,status:'connected',
 verifiedAffiliateAccountId:'affiliate-1',capabilities:{affiliateLinks:true}};
const product={platform:PLATFORM.SHOPEE,productId:'product-19'};
const generatedLink={url:'https://s.shopee.com.br/abc',attributedAffiliateAccountId:'affiliate-1'};
test('owner can publish verified official link',()=>{
 const x=authorizeAffiliateLink({userId:'user-1',connection,product,generatedLink});
 assert.equal(x.ownerUserId,'user-1');assert.equal(canPublish(x),true);
});
test('other user cannot borrow this affiliate connection',()=>{
 assert.throws(()=>authorizeAffiliateLink({userId:'user-2',connection,product,generatedLink}),/not_authorized/);
});
test('reject affiliate attribution to third party',()=>{
 assert.throws(()=>authorizeAffiliateLink({userId:'user-1',connection,product,
 generatedLink:{...generatedLink,attributedAffiliateAccountId:'third-party'}}),/mismatch/);
});
test('reject aggregator links even if they claim the right affiliate ID',()=>{
 assert.throws(()=>authorizeAffiliateLink({userId:'user-1',connection,product,
 generatedLink:{...generatedLink,url:'https://aggregator.example/offer'}}),/unexpected/);
});
test('reject unauthenticated and manually pasted links as verified',()=>{
 assert.equal(canPublish({url:'https://s.shopee.com.br/x',attribution:'unverified'}),false);
 assert.throws(()=>authorizeAffiliateLink({userId:'user-1',connection:{...connection,status:'disconnected'},product,generatedLink}),/not_authorized/);
});
test('TikTok cannot use Shopee connection',()=>{
 assert.throws(()=>authorizeAffiliateLink({userId:'user-1',connection,product:{platform:PLATFORM.TIKTOK,productId:'p'},generatedLink}),/not_authorized/);
});
