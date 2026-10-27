#!/usr/bin/env python3
"""Machine-readable preflight. Never claims unexecuted tests passed."""
import argparse, datetime, json, os, platform, shutil, subprocess, socket, ssl, urllib.request
from pathlib import Path

ap = argparse.ArgumentParser()
ap.add_argument('--output', default='build/diagnostics/preflight.json')
args = ap.parse_args()
commands = ['git','java','javac','gradle','sdkmanager','avdmanager','adb','emulator','node','python3']
result = {'timestamp_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
          'host': platform.platform(), 'commands':{k:shutil.which(k) for k in commands},
          'kvm':Path('/dev/kvm').exists(), 'android_sdk':os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'),
          'network': {}, 'checks': {}}
for url in ['https://raw.githubusercontent.com/vmrp/vmrp/master/wasm/dist/main.html',
            'https://vmrp.github.io/vmrp_v1.0/main.html']:
    try:
        req=urllib.request.Request(url,headers={'User-Agent':'DuobaoV8-preflight/1.0'})
        with urllib.request.urlopen(req,timeout=5) as response:
            payload=response.read(128)
            result['network'][url]={'reachable':bool(payload),'status':response.status}
    except Exception as exc:
        result['network'][url]={'reachable':False,'error':str(exc)}
result['checks']['network_available']=all(x.get('reachable',False) for x in result['network'].values())
result['checks']['android_build_tools_present']=bool(result['commands']['gradle'] and result['commands']['sdkmanager'] and result['commands']['adb'])
result['checks']['android_emulator_present']=bool(result['commands']['emulator'])
result['checks']['hardware_acceleration_available']=result['kvm']
result['checks']['real_vmrp_wasm_present']=Path('app/src/main/assets/vmrp.wasm').exists()
out=Path(args.output);out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
print(json.dumps(result,indent=2,ensure_ascii=False))
