'use strict';
const crypto = require('node:crypto');
const APK = 'https://github.com/aqnoeu/Sellutions-/releases/latest/download/sellutions-release.apk';
function equal(a,b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length > 256 || !b) return false;
  const digest = s => crypto.createHash('sha256').update(s,'utf8').digest();
  return crypto.timingSafeEqual(digest(a),digest(b));
}
function password(req) {
  if (typeof req.body === 'string') return new URLSearchParams(req.body).get('password');
  return req.body && req.body.password;
}
function error(res,code,message) {
  res.setHeader('Content-Type','text/html; charset=utf-8');
  res.setHeader('Content-Security-Policy',"default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'");
  return res.status(code).send('<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Download Sellutions</title><style>body{font:16px system-ui;background:#fff;color:#151515;min-height:100vh;display:grid;place-items:center;margin:0}main{max-width:400px;padding:30px;margin:20px;border:1px solid #ddd;border-radius:20px}a{display:inline-block;background:#b43b00;color:white;text-decoration:none;padding:12px 18px;border-radius:12px}@media(prefers-color-scheme:dark){body{background:#000;color:#fff}main{border-color:#333}}</style><main><h1>Download Sellutions</h1><p>'+message+'</p><a href="/#download">Voltar ao download</a></main></html>');
}
function handler(req,res) {
  res.setHeader('Cache-Control','no-store, private');
  res.setHeader('Referrer-Policy','no-referrer');
  res.setHeader('X-Content-Type-Options','nosniff');
  if(req.method!=='POST'){res.setHeader('Allow','POST');return error(res,405,'Informe a senha na página de download.');}
  const configured=process.env.SELLUTIONS_DOWNLOAD_PASSWORD;
  if(!configured)return error(res,503,'Download temporariamente indisponível.');
  if(!equal(password(req),configured))return error(res,403,'Senha incorreta. Tente novamente.');
  res.setHeader('Location',APK);
  return res.status(303).end();
}
module.exports=handler;
