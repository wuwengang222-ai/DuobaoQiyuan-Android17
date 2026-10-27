#!/usr/bin/env python3
"""Check the real end-scene UTF-16BE bytes inside the ORIGINAL user MRP1.
Standalone stdlib Python 3. No emulator, no false claims of gameplay execution.
"""
import gzip, hashlib, struct, sys
from pathlib import Path

wanted = {
    "——剧终——": 0x1494,
    "虽然制止了敖蛟的阴谋": 0x14a2,
    "如今逃往了妖之国": 0x14e2,
    "又将会引发什么变故": 0x14f4,
}
def read_mrp(file):
    data = Path(file).read_bytes()
    if data[:4] != b"MRPG": raise ValueError("not an MRP archive")
    p = 240
    while p+20<len(data):
        n = struct.unpack_from('<I', data, p)[0]
        if not 1<=n<=64: break
        name = data[p+4:p+4+n].rstrip(b'\0').decode('ascii', errors='replace')
        ofs, size, flags = struct.unpack_from('<III',data,p+4+n)
        if ofs+size>len(data):break
        yield name, data[ofs:ofs+size]
        p+=16+n

def main(original):
    print('Original MRP SHA256:',hashlib.sha256(Path(original).read_bytes()).hexdigest())
    files=[raw for name,raw in read_mrp(original) if name=='14.map']
    if len(files)!=1: raise AssertionError(f'Expected exactly one 14.map, got {len(files)}')
    raw=files[0]
    scene=gzip.decompress(raw) if raw.startswith(b'\x1f\x8b') else raw
    for phrase,offset in wanted.items():
        actual=scene.find(phrase.encode('utf-16-be'))
        print(f'{phrase}: expected 0x{offset:X}, found 0x{actual:X}')
        if actual!=offset: raise AssertionError('Changed ending scene or wrong game file')
    print('PASS: 4 exact end-scene marker offsets verified in original MRP1')

if __name__=='__main__': main(sys.argv[1] if len(sys.argv)>1 else '/mnt/data/319011_240320_gdbqy.mrp')
