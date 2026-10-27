// Error-path integration test: intentionally fail a required preloaded file.
// This is a JS mock, NOT actual VMRP or Android execution.
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
let ready = 0, failures = [], dependencies = new Set();
const dirs = new Set(['/']);
const mockFS = {
  mkdir(p) { if (dirs.has(p)) throw Error('exists'); dirs.add(p); },
  analyzePath(p) { return {exists:dirs.has(p)}; },
  createPreloadedFile(folder, name, url, read, write, onload, onerror) {
    if (url.includes('gb16.uc2')) onerror(Error('deliberate fixture error'));
    else onload();
  }
};
const ctx = {
  window: {location:{search:'?chapter=1'}, DuobaoNative: {
    onRuntimeFailure(chapter,why){ failures.push([chapter, why]); },
    onEmulatorInitialized(){ ready++; }
  }},
  Module:{preRun:[],postRun:[]}, FS:mockFS,
  URLSearchParams, HTMLTextAreaElement:undefined,
  document:{ querySelector(){return null}, addEventListener(){}},
  addRunDependency(dep){dependencies.add(dep)}, removeRunDependency(dep){dependencies.delete(dep)},
  console:{...console,error(){}}, alert(){ throw Error('Unexpected alert') }
};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync('app/src/main/assets/duobao_bridge.js','utf8'),ctx);
ctx.Module.preRun[0]();
ctx.Module.postRun[0]();
assert.equal(dependencies.size,0);
assert.equal(ready,0,'missing game data must not be reported as playable engine');
assert(failures.some(([chapter,why])=>chapter===1 && why.includes('gb16.uc2')));
console.log('PASS: failed preload emits diagnostics and blocks false-positive VMRP started signal (JS fixture only)');
