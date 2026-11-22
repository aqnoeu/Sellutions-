'use strict';
const test=require('node:test');
const assert=require('node:assert/strict');
const http=require('node:http');
const {handler}=require('./tiktok-creator-oauth.cjs');

async function request(path,method='GET',headers={}) {
 const server=http.createServer(handler);
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try {
  const address=server.address();
  const response=await fetch('http://127.0.0.1:'+address.port+path,{method,headers});
  return {status:response.status,body:await response.json()};
 } finally {
  await new Promise(resolve=>server.close(resolve));
 }
}
test('unauthorized status cannot access any user session',async()=>{
 const x=await request('/v1/tiktok/status');
 assert.equal(x.status,401);
 assert.equal(x.body.error,'session_unknown_or_expired');
});
test('random callback rejected before any token exchange',async()=>{
 const x=await request('/v1/tiktok/callback?state=forged&code=forged');
 assert.equal(x.status,400);
 assert.equal(x.body.error,'invalid_oauth_state');
});
test('disconnect requires valid session',async()=>{
 const x=await request('/v1/tiktok/disconnect','POST');
 assert.equal(x.status,401);
});
test('health does not leak app secrets',async()=>{
 const x=await request('/health');
 assert.equal(x.status,200);
 assert.equal(x.body.service,'sellutions-affiliate');
 assert.equal(Object.hasOwn(x.body,'app_secret'),false);
});
