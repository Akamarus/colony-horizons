"""Original opt-in Arcology Plantation Field extensions for ten native categories.

The original residence supplies the repeated shell module and palette. No legacy fields,
worker huts, grown crops, research, ownership, or native AI are modified/seeded.
Static geometry is a candidate for native validation, not a production claim.
"""
import argparse
import copy
from pathlib import Path
from arcology_nbt import Tag,load,rebuild,save,set_block,block

# Native field/work tag strings from pinned SchematicTagConstants.
CATEGORIES={
    'cocoa':('cocoa_field','cocoa'), 'vine':('vine_field','vine'),
    'kelp':('kelp_field','kelp'), 'seagrass':('seagrass_field','seagrass'),
    'seapickle':('seapickle_field','seapickle'), 'glowberry':('glowb_field','glowb_vine'),
    'weepingvine':('weepv_field','weepv_vine'), 'twistingvine':('twistv_field','twistv_vine'),
    'crimson':('crimsonp_field','crimsonp_ground'), 'warped':('warpedp_field','warpedp_ground'),
}


def _pos(x,y,z):return dict(x=x,y=y,z=z)


def _build(source,crop):
    reference,schema,root_name=load(source/'military/archery1.blueprint')
    # Copy states only, never a donor structure or its block array/tile records.
    def material(identifier):return copy.deepcopy(next(p for p in reference['palette'] if p['Name']==identifier))
    frame=material('minecraft:spruce_planks');floor=material('minecraft:oak_planks');roof=material('minecraft:oak_slab')
    wet=crop in ('kelp','seagrass','seapickle')
    anchor=(7,1,0) if wet else (7,1,1)
    n=dict(size_x=10,size_y=6,size_z=10,name=crop+'1.blueprint',
           version=reference['version'],mcversion=reference['mcversion'],required_mods=['minecolonies'],
           entities=[],blocks=[0]*300,palette=[{'Name':'minecraft:air'}],tile_entities=[],
           optional_data={'structurize':{'primary_offset':_pos(*anchor)}})
    def place(p,s):set_block(n,p,{'Name':s} if isinstance(s,str) else s)
    style,_,_=load(source/'fundamentals/citizen1.blueprint')
    for x in range(10):
        for z in range(10):
            for y in (0,5):place((x,y,z),copy.deepcopy(block(style,(x%5,y,z%5))))
            if x in (0,9) or z in (0,9):
                for y in range(1,5):
                    if z==0 and x in (4,5,6):continue
                    state=copy.deepcopy(block(style,(x%5,y,z%5)))
                    if y<4 and not (x%5 in (0,4) and z%5 in (0,4)):state={'Name':'minecraft:air'}
                    place((x,y,z),state)
    # Hang lights from full frame blocks, above the side connection openings.
    for x in (0,9):
        for z in (2,7):
            place((x,4,z),{'Name':'minecraft:lantern','Properties':{'hanging':'true','waterlogged':'false'}})

    work=[]
    if wet:
        # Recessed two-cell water depth; full blocks contain every cardinal edge.
        # Dry dock is y=2 with two clear standing/head cells under the roof.
        pool={(x,z) for x in (2,3,6,7) for z in range(4,7)}
        for x in range(1,9):
            for z in range(1,9):
                if (x,z) in pool:
                    place((x,0,z),'minecraft:brain_coral_block' if crop=='seapickle' else 'minecraft:stone_bricks')
                    for y in (1,2):place((x,y,z),{'Name':'minecraft:water','Properties':{'level':'0'}})
                else:
                    for y in (1,2):place((x,y,z),floor)
        # Ground-level anchor keeps the pond above terrain during construction.
        # First entrance step stands at y2, second at y3 on the dry dock.
        # No basement excavation or buried side strips are needed.
        for x in (4,5,6):
            place((x,1,0),floor)
            place((x,2,0),'minecraft:air')
            for y in (1,2):place((x,y,1),floor)
        if crop=='kelp':work=[(x,0,z) for x in (2,6) for z in range(4,7)]
        elif crop=='seagrass':work=[(x,0,z) for x in (2,3,6,7) for z in range(4,7)]
        else:work=[(x,0,z) for x in (2,3,6,7) for z in (4,6)]
    elif crop=='cocoa':
        work=[(3,1,5),(6,1,5)]
        for p in work:place(p,{'Name':'minecraft:jungle_log','Properties':{'axis':'y'}})
    elif crop=='vine':
        for y in range(1,4):
            for z in range(3,8):place((2,y,z),frame)
        work=[(3,y,z) for y in (1,2) for z in range(3,8)]
    elif crop in ('glowberry','weepingvine'):
        work=[(x,4,z) for x in (3,6) for z in (4,7)]
        for p in work:place(p,frame)
    elif crop=='twistingvine':
        work=[(x,0,z) for x in (3,6) for z in (4,7)]
        for p in work:place(p,'minecraft:warped_nylium')
    elif crop in ('crimson','warped'):
        work=[(x,0,z) for x in range(3,6) for z in range(4,7)]
        for p in work:place(p,'minecraft:'+crop+'_nylium')
    else:raise ValueError('Unknown native crop category '+crop)

    place(anchor,{'Name':'minecolonies:blockhutplantationfield','Properties':{'facing':'north','mirror':'false'}})
    field_tag,work_tag=CATEGORIES[crop]
    positioned=[(field_tag,anchor)]+[(work_tag,p) for p in work]
    tag_map=[{'tagPos':_pos(*(p[i]-anchor[i] for i in range(3))),'tagNameList':[{'tagName':tag}]} for tag,p in positioned]
    path=f'infrastructure/plantationfields/{crop}1.blueprint'
    # Field tile schema comes from TileEntityPlantationField, not a hut template:
    # it has no inventory/building type/colony and persists rotMir as a byte.
    field=dict(id='minecolonies:plantationfield',**_pos(*anchor),rotMir=0,
               name=crop+'1',path=path,pack='Arcology Horizons',
               blueprintDataProvider=dict(corner1=_pos(*(-v for v in anchor)),
                   corner2=_pos(9-anchor[0],5-anchor[1],9-anchor[2]),
                   schematicName=crop+'1',path=path,pack='Arcology Horizons',posTagMap=tag_map))
    n['tile_entities']=[field]
    typed=rebuild(n,schema)
    field_typed=rebuild(field)
    field_typed.value['rotMir']=Tag(1,0)
    typed.value['tile_entities']=Tag(9,(10,[field_typed]))
    return n,typed,root_name


def generate(source,output):
    """Return ten absolute candidate paths; preserve both original and legacy pack."""
    source=Path(source).resolve();output=Path(output).resolve()
    if source==output or source in output.parents or output in source.parents:
        raise ValueError('Crop candidate output must be separate from original assets')
    marker=output/'.arcology-crops-candidate'
    if output.exists() and not marker.is_file():raise ValueError('Refusing unrecognized output directory')
    structures=[(crop,_build(source,crop)) for crop in CATEGORIES]
    output.mkdir(parents=True,exist_ok=True)
    marker.write_text('Original Plantation Field layouts. Local Arcology material/schema references only. Native production unverified.\n',encoding='utf-8')
    paths=[]
    for crop,(n,typed,root_name) in structures:
        path=output/'infrastructure/plantationfields'/f'{crop}1.blueprint'
        save(path,n,typed,root_name);paths.append(path)
    return paths


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    for path in generate(args.source,args.output):print(path)
