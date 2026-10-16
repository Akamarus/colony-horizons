"""Independent asset preservation checks, complementing native GameTests."""
import sys, hashlib
from pathlib import Path
from arcology_nbt import load, block
original,candidate=map(Path,sys.argv[1:])
files={p.relative_to(original) for p in original.rglob('*') if p.is_file()}
assert files=={p.relative_to(candidate) for p in candidate.rglob('*') if p.is_file()}
changed={str(p).replace('\\','/') for p in files if (original/p).read_bytes()!=(candidate/p).read_bytes()}
assert changed=={'decorations/supplies/supplyship.blueprint'}|{f'fundamentals/builder{i}.blueprint' for i in range(1,6)}
for tier in range(1,6):
    path=f'fundamentals/builder{tier}.blueprint'
    a,_,_=load(original/path);b,_,_=load(candidate/path)
    assert {k:v for k,v in a.items() if k not in ('blocks','palette')}=={k:v for k,v in b.items() if k not in ('blocks','palette')}
    delta={}
    for x in range(a['size_x']):
        for y in range(a['size_y']):
            for z in range(a['size_z']):
                p=(x,y,z)
                if block(a,p)!=block(b,p):delta[p]=(block(a,p),block(b,p))
    assert set(delta)=={(0,1,1),(1,1,0)}
    assert all(old=={'Name':'minecraft:air'} and new['Name']=='minecraft:spruce_fence' for old,new in delta.values())
a,_,_=load(original/'decorations/supplies/supplyship.blueprint');b,_,_=load(candidate/'decorations/supplies/supplyship.blueprint')
assert a['blocks']==b['blocks'] and a['palette']==b['palette']
assert len(a['tile_entities'])==len(b['tile_entities'])
for old,new in zip(a['tile_entities'],b['tile_entities']):
    assert old=={k:v for k,v in new.items() if k!='blueprintDataProvider'}
assert b['optional_data']['structurize']['primary_offset']=={'x':1,'y':0,'z':1}
anchor=next(t for t in b['tile_entities'] if (t['x'],t['y'],t['z'])==(1,0,1))
assert anchor['blueprintDataProvider']['posTagMap']==[{'tagPos':{'x':0,'y':0,'z':0},'tagNameList':[{'tagName':'groundlevel'}]}]
print('PASS: exactly six blueprints changed; all stairs, inventories, hut anchors, dimensions, other buildings and ship geometry preserved.')
