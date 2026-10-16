"""Opt-in original Gatehouse 1–3 geometry; no route/transport implementation.

Source structures supply NBT schemas, materials and a five-cell shell reference.
Generate returns three new paths; native guarding/terminal acceptance is pending.
"""
import argparse
import copy
from pathlib import Path
from arcology_nbt import Tag,load,rebuild,save,block,set_block


def _pos(x,y,z):return dict(x=x,y=y,z=z)


def _tile_reference(n,typed,identity):
    for index,t in enumerate(n['tile_entities']):
        if t.get('id')==identity:return t,typed.value['tile_entities'].value[1][index]
    raise ValueError('Missing metadata schema: '+identity)


def _build(source,level):
    equivalent={1:1,2:3,3:5}[level]
    reference,schema,name=load(source/f'military/archery{equivalent}.blueprint')
    _,hut_schema=_tile_reference(reference,schema,'minecolonies:colonybuilding')
    _,bed_schema=_tile_reference(reference,schema,'minecraft:bed')
    builder,builder_schema,_=load(source/'fundamentals/builder1.blueprint')
    _,rack_schema=_tile_reference(builder,builder_schema,'minecolonies:rack')
    n=dict(size_x=15,size_y=6,size_z=10,name=f'gatehouse{level}.blueprint',
           version=reference['version'],mcversion=reference['mcversion'],
           required_mods=['minecolonies']+(['domum_ornamentum'] if level==3 else []),
           entities=[],blocks=[0]*450,palette=[{'Name':'minecraft:air'}],tile_entities=[],
           optional_data={'structurize':{'primary_offset':_pos(12,1,6)}})
    typed_tiles=[]

    def tile(p,values,template):
        values=copy.deepcopy(values);values.update(_pos(*p))
        n['tile_entities'].append(values);typed_tiles.append(rebuild(values,template))

    # Match the original five-cell module, including paired seams at 4/5.
    style,style_schema,_=load(source/f'fundamentals/citizen{equivalent}.blueprint')
    roof_tiles={tuple(t[k] for k in 'xyz'):(t,style_schema.value['tile_entities'].value[1][i])
                for i,t in enumerate(style['tile_entities'])}
    for x in range(15):
        for z in range(10):
            for y in (0,5):
                rp=(x%5,y,z%5);state=copy.deepcopy(block(style,rp))
                set_block(n,(x,y,z),state)
                if rp in roof_tiles and roof_tiles[rp][0].get('id')=='domum_ornamentum:materially_retexturable':
                    record,template=roof_tiles[rp]
                    tile((x,y,z),dict(id=record['id'],textureData=copy.deepcopy(record['textureData'])),template)
            if x in (0,14) or z in (0,9):
                for y in range(1,5):
                    if z in (0,9) and 5<=x<=9:continue
                    # Keep posts and upper braces, not residence tables/carpet.
                    state=copy.deepcopy(block(style,(x%5,y,z%5)))
                    if y<4 and not (x%5 in (0,4) and z%5 in (0,4)):state={'Name':'minecraft:air'}
                    set_block(n,(x,y,z),state)
    # Lighting belongs under the roof, not in the connection openings.
    for x in (2,12):
        set_block(n,(x,4,7),{'Name':'minecraft:lantern','Properties':{'hanging':'true','waterlogged':'false'}})

    set_block(n,(12,1,6),{'Name':'minecolonies:blockhutgatehouse','Properties':{'facing':'north'}})
    ordered_tags=[('knight',(6,1,1)),('knight',(8,1,1)),('archer',(2,1,2)),('archer',(12,1,2)),
                  ('gate',(7,1,5)),('patrol_point',(7,1,8)),('groundlevel',(7,0,5))]
    tags=[dict(tagPos=_pos(p[0]-12,p[1]-1,p[2]-6),tagNameList=[{'tagName':tag}]) for tag,p in ordered_tags]
    inventory=[{'empty':1} for _ in range(27)]
    tile((12,1,6),dict(id='minecolonies:colonybuilding',type='minecolonies:gatehouse',
         mirror=0,inWarehouse=0,colony=0,main=0,inventory=inventory,version=0,
         pack='',path='',tagSIze=0,pos=_pos(0,0,0),
         blueprintDataProvider=dict(corner1=_pos(-12,-1,-6),corner2=_pos(2,4,3),
         path='',pack='',schematicName=f'gatehouse{level}',posTagMap=tags)),hut_schema)
    set_block(n,(13,1,6),{'Name':'minecolonies:blockminecoloniesrack',
                         'Properties':{'variant':'blockrackempty','facing':'north'}})
    tile((13,1,6),dict(id='minecolonies:rack',tagSIze=0,inWarehouse=0,
         pos=_pos(0,0,0),main=0,inventory=inventory,version=2),rack_schema)
    for x in (1,12):
        for part,z in [('foot',7),('head',8)]:
            set_block(n,(x,1,z),{'Name':'minecraft:red_bed','Properties':{'part':part,'facing':'south','occupied':'false'}})
            tile((x,1,z),{'id':'minecraft:bed'},bed_schema)
    typed=rebuild(n,schema)
    typed.value['tile_entities']=Tag(9,(10,typed_tiles))
    return n,typed,name


def generate(source,output):
    """Return three original candidate paths without modifying either source pack."""
    source=Path(source).resolve();output=Path(output).resolve()
    if source==output or source in output.parents or output in source.parents:
        raise ValueError('Candidate directory must be separate from source')
    marker=output/'.arcology-gatehouse-candidate'
    if output.exists() and not marker.is_file():raise ValueError('Refusing unrecognized candidate directory')
    structures=[_build(source,level) for level in range(1,4)]
    output.mkdir(parents=True,exist_ok=True)
    marker.write_text('Original Gatehouse geometry; local Arcology schema/material references. Native acceptance pending.\n',encoding='utf-8')
    paths=[]
    for level,(n,typed,name) in enumerate(structures,1):
        path=output/'military'/f'gatehouse{level}.blueprint'
        save(path,n,typed,name);paths.append(path)
    return paths


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    for path in generate(args.source,args.output):print(path)
