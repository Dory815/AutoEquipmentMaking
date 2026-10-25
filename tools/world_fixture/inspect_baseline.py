"""Read the arranged baseline's region files; never open the world for play."""
import json, zlib
from pathlib import Path
from inspect_nbt import read_bytes

root = Path(__file__).resolve().parents[2]
world = root / 'runtime/test-baselines/201-trading-hall-user-arranged-20261025/world'
blocks, containers = {}, []
for f in (world / 'dimensions/minecraft/the_end/region').glob('*.mca'):
    b = f.read_bytes()
    for i in range(1024):
        off = int.from_bytes(b[i*4:i*4+3], 'big')*4096
        if not off: continue
        size = int.from_bytes(b[off:off+4], 'big')
        d = read_bytes(zlib.decompress(b[off+5:off+4+size]))
        cx, cz = d['xPos'], d['zPos']
        if not (65 <= cx <= 68 and 0 <= cz <= 2): continue
        for section in d.get('sections', []):
            sy = section['Y']; bs = section.get('block_states', {})
            palette = bs.get('palette', []); data = bs.get('data', [])
            if not palette or sy not in (3,4): continue
            bits = max(4, (len(palette)-1).bit_length()); per = 64//bits
            for n in range(4096):
                index = ((data[n//per] & ((1<<64)-1)) >> ((n%per)*bits)) & ((1<<bits)-1) if data else 0
                x, y, z = cx*16+n%16, sy*16+n//256, cz*16+(n//16)%16
                if 1054<=x<=1095 and 55<=y<=68 and 8<=z<=36:
                    blocks[f'{x},{y},{z}'] = palette[index]
        for t in d.get('block_entities', []):
            if 'Items' in t:
                containers.append({'pos':[t['x'],t['y'],t['z']], 'id':t['id'], 'items':t['Items']})
out = root / '.tools/201-layout.json'
out.write_text(json.dumps({'blocks':blocks,'containers':containers},default=list),encoding='utf-8')
for c in containers:
    p=c['pos']; state=blocks.get(','.join(map(str,p)),{})
    if not state: continue
    ids={i['id'] for i in c['items']}
    if 'minecraft:diamond' in ids or not c['items'] or 'minecraft:elytra' in ids:
        print(p,state.get('Name'),len(c['items']), sorted(ids))
print('Raw box surroundings:')
for y in range(56,65):
    print(y, [(k,v) for k,v in blocks.items() if k.split(',')[1]==str(y) and 1086<=int(k.split(',')[0])<=1090 and 14<=int(k.split(',')[2])<=18 and v['Name']!='minecraft:air'])
