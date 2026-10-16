"""Generate four original, opt-in Arcology quarry assets; no native excavation claim."""
import argparse
import copy
from pathlib import Path
from arcology_nbt import Tag, load, rebuild, save, set_block, block

SPECS=(('simplequarry',15,32,7),('mediumquarry',25,64,12))
AIR={'Name':'minecraft:air'}
KEEP={'Name':'structurize:blocksubstitution'}
STONE={'Name':'minecraft:cobblestone'}
LADDER={'Name':'minecraft:ladder','Properties':{'facing':'east','waterlogged':'false'}}

def _pos(x,y,z):
    return dict(x=x,y=y,z=z)

def _tile_schema(data,typed,identity):
    for i,tile in enumerate(data['tile_entities']):
        if tile.get('id')==identity:return typed.value['tile_entities'].value[1][i]
    raise ValueError('Missing source tile schema: '+identity)

def _blank(w,height,name,primary,reference,default):
    return dict(size_x=w,size_y=height,size_z=w,name=name,version=reference['version'],
                mcversion=reference['mcversion'],required_mods=['minecolonies','structurize'],
                entities=[],blocks=[0]*((w*height*w+1)//2),palette=[copy.deepcopy(default)],
                tile_entities=[],optional_data={'structurize':{'primary_offset':_pos(*primary)}})

def _build(source,family,w,depth,anchor):
    # Mine and Builder supply serialization schemas; the residence supplies canopy styling.
    reference,schema,root_name=load(source/'fundamentals/miner1.blueprint')
    builder,builder_schema,_=load(source/'fundamentals/builder1.blueprint')
    hut_schema=_tile_schema(reference,schema,'minecolonies:colonybuilding')
    rack_schema=_tile_schema(builder,builder_schema,'minecolonies:rack')
    surface=_blank(w,7,family+'1.blueprint',(anchor,2,2),reference,AIR)
    shaft=_blank(w,depth,family+'shaft1.blueprint',(anchor,depth-2,2),reference,KEEP)
    timber=family=='simplequarry'
    frame={'Name':'minecraft:spruce_planks' if timber else 'minecraft:stone'}
    floor={'Name':'minecraft:oak_planks' if timber else 'minecraft:cobblestone'}
    roof={'Name':'minecraft:oak_slab' if timber else 'minecraft:cobblestone_slab',
          'Properties':{'type':'bottom','waterlogged':'false'}}

    for x in range(w):
        for z in range(w):
            interior=3<=x<=w-4 and 5<=z<=w-4
            liner=2<=x<=w-3 and 4<=z<=w-3 and (x in (2,w-3) or z in (4,w-3))
            set_block(surface,(x,0,z),KEEP)
            set_block(surface,(x,1,z),KEEP if interior else (frame if x%5==0 or z%5==0 else floor))
            if interior:set_block(surface,(x,0,z),KEEP)
            if liner:
                for y in (0,1):set_block(surface,(x,y,z),STONE)
                # Two-cell north landing is the only break in the safety rail.
                if not (z==4 and x in (3,4)):
                    if timber:
                        rail={'Name':'minecraft:spruce_fence','Properties':{'north':'false','east':'false','south':'false','west':'false','waterlogged':'false'}}
                    else:
                        rail={'Name':'minecraft:cobblestone_wall','Properties':{'north':'none','east':'none','south':'none','west':'none','up':'true','waterlogged':'false'}}
                    set_block(surface,(x,2,z),rail)
    # Match the original five-cell roof, at the same height above the walking floor.
    style,_,_=load(source/f'fundamentals/citizen{1 if timber else 3}.blueprint')
    for local_x in range(5):
        for z in range(5):
            x=anchor-2+local_x
            set_block(surface,(x,6,z),copy.deepcopy(block(style,(local_x,5,z))))
            if local_x in (0,4) or z in (0,4):
                for y in range(2,6):
                    state=copy.deepcopy(block(style,(local_x,y-1,z)))
                    if y<5 and not (local_x in (0,4) and z in (0,4)):state=AIR
                    set_block(surface,(x,y,z),state)
    # Continuous supported access; overlap with shaft is deliberately identical.
    for y in range(5):
        set_block(surface,(2,y,5),STONE)
        set_block(surface,(3,y,5),LADDER)

    empty_inventory=[{'empty':1} for _ in range(27)]
    tile_templates=[]
    def tile(p,data,template):
        data=copy.deepcopy(data);data.update(_pos(*p))
        surface['tile_entities'].append(data)
        tile_templates.append(rebuild(data,template))
    set_block(surface,(anchor,2,2),{'Name':'minecolonies:'+family,'Properties':{'facing':'south'}})
    tile((anchor,2,2),dict(id='minecolonies:colonybuilding',type='minecolonies:'+family,
         mirror=0,inWarehouse=0,colony=0,main=0,inventory=empty_inventory,version=0,
         pack='',path='',tagSIze=0,pos=_pos(0,0,0),
         blueprintDataProvider=dict(corner1=_pos(-anchor,-2,-2),corner2=_pos(w-1-anchor,4,w-3),
         path='',pack='',schematicName=family+'1',posTagMap=[
             {'tagPos':_pos(0,0,0),'tagNameList':[{'tagName':'shaft=infrastructure/mineshafts/'+family+'shaft1.blueprint'}]}])),hut_schema)
    for x in (anchor-2,anchor+2):
        set_block(surface,(x,2,2),{'Name':'minecolonies:blockminecoloniesrack','Properties':{'variant':'blockrackempty','facing':'south'}})
        tile((x,2,2),dict(id='minecolonies:rack',tagSIze=0,inWarehouse=0,
             pos=_pos(0,0,0),main=0,inventory=empty_inventory,version=2),rack_schema)

    for y in range(depth):
        for x in range(2,w-2):
            for z in range(4,w-2):
                liner=x in (2,w-3) or z in (4,w-3)
                set_block(shaft,(x,y,z),STONE if y==0 or liner else AIR)
        if y>0:set_block(shaft,(3,y,5),LADDER)
    for y in range(3,depth-2,6):
        set_block(shaft,(anchor,y,5),{'Name':'minecraft:wall_torch','Properties':{'facing':'south'}})
    surface_typed=rebuild(surface,schema)
    surface_typed.value['tile_entities']=Tag(9,(10,tile_templates))
    shaft_typed=rebuild(shaft,schema)
    shaft_typed.value['tile_entities']=Tag(9,(10,[]))
    return [(Path('fundamentals')/(family+'1.blueprint'),surface,surface_typed,root_name),
            (Path('infrastructure/mineshafts')/(family+'shaft1.blueprint'),shaft,shaft_typed,root_name)]

def generate(source,output):
    """Return four new paths in a separate, explicitly marked candidate directory."""
    source=Path(source).resolve();output=Path(output).resolve()
    if source==output or source in output.parents or output in source.parents:
        raise ValueError('Quarry candidate must be separate from source assets')
    marker=output/'.arcology-quarry-candidate'
    if output.exists() and not marker.is_file():
        raise ValueError('Refusing an unrecognized output directory')
    structures=[item for spec in SPECS for item in _build(source,*spec)]
    output.mkdir(parents=True,exist_ok=True)
    marker.write_text('Original quarry geometry; static candidate only. Native excavation and footprint behavior unverified.\n',encoding='utf-8')
    paths=[]
    for relative,data,typed,root_name in structures:
        path=output/relative
        save(path,data,typed,root_name);paths.append(path)
    return paths

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    for path in generate(args.source,args.output):print(path)
