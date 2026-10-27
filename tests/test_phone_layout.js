/* No browser required: check the actual DOM reorderer with stubbed VMRP key nodes. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const code=fs.readFileSync('app/src/main/assets/duobao_phone_ui.js','utf8');
const css=fs.readFileSync('app/src/main/assets/duobao_phone_ui.css','utf8');
assert.match(css,/#duobao-navigation/);
assert.match(css,/#duobao-numbers/);
assert.match(css,/100dvh/);
const ids=['key_sleft','key_up','key_sright','key_left','key_ok','key_right','key_down',
'key_1','key_2','key_3','key_4','key_5','key_6','key_7','key_8','key_9','key_star','key_0','key_pound'];
function node(id=''){
 const n={id,children:[],attributes:{},style:{},dataset:{},textContent:'',clientWidth:390,clientHeight:550,
 classList:{classes:new Set(),add(x){this.classes.add(x)},toggle(x){const enabled=!this.classes.has(x);if(enabled)this.classes.add(x);else this.classes.delete(x);return enabled}},
 appendChild(x){this.children.push(x);return x},
 replaceChildren(...xs){this.children=xs},
 setAttribute(k,v){this.attributes[k]=v},
 addEventListener(k,fn){this['on_'+k]=fn}};
 return n;
}
const elements=Object.fromEntries([...ids,'main','keymap','screen','canvas','dialog'].map(x=>[x,node(x)]));
for(const id of ids)elements[id].onmousedown=()=>id;
elements.canvas.width=240;elements.canvas.height=320;
const document={getElementById:id=>elements[id]||null,createElement:t=>node(t)};
const context={document,window:{addEventListener(){}},console:{info(){},error(x){throw Error(x)}},
 requestAnimationFrame:fn=>fn(),ResizeObserver:class{constructor(fn){this.fn=fn}observe(){}},
 MutationObserver:class{observe(){}} ,setTimeout:fn=>fn()};
vm.runInNewContext(code,context);
const nav=elements.keymap.children[0],nums=elements.keymap.children[1];
assert.equal(nav.id,'duobao-navigation');
assert.equal(nums.id,'duobao-numbers');
assert.deepEqual(nav.children.map(x=>x.id||''),['key_sleft','key_up','key_sright','key_left','key_ok','key_right','span','key_down','span']);
assert.deepEqual(nums.children.map(x=>x.id),['key_1','key_2','key_3','key_4','key_5','key_6','key_7','key_8','key_9','key_star','key_0','key_pound']);
assert.equal(elements.key_ok.textContent,'OK');
assert.equal(elements.key_up.onmousedown(),'key_up','Reparenting must not remove original emulator handlers');
assert.equal(elements.keymap.children.length,2);
const toggle=elements.main.children.find(x=>x.id==='duobao-screen-toggle');
assert.ok(toggle);
toggle.on_click();
assert.equal(toggle.textContent,'⌨ 按键');
toggle.on_click();
assert.equal(toggle.textContent,'⛶ 放大');
assert.equal(elements.main.dataset.duobaoPhoneKeys,'ready');
const computed=Number.parseInt(elements.canvas.style.width);
assert.ok(computed>200&&computed<=390, 'Canvas should enlarge within available display bounds');
console.log('PASS: phone D-pad above 3x4 numpad, keycode handlers retained, fullscreen toggle and scaled canvas');
