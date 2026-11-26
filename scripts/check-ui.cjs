const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync('app/src/main/assets/index.html','utf8');
const js = html.match(/<script>([\s\S]*?)<\/script>/);
assert.ok(js, 'JavaScript não encontrado');
new vm.Script(js[1], {filename:'index.html'});
for (const id of ['tab-home','tab-catalog','tab-queue','tab-groups','tab-settings','groupsList','queueList','templateName','catalogStatus']) {
  // templateName is dynamically generated in the modal, not present in the static HTML
  if(id === 'templateName') {
    assert.match(js[1], /id="templateName"/);
  } else {
    assert.ok(html.includes('id="'+id+'"'), 'Elemento ausente: '+id);
  }
}
for (const asset of ['sellutions-cifrao.svg','sellutions-icone.svg']) {
  assert.ok(html.includes(asset), 'Marca oficial não utilizada: '+asset);
  assert.ok(fs.existsSync('assets/brand/'+asset), 'SVG oficial ausente: '+asset);
}
const nav = html.match(/<nav class="bottom"[\s\S]*?<\/nav>/)?.[0];
assert.ok(nav, 'Barra de navegação não encontrada');
assert.equal((nav.match(/class="nav-icon"/g) || []).length, 4, 'A barra deve ter quatro SVGs vetoriais');
for (const tab of ['home', 'catalog', 'queue', 'groups']) {
  assert.ok(nav.includes('data-tab="'+tab+'"'), 'Aba não encontrada: '+tab);
}
assert.ok(nav.includes('aria-current="page"'), 'Aba ativa deve ser acessível');
assert.ok(!/[⌂▦▤♧]/.test(nav), 'Símbolos antigos devem ser removidos');
assert.ok(!html.includes('products=[{'), 'Produtos fictícios não devem entrar na versão Android');
console.log('Verificação estática OK: JavaScript válido, telas e marcas presentes.');

require('./test-shopee-ui.cjs');
