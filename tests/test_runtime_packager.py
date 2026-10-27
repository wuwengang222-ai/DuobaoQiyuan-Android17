#!/usr/bin/env python3
"""Offline assembly smoke tests, never claims to execute VMRP.

The synthetic WASM here is never included in app assets or deliverables.
Its only role is exercising the package-building and fail-closed guards.
"""
import importlib.util, json, tempfile, pathlib, shutil
from unittest.mock import patch
root = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('duobao_prepare', root/'scripts/prepare_runtime.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)
html = ('''<!DOCTYPE html><html><head><title></title></head><body>
<div id="status">Downloading...</div>
<script>function js_dialogCreate(title, text, type) {return 1;}\nvar Module = {preRun:[], postRun: []};</script>
<script src="./fs.js"></script>\n<script async src="./vmrp.js"></script></body></html>''').encode()


def fake_fetch(url):
    if url.endswith('/main.html'): return html
    if url.endswith('/vmrp.wasm'): return b'\0asm' + b'1'*11000
    if url.endswith('/midi.js') or url.endswith('/vmrp.js'): return b'var abc = 123;\n' * 1000
    if url.endswith('/LICENSE'): return b'GPL-3.0\n' * 150
    return b'fixture runtime entry'

with tempfile.TemporaryDirectory(prefix='duobao_package_test_') as td:
    p.assets = pathlib.Path(td)
    (p.assets/'games').mkdir()
    (p.assets/'games/part1.mrp').write_bytes((root/'app/src/main/assets/games/part1.mrp').read_bytes())
    (p.assets/'games/part2.mrp').write_bytes((root/'app/src/main/assets/games/part2.mrp').read_bytes())
    (p.assets/'duobao_bridge.js').write_bytes((root/'app/src/main/assets/duobao_bridge.js').read_bytes())
    # The runtime packager now inlines the user-interface source into HTML;
    # temp assets must include both files just like a genuine source checkout.
    for name in ('duobao_phone_ui.css', 'duobao_phone_ui.js'):
        (p.assets/name).write_bytes((root/'app/src/main/assets'/name).read_bytes())
    with patch.object(p, 'fetch', fake_fetch): p.main()
    index=(p.assets/'index.html').read_text()
    assert 'duobao_bridge.js' in index and 'src="./fs.js"' not in index
    assert 'id="duobao-phone-ui"' in index and 'id="duobao-phone-style"' in index
    assert '#duobao-navigation' in index and 'duobao-screen-toggle' in index
    assert index.index('var Module = {') < index.index('duobao_bridge.js') < index.index('src="./vmrp.js"')
    manifest=json.loads((p.assets/'integrity-manifest.json').read_text())
    assert manifest['games/part1.mrp']['bytes'] > 360000
    assert manifest['games/part2.mrp']['bytes'] > 350000
    assert all((p.assets/k).is_file() for k in manifest)
    with patch.object(p,'fetch', lambda url: html.replace(b'<script src="./fs.js"></script>',b'' ) if url.endswith('/main.html') else fake_fetch(url)):
        try: p.main()
        except RuntimeError as e: assert 'layout changed' in str(e)
        else: raise AssertionError('Must reject unexpected upstream HTML loader')
print('PASS: offline packager HTML injection, bootstrap order, manifest and fail-closed guards (synthetic assets only)')
