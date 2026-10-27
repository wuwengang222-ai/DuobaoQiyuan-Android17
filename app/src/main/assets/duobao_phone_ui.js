/* Classic feature phone UI: MOVE existing buttons, preserve VMRP key handlers. */
(function(){
"use strict";
const main=document.getElementById('main'), map=document.getElementById('keymap');
const screen=document.getElementById('screen'), canvas=document.getElementById('canvas');
const dialog=document.getElementById('dialog');
if(!main||!map||!screen||!canvas||!dialog||main.dataset.duobaoPhoneKeys)return;
const all=['key_sleft','key_up','key_sright','key_left','key_ok','key_right','key_down',
'key_1','key_2','key_3','key_4','key_5','key_6','key_7','key_8','key_9','key_star','key_0','key_pound'];
if(all.some(id=>!document.getElementById(id))){console.error('Missing upstream VMRP keys');return;}
const n=document.createElement('div'), d=document.createElement('div');
n.id='duobao-navigation';d.id='duobao-numbers';
n.setAttribute('aria-label','方向键和功能键');d.setAttribute('aria-label','数字键');
for(const id of ['key_sleft','key_up','key_sright','key_left','key_ok','key_right',null,'key_down',null]){
 if(!id){const span=document.createElement('span');span.setAttribute('aria-hidden','true');n.appendChild(span);continue;}
 const key=document.getElementById(id);
 // appendChild preserves original VMRP onmouse/onTouch listeners and keycodes.
 n.appendChild(key);
 if(id==='key_ok')key.textContent='OK';
 if(id==='key_sleft'||id==='key_sright'){key.textContent=id==='key_sleft'?'菜单':'返回';key.classList.add('duobao-soft');}
 else if(id==='key_up'||id==='key_down'||id==='key_left'||id==='key_right')key.classList.add('duobao-direction');
}
for(const id of ['key_1','key_2','key_3','key_4','key_5','key_6','key_7','key_8','key_9','key_star','key_0','key_pound'])d.appendChild(document.getElementById(id));
map.replaceChildren(n,d);
const toggle=document.createElement('button');
toggle.id='duobao-screen-toggle';toggle.type='button';toggle.textContent='⛶ 放大';
toggle.setAttribute('aria-label','最大化游戏画面，再次点击显示按键');
toggle.addEventListener('click',()=>{
 const big=main.classList.toggle('duobao-max-screen');
 toggle.textContent=big?'⌨ 按键':'⛶ 放大';
 requestAnimationFrame(resize);
});
main.appendChild(toggle);
function resize(){
 const w=Math.max(1,screen.clientWidth),h=Math.max(1,screen.clientHeight-26);
 const iw=Number(canvas.width)||240,ih=Number(canvas.height)||320;
 if(!(iw>0&&ih>0))return;
 const k=Math.max(.1,Math.min(w/iw,h/ih));
 const pw=Math.max(1,Math.floor(iw*k)),ph=Math.max(1,Math.floor(ih*k));
 canvas.style.width=pw+'px';canvas.style.height=ph+'px';
 dialog.style.width=pw+'px';dialog.style.height=ph+'px';
}
if(typeof ResizeObserver!=='undefined')new ResizeObserver(resize).observe(screen);
if(typeof MutationObserver!=='undefined')new MutationObserver(resize).observe(canvas,{attributes:true,attributeFilter:['width','height']});
window.addEventListener('resize',resize);
window.addEventListener('orientationchange',()=>setTimeout(resize,150));
resize();
main.dataset.duobaoPhoneKeys='ready';
console.info('Duobao: feature-phone directional keys above 3x4 numeric keypad');
})();
