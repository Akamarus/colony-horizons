"""Read-only blueprint overlap diagnostic; does not simulate builder execution."""
import argparse, json
from pathlib import Path
from collections import Counter
from arcology_nbt import load, block

p=argparse.ArgumentParser()
p.add_argument('--pack',type=Path,required=True)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
rows=[]
for old_level in range(1,5):
    old,_,_=load(a.pack/'military'/f'barracks{old_level}.blueprint')
    new,_,_=load(a.pack/'military'/f'barracks{old_level+1}.blueprint')
    huts=[t for t in old['tile_entities'] if t.get('type')=='minecolonies:barrackstower']
    for tower_level in range(1,old_level+1):
        tower,_,_=load(a.pack/'military'/f'barrackstower{tower_level}.blueprint')
        anchor=next(t for t in tower['tile_entities'] if t.get('type')=='minecolonies:barrackstower')
        original_facing=block(tower,(anchor['x'],anchor['y'],anchor['z']))['Properties']['facing']
        facings=['north','east','south','west']
        for hut in huts:
            facing=block(old,(hut['x'],hut['y'],hut['z']))['Properties']['facing']
            turns=(facings.index(facing)-facings.index(original_facing))%4
            conflicts=[]
            for x in range(tower['size_x']):
                for y in range(tower['size_y']):
                    for z in range(tower['size_z']):
                        state=block(tower,(x,y,z))
                        if state['Name'] in ('minecraft:air','minecraft:structure_void','structurize:blocksubstitution'): continue
                        dx,dz=x-anchor['x'],z-anchor['z']
                        for _ in range(turns): dx,dz=-dz,dx
                        at=(hut['x']+dx,hut['y']+y-anchor['y'],hut['z']+dz)
                        target=block(new,at)
                        if target and target['Name']=='minecraft:air':
                            conflicts.append({'position':list(at),'towerBlock':state['Name'],'parentTarget':'minecraft:air'})
            rows.append({'upgrade':f'{old_level}->{old_level+1}','towerLevel':tower_level,'anchor':[hut[k] for k in ('x','y','z')], 'airOverwriteCount':len(conflicts),'blocks':dict(Counter(v['towerBlock'] for v in conflicts)), 'conflicts':conflicts})
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_text(json.dumps({'scope':'Static blueprint air overlap, not a native timed builder test','cases':rows},indent=2))
for r in rows: print(f"Barracks {r['upgrade']}, tower L{r['towerLevel']} at {r['anchor']}: {r['airOverwriteCount']} solid tower blocks targeted as air; {r['blocks']}")
print(f'{sum(r["airOverwriteCount"]>0 for r in rows)}/{len(rows)} overlap cases contain destructive air targets')
