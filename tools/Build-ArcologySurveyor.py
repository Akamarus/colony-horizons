"""Create a separate Arcology candidate with a native Surveyor module at levels 1–5."""
import argparse, copy, shutil
from pathlib import Path
from arcology_nbt import load, save

p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
source=a.source.resolve();output=a.output.resolve()
assert source.is_dir() and source!=output and source not in output.parents and output not in source.parents
assert output.is_relative_to(Path(__file__).resolve().parents[1]/'build'), 'Only isolated build output may be changed'
shutil.copytree(source,output,dirs_exist_ok=True)
for level in range(1,6):
    n,template,name=load(source/'fundamentals'/f'builder{level}.blueprint')
    n=copy.deepcopy(n);n['name']=f'surveyor{level}.blueprint'
    n['required_mods']=list(dict.fromkeys(n.get('required_mods',[])+['colonyhorizons']))
    for state in n['palette']:
        if state['Name']=='minecolonies:blockhutbuilder':state['Name']='colonyhorizons:blockhutsurveyor'
    huts=[t for t in n['tile_entities'] if t.get('id')=='minecolonies:colonybuilding']
    assert len(huts)==1 and huts[0]['type']=='minecolonies:builder'
    hut=huts[0];hut['type']='colonyhorizons:surveyor';hut['blueprintDataProvider']['schematicName']=f'surveyor{level}'
    assert hut['colony']==0 and all(v.get('empty')==1 for v in hut['inventory'])
    save(output/'fundamentals'/f'surveyor{level}.blueprint',n,template,name)
print('Created isolated Surveyor pack:',output)
