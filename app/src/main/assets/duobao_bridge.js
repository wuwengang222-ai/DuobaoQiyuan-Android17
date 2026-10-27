/* Offline VMRP bridge. This does not by itself prove that graphical RPG text
   is captured. Only VMRP dialog/text callbacks can currently be observed. */
(function () {
  'use strict';
  const params = new URLSearchParams(window.location.search);
  const chapter = Number(params.get('chapter')) === 2 ? 2 : 1;
  const native = window.DuobaoNative;
  const baseline = [
    'dsm_gm.mrp','mpc.mrp','ydqtwo.mrp','plugins/netpay.mrp',
    'plugins/flaengine.mrp','plugins/ose/brwcore.mrp',
    'system/gb12.uc2','system/gb16.uc2'
  ];
  const baselineSet = new Set(baseline);
  const maxSnapshotBytes = 2_000_000;
  const maxRestoreEntries = 1000;
  let fsReady = false;
  let pending = false;
  let switching = false;
  let runtimeAssetError = null;
  const dir = '/mythroad';
  const chName = 'part' + chapter + '.mrp';

  function mkdirp(path) {
    const parts = path.split('/').filter(Boolean);
    let full = '';
    for (const part of parts) {
      full += '/' + part;
      try { FS.mkdir(full); } catch(e) { if (!FS.analyzePath(full).exists) throw e; }
    }
  }
  function restore() {
    if (!native) return;
    let state;
    try { state = JSON.parse(native.loadSaveState(chapter)); }
    catch (e) { throw Error('存档JSON损坏: ' + e); }
    const entries = Object.entries((state && state.files) || {});
    if (entries.length > maxRestoreEntries) throw Error('存档记录数量过多');
    let restoredBytes = 0;
    for (const [name, value] of entries) {
      if (typeof name !== 'string' || /(^\/|\.\.|\\|\x00)/.test(name) || baselineSet.has(name)) continue;
      if (typeof value !== 'string' || value.length > maxSnapshotBytes * 2) continue;
      const p = dir + '/' + name;
      mkdirp(p.slice(0, p.lastIndexOf('/')));
      const raw = atob(value);
      restoredBytes += raw.length;
      if (restoredBytes > maxSnapshotBytes) throw Error('存档大小超过安全限制');
      const bytes = new Uint8Array(raw.length);
      for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
      FS.writeFile(p, bytes);
    }
  }
  function snapshot() {
    if (!fsReady) return null;
    const files = {};
    let total = 0;
    let count = 0;
    function walk(folder, prefix) {
      for (const name of FS.readdir(folder)) {
        if (name === '.' || name === '..') continue;
        const rel = prefix ? (prefix + '/' + name) : name;
        if (baselineSet.has(rel)) continue;
        const path = folder + '/' + name;
        const node = FS.lookupPath(path).node;
        if (FS.isDir(node.mode)) { walk(path, rel); continue; }
        if (!FS.isFile(node.mode)) continue;
        const bytes = FS.readFile(path);
        total += bytes.length;
        if (++count > 1000 || total > maxSnapshotBytes) throw Error('存档过大，已停止覆盖已有存档');
        let binary = '';
        // avoid String.fromCharCode(...bytes) range error on large arrays
        for (let i = 0; i < bytes.length; i += 16384) {
          binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 16384));
        }
        files[rel] = btoa(binary);
      }
    }
    walk(dir, '');
    return JSON.stringify({ files: files, chapter: chapter, format: 1 });
  }
  window.duobaoFlush = function () {
    if (!native || !fsReady || pending) return false;
    pending = true;
    try {
      const payload = snapshot();
      return payload && !!native.persistSaveState(chapter, payload);
    } catch (error) {
      console.error('Failed to save:', error);
      return false;
    } finally { pending = false; }
  };
  window.duobaoFlushAndSwitch = function () {
    if (switching) return;
    switching = true;
    const success = window.duobaoFlush();
    native.onChapterSaveFlushed(chapter, success);
    if (!success) switching = false;
  };

  // On preRun, load all required engine files, then restore any native-saved
  // changes BEFORE the MRP is executed. The dependency controls that order.
  Module.preRun = Module.preRun || [];
  Module.preRun.push(function () {
    const allFiles = [
      ['/cfunction.ext', './fs/cfunction.ext'],
      ...baseline.map(s => [dir + '/' + s, './fs/mythroad/' + s])
        .filter(([path]) => !path.endsWith('/dsm_gm.mrp')),
      [dir + '/dsm_gm.mrp', './games/' + chName]
    ];
    mkdirp(dir);
    mkdirp(dir + '/plugins/ose');
    mkdirp(dir + '/system');
    addRunDependency('duobao-fs-restore');
    let remain = allFiles.length;
    let failed = false;
    let released = false;
    const release = () => { if (!released) { released = true; removeRunDependency('duobao-fs-restore'); } };
    const done = () => {
      if (--remain !== 0 || failed) return;
      try {
        restore();
        fsReady = true;
        release();
        setInterval(() => window.duobaoFlush(), 20000);
      } catch (e) {
        failed = true;
        runtimeAssetError = '无法恢复存档: ' + String(e);
        console.error(runtimeAssetError);
        if (native && native.onRuntimeFailure) native.onRuntimeFailure(chapter, runtimeAssetError);
        release();
      }
    };
    for (const [destination, url] of allFiles) {
      const split = destination.lastIndexOf('/');
      const folder = destination.slice(0, split) || '/';
      const name = destination.slice(split + 1);
      FS.createPreloadedFile(folder, name, url, true, true, done, function (err) {
        failed = true;
        runtimeAssetError = '缺少游戏运行文件：' + url;
        console.error(runtimeAssetError, err);
        if (native && native.onRuntimeFailure) native.onRuntimeFailure(chapter, runtimeAssetError);
        release();
      });
    }
  });

  const showText = function (content) {
    if (chapter !== 1 || !native || typeof content !== 'string') return;
    native.onDisplayedText(1, content);
  };
  // Extra runtime observation: the upstream VMRP UI always sets the DOM
  // textarea's .value when it shows an OS dialogue. Observing the setter
  // catches that visible path even when the JS import binding is cached in
  // compiled vmrp.js and bypasses our window.js_text* wrappers.
  // Does NOT capture text the RPG draws directly to its framebuffer.
  const textArea = document.querySelector('#dialog textarea');
  const textareaDesc = typeof HTMLTextAreaElement === 'undefined' ? null :
      Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value');
  if (textArea && textareaDesc && textareaDesc.get && textareaDesc.set) {
    Object.defineProperty(textArea, 'value', {
      configurable: true,
      enumerable: textareaDesc.enumerable,
      get: () => textareaDesc.get.call(textArea),
      set: value => {
        textareaDesc.set.call(textArea, value);
        showText(String(value));
      }
    });
  }
  // Observable OS-level dialogs. Graphics text remains unhooked until a real
  // Unicorn/Mythroad text renderer hook is implemented in the engine.
  for (const name of ['js_textCreate','js_textRefresh','js_dialogCreate','js_dialogRefresh']) {
    const original = window[name];
    if (typeof original !== 'function') continue;
    window[name] = function () {
      try { showText(getUnicodeStr(arguments[name.includes('Refresh') ? 2 : 1])); } catch (e) { console.error(e); }
      return original.apply(this, arguments);
    };
  }


  // Performance-light optical smoke check. Observe whether the VMRP canvas
  // actually changes, without asserting any dialog/ending text was recognized.
  // Cannot by itself distinguish title animation from playable game scenes.
  function startFrameProbe() {
    if (typeof document.getElementById !== 'function') return;
    const canvas = document.getElementById('canvas');
    if (!canvas || !native || typeof native.onCanvasActivityDetected !== 'function') return;
    const checker = document.createElement('canvas');
    checker.width = 24; checker.height = 32;
    const ctx = checker.getContext('2d', {willReadFrequently: true});
    if (!ctx) return;
    let previous = null, samples = 0;
    const timer = setInterval(function () {
      if (samples++ > 24) { clearInterval(timer); return; }
      try {
        ctx.drawImage(canvas, 0, 0, checker.width, checker.height);
        const a = ctx.getImageData(0, 0, 24, 32).data;
        let checksum = 2166136261;
        for (let i = 0; i < a.length; i += 4) {
          checksum = Math.imul((checksum ^ a[i]) >>> 0, 16777619);
          checksum = Math.imul((checksum ^ a[i + 1]) >>> 0, 16777619);
          checksum = Math.imul((checksum ^ a[i + 2]) >>> 0, 16777619);
        }
        if (previous !== null && previous !== checksum) {
          clearInterval(timer);
          native.onCanvasActivityDetected(chapter);
        }
        previous = checksum;
      } catch (e) {
        console.warn('Canvas visual probe unavailable:', e);
        clearInterval(timer);
      }
    }, 800);
  }

  // Prefer OCR on exact emulator framebuffer pixels rather than WebView.draw,
  // which can return a blank image for a GPU-accelerated WebView surface.
  // Capture is local only; no network permission or upload is required.
  function startDirectCanvasOcr() {
    if (chapter !== 1 || !native || typeof native.onGameCanvasPNG !== 'function') return;
    const screen = document.getElementById('canvas');
    if (!screen) return;
    const scratch = document.createElement('canvas');
    scratch.width = 960;  // 4x upscaling of the original 240x320 canvas
    scratch.height = 1280;
    const ctx = scratch.getContext('2d', {willReadFrequently:true});
    if (!ctx || !scratch.toDataURL) return;
    ctx.imageSmoothingEnabled = false; // preserve original pixel font strokes
    let last = null;
    setInterval(function () {
      try {
        if (!document.visibilityState || document.visibilityState === 'visible') {
          ctx.clearRect(0, 0, scratch.width, scratch.height);
          ctx.drawImage(screen, 0, 0, scratch.width, scratch.height);
          const encoded = scratch.toDataURL('image/png');
          if (encoded && encoded.length > 32 && encoded !== last && encoded.length < 3000000) {
            native.onGameCanvasPNG(chapter, encoded);
            last = encoded;
          }
        }
      } catch (e) { console.warn('Direct game canvas capture unsupported', e); }
    }, 1700);
  }

  function wrapEvent() {
    if (!Module || typeof Module._c_event !== 'function' || Module._c_event.__duobaoWrapped) return;
    const old = Module._c_event;
    const hooked = function () {
      const result = old.apply(this, arguments);
      const eventType = Number(arguments[0]), keyCode = Number(arguments[1]);
      if (chapter === 1 && native && ((eventType === 1 && (keyCode === 20 || keyCode === 17)) || (eventType === 6 && keyCode === 0))) {
        native.onContinueInput(1);
      }
      return result;
    };
    hooked.__duobaoWrapped = true;
    Module._c_event = hooked;
  }
  Module.postRun = Module.postRun || [];
  Module.postRun.push(function () {
    // Emscripten also executes postRun after a failed preload if we release
    // the dependency to avoid hanging. Never report such a state as a launch.
    if (runtimeAssetError || !fsReady) {
      const reason = runtimeAssetError || 'VMRP 文件系统未初始化';
      if (native && native.onRuntimeFailure) native.onRuntimeFailure(chapter, reason);
      console.error('Duobao: blocking false-positive startup:', reason);
      return;
    }
    wrapEvent();
    startFrameProbe();
    startDirectCanvasOcr();
    if (native && typeof native.onEmulatorInitialized === 'function')
      native.onEmulatorInitialized(chapter);
  });
  // Pressing hardware enter is mapped by upstream SDL instead of _c_event.
  document.addEventListener('keyup', e => {
    if (chapter !== 1 || !native) return;
    if (e.key === 'Enter') native.onContinueInput(1);
  });

  // Avoid claiming automatic completion until a renderer callback has been
  // validated against a real end-to-end playthrough.
  console.info('Duobao: emulator setup', chapter);
})();
