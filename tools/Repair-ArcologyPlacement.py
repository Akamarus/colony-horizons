"""Create a separate development pack; never edit the source pack or a live world."""
import argparse, json, shutil
from pathlib import Path
from arcology_nbt import load, save, block, set_block

def repair(source, output):
    if output.exists():
        raise ValueError('Output must be new: '+str(output))
    shutil.copytree(source, output)
    path=output/'decorations/supplies/supplyship.blueprint'
    data,schema,name=load(path)
    # BlueprintTagUtils reads tags from the primary anchor's tile data.
    # Use an existing supply barrel; no blocks or contents need changing.
    anchor=next(t for t in data['tile_entities'] if (t['x'],t['y'],t['z'])==(1,0,1))
    assert anchor['id']=='minecraft:barrel'
    data['optional_data']['structurize']['primary_offset']={'x':1,'y':0,'z':1}
    anchor['blueprintDataProvider']={'posTagMap':[{'tagPos':{'x':0,'y':0,'z':0},'tagNameList':[{'tagName':'groundlevel'}]}]}
    save(path,data,schema,name)
    # These two edge pockets have floor support, so Recall accepts them, but
    # the hut and spiral stair prevent a route back into an elevated module.
    # Rails make the pockets ineligible without removing any stair treads.
    for tier in range(1,6):
        path=output/f'fundamentals/builder{tier}.blueprint'
        data,schema,name=load(path)
        for pos in ((0,1,1),(1,1,0)):
            assert block(data,pos)=={'Name':'minecraft:air'}, (path,pos)
            set_block(data,pos,{'Name':'minecraft:spruce_fence','Properties':{'east':'false','west':'false','north':'false','south':'false','waterlogged':'false'}})
        save(path,data,schema,name)
    changes=[]
    for f in output.rglob('*'):
        if f.is_file() and f.read_bytes()!=(source/f.relative_to(output)).read_bytes():
            changes.append(str(f.relative_to(output)))
    print(json.dumps({'output':str(output),'changed':changes},indent=2))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('output',type=Path)
    args=p.parse_args();repair(args.source,args.output)
