"""Opt-in original Stable 1–5 geometry; source assets supply schemas and modular shell references.

Returns five new blueprint paths. Does not modify or integrate either input pack.
Static contracts do not establish native worker or cavalry operation.
"""
import argparse
import copy
from pathlib import Path
from arcology_nbt import Tag, load, plain, rebuild, save, block, set_block


def _reference(source, relative):
    return load(source / relative)


def _tile_reference(data, typed, identity):
    for index, tile in enumerate(data['tile_entities']):
        if tile.get('id') == identity:
            return tile, typed.value['tile_entities'].value[1][index]
    raise ValueError('Missing metadata reference: ' + identity)


def _pos(x,y,z):
    return dict(x=x,y=y,z=z)


def _build(source, level):
    # Functional layout is original; the shell follows the existing residence module.
    ref, schema, name = _reference(source, f'military/archery{level}.blueprint')
    _, hut_schema = _tile_reference(ref, schema, 'minecolonies:colonybuilding')
    _, bed_schema = _tile_reference(ref, schema, 'minecraft:bed')
    builder, builder_schema, _ = _reference(source, 'fundamentals/builder1.blueprint')
    _, rack_schema = _tile_reference(builder, builder_schema, 'minecolonies:rack')
    n = dict(size_x=15, size_y=6, size_z=20, name=f'stable{level}.blueprint',
             version=ref['version'], mcversion=ref['mcversion'],
             required_mods=['minecolonies'] + (['domum_ornamentum'] if level==5 else []),
             entities=[], blocks=[0]*900, palette=[{'Name':'minecraft:air'}], tile_entities=[],
             optional_data={'structurize':{'primary_offset':_pos(12,1,1)}})
    typed_tiles = []

    def place(p, state):
        set_block(n, p, state)

    def tile(p, data, template):
        value=copy.deepcopy(data);value.update(_pos(*p))
        n['tile_entities'].append(value)
        typed_tiles.append(rebuild(value,template))

    # Match the original five-cell module, including paired seams at 4/5.
    style,style_schema,_=load(source/f'fundamentals/citizen{level}.blueprint')
    roof_tiles={tuple(t[k] for k in 'xyz'):(t,style_schema.value['tile_entities'].value[1][i])
                for i,t in enumerate(style['tile_entities'])}
    for x in range(15):
        for z in range(20):
            for y in (0,5):
                rp=(x%5,y,z%5);state=copy.deepcopy(block(style,rp))
                place((x,y,z),state)
                if rp in roof_tiles and roof_tiles[rp][0].get('id')=='domum_ornamentum:materially_retexturable':
                    record,template=roof_tiles[rp]
                    tile((x,y,z),dict(id=record['id'],textureData=copy.deepcopy(record['textureData'])),template)
            if x in (0,14) or z in (0,19):
                for y in range(1,5):
                    if z==0 and 5<=x<=9:continue
                    # Keep posts and upper braces, not residence tables/carpet.
                    state=copy.deepcopy(block(style,(x%5,y,z%5)))
                    if y<4 and not (x%5 in (0,4) and z%5 in (0,4)):state={'Name':'minecraft:air'}
                    place((x,y,z),state)
    # Lighting belongs under the roof, not in the connection openings.
    for x in (2,12):
        place((x,4,2),{'Name':'minecraft:lantern','Properties':{'hanging':'true','waterlogged':'false'}})

    # Hut, storage, and staff beds stay outside the mounted circulation volume.
    place((12,1,1), {'Name':'minecolonies:blockhutstable','Properties':{'facing':'south'}})
    marker_positions=[('stall',(x,1,z)) for z in (5,8,11,14,17)[:level] for x in (2,11)]
    marker_positions += [('patrol_point',(7,1,1)),('groundlevel',(7,0,1))]
    tag_map=[{'tagPos':_pos(p[0]-12,p[1]-1,p[2]-1),'tagNameList':[{'tagName':tag}]}
             for tag,p in marker_positions]
    empty_inventory=[{'empty':1} for _ in range(27)]
    tile((12,1,1), dict(id='minecolonies:colonybuilding',type='minecolonies:stable',
         mirror=0,inWarehouse=0,colony=0,main=0,inventory=empty_inventory,version=0,
         pack='',path='',tagSIze=0,pos=_pos(0,0,0),
         blueprintDataProvider=dict(corner1=_pos(-12,-1,-1),corner2=_pos(2,4,18),
            path='',pack='',schematicName=f'stable{level}',posTagMap=tag_map)),hut_schema)
    place((13,1,1), {'Name':'minecolonies:blockminecoloniesrack',
                    'Properties':{'variant':'blockrackempty','facing':'south'}})
    tile((13,1,1),dict(id='minecolonies:rack',tagSIze=0,inWarehouse=0,
         pos=_pos(0,0,0),main=0,inventory=empty_inventory,version=2),rack_schema)
    for x in (1,2,3,4,10,11)[:level+1]:
        for part,z in [('head',1),('foot',2)]:
            place((x,1,z), {'Name':'minecraft:red_bed',
                            'Properties':{'part':part,'facing':'north','occupied':'false'}})
            tile((x,1,z), {'id':'minecraft:bed'},bed_schema)

    typed=rebuild(n,schema)
    # Heterogeneous tile list entries each use their own reference schema; the
    # generic list rebuilder must not apply the donor's nth entity type to ours.
    typed.value['tile_entities']=Tag(9,(10,typed_tiles))
    return n,typed,name


def generate(source, output):
    """Generate only five new blueprints in a separate opt-in candidate directory."""
    source=Path(source).resolve();output=Path(output).resolve()
    if source==output or source in output.parents or output in source.parents:
        raise ValueError('Candidate output must be separate from original assets')
    marker=output/'.arcology-stable-candidate'
    if output.exists() and not marker.is_file():
        raise ValueError('Refusing an unrecognized output directory')
    # Validate/read all references before creating an output directory.
    structures=[_build(source,level) for level in range(1,6)]
    output.mkdir(parents=True,exist_ok=True)
    marker.write_text('Original stable geometry; metadata/material references from local Arcology assets. Native validation pending.\n',encoding='utf-8')
    paths=[]
    for level,(n,typed,name) in enumerate(structures,1):
        path=output/'military'/f'stable{level}.blueprint'
        save(path,n,typed,name)
        paths.append(path)
    return paths


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    for path in generate(args.source,args.output): print(path)
