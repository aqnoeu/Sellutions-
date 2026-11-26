const test=require('node:test'),assert=require('node:assert/strict'),handler=require('../api/download');
function request(method,body,configured='test-password'){
 const old=process.env.SELLUTIONS_DOWNLOAD_PASSWORD;
 if(configured===null)delete process.env.SELLUTIONS_DOWNLOAD_PASSWORD;else process.env.SELLUTIONS_DOWNLOAD_PASSWORD=configured;
 const result={headers:{}};const res={setHeader(k,v){result.headers[k]=v},status(n){result.status=n;return this},send(s){result.body=s;return this},end(){return this}};
 try{handler({method,body},res);return result}finally{if(old===undefined)delete process.env.SELLUTIONS_DOWNLOAD_PASSWORD;else process.env.SELLUTIONS_DOWNLOAD_PASSWORD=old}
}
test('GET never downloads',()=>assert.equal(request('GET',{password:'test-password'}).status,405));
test('bad or absent password rejected',()=>{for(const body of [{password:'wrong'},{},null,{password:['test-password']}]){const r=request('POST',body);assert.equal(r.status,403);assert.equal(r.headers.Location,undefined)}});
test('only matching password redirects to current official APK',()=>{for(const body of [{password:'test-password'},'password=test-password']){const r=request('POST',body);assert.equal(r.status,303);assert.equal(r.headers.Location,'https://github.com/aqnoeu/Sellutions-/releases/latest/download/sellutions-release.apk');assert.match(r.headers['Cache-Control'],/no-store/)}});
test('fails closed if secret missing',()=>assert.equal(request('POST',{password:'test-password'},null).status,503));
