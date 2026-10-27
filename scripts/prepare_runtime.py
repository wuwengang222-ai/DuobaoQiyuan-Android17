#!/usr/bin/env python3
"""Fetch VMRP from its upstream source/hosted build and package offline assets.

Requires internet. Deliberately fails CLOSED on absent emulator .wasm; never builds
an APK that contains just a pretend game-selection screen.

VMRP is GPLv3 (github.com/vmrp/vmrp); this project includes the upstream
repository's license and source provenance. Bundled third-party game assets
remain supplied by the game owner for private testing.
"""
from __future__ import annotations
import hashlib
from pathlib import Path
from urllib.request import Request, urlopen

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets'
source_base = 'https://raw.githubusercontent.com/vmrp/vmrp/master/'
published = 'https://vmrp.github.io/vmrp_v1.0/'


def fetch(url: str) -> bytes:
    request = Request(url, headers={'User-Agent': 'DuobaoDuologyBuilder/0.3'})
    with urlopen(request, timeout=45) as response:
        data = response.read()
    if not data:
        raise RuntimeError(f'Empty download: {url}')
    return data


def write(rel: str, urls: list[str], minimum: int = 1) -> None:
    last = None
    for url in urls:
        try:
            content = fetch(url)
            if len(content) < minimum:
                raise ValueError(f'Content too small at {url}: {len(content)}')
            if rel.endswith('.wasm') and not content.startswith(b'\x00asm'):
                raise ValueError('Not a WebAssembly module')
            if rel.endswith('.js') and content.lstrip().startswith(b'<!doctype'):
                raise ValueError('HTML instead of JS')
            target = assets / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            print(rel, len(content), hashlib.sha256(content).hexdigest(), url)
            return
        except Exception as ex:
            last = ex
            print('retry failed', url, str(ex))
    raise RuntimeError(f'Cannot obtain {rel}. Last error: {last}')


def main():
    # Published VMRP web build is the executable engine; upstream GitHub repo
    # contains source, support files, and compiler inputs, but no checked-in
    # source under the same filename for vmrp.wasm on master.
    write('vmrp.js', [published + 'vmrp.js'], minimum=10000)
    write('vmrp.wasm', [published + 'vmrp.wasm'], minimum=10000)
    write('midi.js', [source_base + 'wasm/dist/midi.js'], minimum=10000)
    write('VMRP_LICENSE.txt', [source_base + 'LICENSE'], minimum=1000)
    for item in ['cfunction.ext', 'mythroad/dsm_gm.mrp', 'mythroad/mpc.mrp',
                 'mythroad/ydqtwo.mrp', 'mythroad/plugins/netpay.mrp',
                 'mythroad/plugins/flaengine.mrp',
                 'mythroad/plugins/ose/brwcore.mrp',
                 'mythroad/system/gb12.uc2',
                 'mythroad/system/gb16.uc2']:
        write('fs/' + item, [source_base + 'wasm/dist/fs/' + item])

    # Reuse the upstream front-end so its JS imports, SDL key routing,
    # unicode conversion, and sound bridge remain available. Inject only
    # project-owned bridge before the compiled VMRP engine starts.
    html = fetch(source_base + 'wasm/dist/main.html').decode('utf-8-sig')
    original = '<script src="./fs.js"></script>'
    if html.count(original) != 1:
        raise RuntimeError('Upstream VMRP HTML loader layout changed, refuse unsafe injection')
    # Keep the original emulator bootstrap and all of its SDL/keyboard bindings.
    # The replacement bridge provides the same FS.preRun preload functionality
    # as fs.js, plus chapter-specific state. Loading BOTH would race/overwrite
    # game1's entry MRP and invalidate chapter-specific saves.
    if 'var Module = {' not in html or 'function js_dialogCreate(' not in html:
        raise RuntimeError('Upstream VMRP HTML API changed, runtime hook must be reviewed')
    html = html.replace(original, '<script src="./duobao_bridge.js"></script>')
    if html.index('var Module = {') > html.index('src="./duobao_bridge.js"'):
        raise RuntimeError('Duobao bridge would load before Module initialization')
    html = html.replace('<title></title>', '<title>夺宝奇缘1+2 双部曲</title>')
    html = html.replace('Downloading...', '正在加载夺宝奇缘双部曲...')
    html = html.replace('</head>', '''<style>
      body {background:#0a1020;color:#f6e5bd;padding:0 5px;}
      #main {max-width:450px}
      #canvas {image-rendering:pixelated;max-width:100vw;width:min(94vw,390px);height:auto;left:auto;margin-left:auto;margin-right:auto;}
      #dialog {width:min(94vw,390px);height:auto;aspect-ratio:3/4;left:50%;transform:translateX(-50%);margin-left:0;}
      #keymap {margin:10px auto;}
      #keymap button{font-size:1.1em;color:#1d2739;background:#ead8ac;border-radius:8px;border:0}
      #status {color:#e3c78a;}
      </style></head>''')
    # Integrate responsive classic feature-phone layout. Keep every original
    # VMRP button node and its JS callbacks; JS moves the nodes after the
    # upstream keyboard listener is registered. Both sources live locally.
    phone_css = (assets / 'duobao_phone_ui.css').read_text(encoding='utf-8')
    phone_js = (assets / 'duobao_phone_ui.js').read_text(encoding='utf-8')
    if not phone_css.strip() or not phone_js.strip():
        raise RuntimeError('Missing classic feature-phone UI source')
    if '</style>' in phone_css.lower() or '</script>' in phone_js.lower():
        raise RuntimeError('Unsafe closing HTML tag in phone UI bundle')
    html = html.replace('</head>', '<style id="duobao-phone-style">\\n'
                        + phone_css + '\\n</style></head>', 1)
    insertion_point = '<script src="./duobao_bridge.js"></script>'
    if html.count(insertion_point) != 1:
        raise RuntimeError('VMRP HTML bridge location unexpectedly changed')
    html = html.replace(insertion_point,
                        '<script id="duobao-phone-ui">\\n' + phone_js
                        + '\\n</script>\\n' + insertion_point, 1)
    # Preserve the upstream keymap DOM and ID fields, but simplify the original
    # donation/help text. It is not required to run the game.
    index = assets / 'index.html'
    index.write_text(html, encoding='utf-8')
    print('Generated', index, 'from VMRP upstream HTML')
    # Reproducible audit metadata. This is NOT a game-running test.
    import json
    important = ['index.html','vmrp.js','vmrp.wasm','duobao_bridge.js',
                 'midi.js','VMRP_LICENSE.txt','duobao_phone_ui.css','duobao_phone_ui.js',
                 'games/part1.mrp','games/part2.mrp']
    manifest = {name: {'bytes':(assets/name).stat().st_size,
                       'sha256':hashlib.sha256((assets/name).read_bytes()).hexdigest()}
                for name in important}
    (assets/'integrity-manifest.json').write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Integrity manifest generated')
    print('Fully local WebView assets ready; still requires on-device testing.')

if __name__ == '__main__': main()
