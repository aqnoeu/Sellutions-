const test=require('node:test'),assert=require('node:assert/strict'),crypto=require('node:crypto');const t=require('../lib/tiktok');
const c={enc:crypto.randomBytes(32)};const now=Math.floor(Date.now()/1000);
const valid={code:0,data:{user_type:1,open_id:'creator123',granted_scopes:['creator.affiliate.info','creator.affiliate_collaboration.read'],access_token:'fake-access',refresh_token:'fake-refresh',access_token_expire_in:now+3600,refresh_token_expire_in:now+86400}};
test('disabled by default',()=>assert.throws(()=>t.cfg({}),/pilot_not_configured/));
test('AES-GCM round trip, bound to record and tamper resistant',()=>{const s=t.seal({token:'fake'},c,'record');assert.deepEqual(t.unseal(s,c,'record'),{token:'fake'});assert.throws(()=>t.unseal(s,c,'other'));const parts=s.split('.');const b=Buffer.from(parts[2],'base64url');b[0]^=1;parts[2]=b.toString('base64url');assert.throws(()=>t.unseal(parts.join('.'),c,'record'))});
test('creator type, scopes, identity and expiry validated',()=>{assert.equal(t.tokenData(valid).status,'connected');for(const change of [{user_type:0},{granted_scopes:[]},{access_token_expire_in:now-1},{open_id:'different'}])assert.throws(()=>t.tokenData({code:0,data:{...valid.data,...change}},'creator123'))});
test('sign sorts query and excludes sign and access token',()=>assert.equal(t.sign('/path',{z:2,a:1,sign:'ignored',access_token:'ignored'},'{}','secret'),crypto.createHmac('sha256','secret').update('secret/patha1z2{}secret').digest('hex')));
test('random states have 256 bits and separate keys',()=>{const s=t.random();assert.ok(t.valid(s));assert.notEqual(s,t.random());assert.notEqual(t.key('state',s),t.key('session',s))});
test('only approved product fields returned',()=>assert.deepEqual(t.product({id:'123',title:'Product',sales_price:{minimum_amount:'12',currency:'BRL'},commission_rate:123,secret:'no'}),{id:'123',title:'Product',price:'12',currency:'BRL',commission_rate:123}));

test('mocked end-to-end: authorization, replay, API access, refresh, disconnect',async()=>{
 const original=global.fetch;const records=new Map();let calls=0,refresh=0;
 const config={appKey:'fake-key',secret:'fake-secret',redis:'https://redis.example',redisToken:'fake',enc:c.enc};
 const fake=(v)=>new Response(JSON.stringify(v),{status:200});
 global.fetch=async(url,opts)=>{
   if(url===config.redis){const [cmd,k,v,...rest]=JSON.parse(opts.body);let result=null;
     if(cmd==='SET'){if(rest.includes('NX')&&records.has(k)||rest.includes('XX')&&!records.has(k))result=null;else{records.set(k,v);result='OK'}}
     if(cmd==='GET'||cmd==='GETDEL'){result=records.get(k)||null;if(cmd==='GETDEL')records.delete(k)}
     if(cmd==='DEL'){result=records.delete(k)?1:0}
     if(cmd==='EVAL'){assert.equal(rest.at(-1),30);result=1}
     return fake({result});
   }
   const u=new URL(url);
   if(u.hostname==='auth.tiktok-shops.com'){if(u.pathname.endsWith('/refresh')){refresh++;assert.equal(u.searchParams.get('grant_type'),'refresh_token');return fake({code:0,data:{...valid.data,access_token:'new-access',access_token_expire_in:now+3600}})}calls++;assert.equal(u.searchParams.get('auth_code'),'fake-code');return fake({code:0,data:{...valid.data,access_token_expire_in:now+30}})}
   if(u.hostname==='open-api.tiktokglobalshop.com'){assert.ok(opts.headers['x-tts-access-token']);assert.equal(u.searchParams.get('app_key'),'fake-key');assert.ok(u.searchParams.get('sign'));return fake({code:0,data:{creator_user_id:'creator'}})}
   throw Error('unexpected network');
 };
 try{const b=await t.begin(config);const state=new URL(b.authorization_url).searchParams.get('state');
 assert.equal((await t.current(config,b.session)).status,'pending');await assert.rejects(t.complete(config,{state:t.random(),code:'fake-code'}),/invalid_state/);
 await t.complete(config,{state,code:'fake-code',app_key:'fake-key'});assert.equal(calls,1);await assert.rejects(t.complete(config,{state,code:'fake-code'}),/invalid_state/);assert.equal(calls,1);
 const got=await t.current(config,b.session);assert.equal(got.access,'new-access');assert.equal(refresh,1);assert.ok(!JSON.stringify([...records.values()]).includes('new-access'));assert.ok(!JSON.stringify([...records.values()]).includes('fake-refresh'));
 records.delete(t.key('session',b.session));await assert.rejects(t.current(config,b.session),/session_expired/);
 }finally{global.fetch=original}
});
