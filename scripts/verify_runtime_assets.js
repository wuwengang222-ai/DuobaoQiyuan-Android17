#!/usr/bin/env node
/* Runs ONLY after prepare_runtime.py fetched upstream files. This validates
   actual WebAssembly binary structure, not just a fabricated \0asm header. */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const root = path.resolve(__dirname, '../app/src/main/assets');
function expect(name, predicate) {
  const p=path.join(root,name);
  if (!fs.existsSync(p)) throw new Error('Missing: '+p);
  const buf=fs.readFileSync(p);
  if (!predicate(buf)) throw new Error('Invalid: '+name+' bytes='+buf.length);
  console.log(name, buf.length, crypto.createHash('sha256').update(buf).digest('hex'));
}
expect('vmrp.wasm', b => b.length > 10000 && WebAssembly.validate(b));
expect('vmrp.js', b => b.length > 10000 && !b.subarray(0,128).toString().includes('<html'));
for (const entry of ['fs/cfunction.ext','fs/mythroad/mpc.mrp','fs/mythroad/ydqtwo.mrp',
  'fs/mythroad/plugins/netpay.mrp','fs/mythroad/plugins/flaengine.mrp',
  'fs/mythroad/plugins/ose/brwcore.mrp','fs/mythroad/system/gb12.uc2',
  'fs/mythroad/system/gb16.uc2']) expect(entry, b => b.length > 16);
for (const [file,sha] of [
  ['games/part1.mrp','69f48fd49dea033229ce789e94b396cdf7452ec65e40b963ccbae97717982c2d'],
  ['games/part2.mrp','80299713a7a9dc616b79af1db4dfabd39c6e7b56682012ecad48b81112e8b959']]) {
  expect(file, b => crypto.createHash('sha256').update(b).digest('hex')===sha);
}
console.log('RUNTIME FILE STRUCTURE VALID. NOT proof that RPG gameplay runs.');
