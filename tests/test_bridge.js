// Mocks verify the glue layer only. They do NOT execute MRP or Android.
const assert = require('assert');
const fs = require('fs');
const vm = require('vm');
const files = new Map();
const dirs = new Set(['/']);
const observed = [];
let savedJSON = null;
let dependencies = new Set();
const probeTimers = [];
const ocrTimers = [];
let sample = 0;
let called = 0;
const mockFS = {
  mkdir(p) { if (dirs.has(p)) throw new Error('exists'); dirs.add(p); },
  analyzePath(p) { return { exists:dirs.has(p) }; },
  createPreloadedFile(folder,name,url,r,w,onload) {
    const target = (folder==='/'?'':folder) + '/' + name;
    files.set(target, new Uint8Array([1,2,3,4]));
    onload();
  },
  readdir(folder) {
    const prefix = folder.endsWith('/')?folder:folder+'/';
    return ['.','..', ...new Set([
      ...[...files.keys(), ...dirs].filter(p=>p.startsWith(prefix) && p!==folder)
        .map(p=>p.slice(prefix.length).split('/')[0]).filter(Boolean)
    ])];
  },
  lookupPath(p){ return {node:{mode:dirs.has(p)?1:0}}; },
  isDir(m){return m===1;}, isFile(m){return m===0;},
  readFile(p){return files.get(p);},
  writeFile(p,d){files.set(p,d);}
};
const native = {
  loadSaveState(c) {assert.equal(c,1);return '{"files":{}}';},
  persistSaveState(c,data){assert.equal(c,1);savedJSON=data;return true;},
  onDisplayedText(c,t){observed.push(['text',c,t]);},
  onCanvasActivityDetected(c){observed.push(['canvas',c]);},
  onGameCanvasPNG(c,s){observed.push(['ocr_png',c,s]);},
  onEmulatorInitialized(c){observed.push(['wasm_initialized',c]);},
  onContinueInput(c){observed.push(['confirm',c]);},
  onChapterSaveFlushed(c,ok){observed.push(['saved',c,ok]);}
};
// Model a real DOM textarea property to test the VMRP-visible text route.
class MockTextArea { constructor(){this._value='';} }
Object.defineProperty(MockTextArea.prototype,'value', {
  configurable:true, get(){return this._value;},set(v){this._value=String(v);}
});
const textArea=new MockTextArea();
const ctx = {
  window: {location:{search:'?chapter=1'}, DuobaoNative:native,
    js_textCreate:(...args)=>args, js_dialogRefresh:(...args)=>args},
  Module:{preRun:[],postRun:[],_c_event(...args){called++;return called;}},
  FS:mockFS, URLSearchParams, HTMLTextAreaElement:MockTextArea, document:{
    addEventListener(){},
    querySelector(sel){return sel==='#dialog textarea'?textArea:null;},
    getElementById(id){return id==='canvas'?{}:null;},
    createElement(tag) { assert.equal(tag, 'canvas'); return { toDataURL() {return 'data:image/png;base64,'+'A'.repeat(80)}, getContext() {return {
      clearRect(){},
      drawImage(){},
      getImageData(){ return {data:new Uint8ClampedArray(24*32*4).fill(++sample)}; }
    }; } }; }
  },
  Uint8Array, btoa:s=>Buffer.from(s,'binary').toString('base64'),
  atob:s=>Buffer.from(s,'base64').toString('binary'),
  addRunDependency:x=>dependencies.add(x),
  removeRunDependency:x=>dependencies.delete(x),
  setInterval(fn,ms){if(ms===800) probeTimers.push(fn); else if(ms===1700) ocrTimers.push(fn);}, clearInterval(){}, getUnicodeStr:s=>s, console,
  alert:s=>{throw new Error(s)}
};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync('app/src/main/assets/duobao_bridge.js','utf8'),ctx);
assert.equal(ctx.Module.preRun.length,1);
ctx.Module.preRun[0]();
assert.equal(dependencies.size,0,'all preloads completed');
files.set('/mythroad/slot0.sav',new Uint8Array([11,12,13]));
assert.equal(ctx.window.duobaoFlush(),true);
const save=JSON.parse(savedJSON);
assert.equal(save.files['slot0.sav'],'CwwN');
assert(!save.files['dsm_gm.mrp'],'base ROM should not be persisted');
textArea.value='——剧终——';
ctx.window.js_textCreate(0,'——剧终——',0);
ctx.window.js_dialogRefresh(0,'标题','虽然制止了敖蛟的阴谋');
assert.deepEqual(observed.slice(0,3),[['text',1,'——剧终——'],['text',1,'——剧终——'],['text',1,'虽然制止了敖蛟的阴谋']]);
ctx.Module.postRun[0]();
assert.equal(probeTimers.length,1,'visual smoke probe started');
assert.equal(ocrTimers.length,1,'direct canvas OCR source started');
ocrTimers[0]();
assert(observed.some(row=>row[0]==='ocr_png' && row[1]===1 && row[2].startsWith('data:image/png;base64,')), 'no direct canvas PNG');
probeTimers[0](); probeTimers[0]();
assert(observed.find(([kind,c])=>kind==='canvas' && c===1),'Canvas pixel change reported');
ctx.Module._c_event(1,20,0);
assert.equal(observed[3][0],'wasm_initialized');
assert(observed.some(row=>row[0]==='confirm'));
ctx.window.duobaoFlushAndSwitch();
assert(observed.some(row=>row[0]==='saved'));
console.log('Bridge mock: file preload, save exclusion, dialogue callback, visual frame change, input callback, and switch flush PASS (simulation only)');
