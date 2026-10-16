"""Independent crop-extension contracts. Geometry checks are not gameplay proof."""
import copy
import hashlib
import importlib.util
from collections import deque
from pathlib import Path
import tempfile
import unittest
from arcology_nbt import load,block,set_block

ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('blueprints/arcology-horizons')
# Native strings and planned positions deliberately defined independently of generator.
CROPS={
 'cocoa':('cocoa_field','cocoa',[(3,1,5),(6,1,5)]),
 'vine':('vine_field','vine',[(3,y,z) for y in (1,2) for z in range(3,8)]),
 'kelp':('kelp_field','kelp',[(x,0,z) for x in (2,6) for z in range(4,7)]),
 'seagrass':('seagrass_field','seagrass',[(x,0,z) for x in (2,3,6,7) for z in range(4,7)]),
 'seapickle':('seapickle_field','seapickle',[(x,0,z) for x in (2,3,6,7) for z in (4,6)]),
 'glowberry':('glowb_field','glowb_vine',[(x,4,z) for x in (3,6) for z in (4,7)]),
 'weepingvine':('weepv_field','weepv_vine',[(x,4,z) for x in (3,6) for z in (4,7)]),
 'twistingvine':('twistv_field','twistv_vine',[(x,0,z) for x in (3,6) for z in (4,7)]),
 'crimson':('crimsonp_field','crimsonp_ground',[(x,0,z) for x in range(3,6) for z in range(4,7)]),
 'warped':('warpedp_field','warpedp_ground',[(x,0,z) for x in range(3,6) for z in range(4,7)]),
}
WET={'kelp','seagrass','seapickle'}
SOLID={'minecraft:spruce_planks','minecraft:oak_planks','minecraft:stone_bricks',
       'minecraft:jungle_log','minecraft:crimson_nylium','minecraft:warped_nylium','minecraft:brain_coral_block'}


def hashes(root):return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}


# Independent allowed states for the pinned crop design. Validate every palette
# entry, including unused entries, before native decoding can substitute defaults.
PALETTE_RULES={name:{} for name in (
 'minecraft:air','minecraft:spruce_planks','minecraft:oak_planks',
 'minecraft:stone_bricks','minecraft:crimson_nylium','minecraft:warped_nylium',
 'minecraft:brain_coral_block')}
PALETTE_RULES.update({
 'minecraft:spruce_stairs':{'half':{'top','bottom'},'waterlogged':{'true','false'},'shape':{'straight','inner_left','inner_right','outer_left','outer_right'},'facing':{'north','south','east','west'}},
 'minecraft:oak_slab':{'type':{'top','bottom','double'},'waterlogged':{'true','false'}},
 'minecraft:lantern':{'hanging':{'true','false'},'waterlogged':{'true','false'}},
 'minecraft:jungle_log':{'axis':{'x','y','z'}},
 'minecraft:water':{'level':{str(i) for i in range(16)}},
 'minecolonies:blockhutplantationfield':{'facing':{'north','south','east','west'},'mirror':{'true','false'}}
})


def validate_palette(n):
    for state in n['palette']:
        name=state['Name']
        assert name in PALETTE_RULES, ('unsupported palette block',name)
        assert set(state)<={'Name','Properties'}, ('unexpected palette keys',state)
        rules=PALETTE_RULES[name];props=state.get('Properties',{})
        assert set(props)==set(rules), ('missing or unknown block properties',state)
        assert all(value in rules[key] for key,value in props.items()), ('invalid property value',state)


def contract(n,crop):
    validate_palette(n)
    assert tuple(n['size_'+k] for k in 'xyz')==(10,6,10)
    assert n['name']==crop+'1.blueprint' and not n['entities'] and len(n['blocks'])==300
    anchor=(7,1,0) if crop in WET else (7,1,1)
    assert n['optional_data']['structurize']['primary_offset']==dict(zip('xyz',anchor))
    cells={(x,y,z):block(n,(x,y,z)) for x in range(10) for y in range(6) for z in range(10)}
    field_blocks=[p for p,s in cells.items() if s['Name'].startswith('minecolonies:blockhut')]
    assert field_blocks==[anchor], 'must have one field anchor, no worker hut'
    assert cells[anchor]=={'Name':'minecolonies:blockhutplantationfield','Properties':{'facing':'north','mirror':'false'}}
    assert len(n['tile_entities'])==1
    tile=n['tile_entities'][0]
    assert tile['id']=='minecolonies:plantationfield' and tuple(tile[k] for k in 'xyz')==anchor
    assert not {'colony','inventory','type','workingPositions','building'}.intersection(tile)
    assert tile['rotMir']==0 and tile['name']==crop+'1'
    assert tile['path']==f'infrastructure/plantationfields/{crop}1.blueprint' and tile['pack']=='Arcology Horizons'
    provider=tile['blueprintDataProvider']
    assert provider['schematicName']==crop+'1'
    assert provider['path']==tile['path'] and provider['pack']==tile['pack']
    assert provider['corner1']==dict(zip('xyz',(-anchor[0],-anchor[1],-anchor[2])))
    assert provider['corner2']==dict(zip('xyz',(9-anchor[0],5-anchor[1],9-anchor[2])))
    tags={}
    for entry in provider['posTagMap']:
        p=tuple(anchor[i]+entry['tagPos'][k] for i,k in enumerate('xyz'))
        assert p in cells
        for tag in entry['tagNameList']:tags.setdefault(tag['tagName'],[]).append(p)
    field_tag,work_tag,positions=CROPS[crop]
    assert set(tags)=={field_tag,work_tag} and tags[field_tag]==[anchor]
    assert len(tags[work_tag])==len(positions) and set(tags[work_tag])==set(positions)
    def state(p):return cells[p]['Name'] if p in cells else None
    def air(p):return state(p)=='minecraft:air'
    assert sum(s['Name']=='minecraft:lantern' for s in cells.values())==4
    assert not any(s['Name'] in {'minecraft:cocoa','minecraft:vine','minecraft:kelp','minecraft:seagrass','minecraft:sea_pickle','minecraft:cave_vines','minecraft:weeping_vines','minecraft:twisting_vines','minecraft:crimson_fungus','minecraft:warped_fungus'} for s in cells.values()), 'no seeded grown output'
    action_targets=[]
    if crop=='cocoa':
        derived=set()
        for x,y,z in positions:
            assert state((x,y,z))=='minecraft:jungle_log'
            for p in ((x-1,y,z),(x+1,y,z),(x,y,z-1),(x,y,z+1)):
                assert air(p);derived.add(p)
        assert len(derived)==8
        action_targets=list(derived)
    elif crop=='vine':
        for x,y,z in positions:
            assert air((x,y,z)) and state((x-1,y,z)) in SOLID
        assert len(positions)==10 # Native 20% starter population = two.
        action_targets=positions
    elif crop in WET:
        for x in range(1,9):
            assert state((x,2,1)) in SOLID and air((x,3,1)) and air((x,4,1)), 'recessed north strip traps returning workers'
        for x in (4,5,6):
            assert state((x,1,0)) in SOLID and air((x,2,0)) and air((x,3,0)), 'raised pond needs a one-block entry step'
        for x in (2,3,6,7):
            for z in range(4,7):
                assert state((x,0,z))==('minecraft:brain_coral_block' if crop=='seapickle' else 'minecraft:stone_bricks')
                for y in (1,2):
                    assert cells[x,y,z]=={'Name':'minecraft:water','Properties':{'level':'0'}}
                    for nx,nz in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
                        assert state((nx,y,nz))=='minecraft:water' or state((nx,y,nz)) in SOLID, 'uncontained water edge'
                assert air((x,3,z)) and air((x,4,z))
        assert len(positions)<=({'seapickle':10,'seagrass':121,'kelp':20}[crop])
        # Kelp seeks first air above planting/harvest actions. Bone-meal modules
        # use bottom targets and harvest above them, so test both elevations.
        action_targets=[(x,3,z) for x,_,z in positions] if crop=='kelp' else [(x,y,z) for x,_,z in positions for y in (0,1)]
    elif crop in ('glowberry','weepingvine'):
        for x,y,z in positions:
            assert state((x,y,z)) in SOLID
            assert all(air((x,yy,z)) for yy in (1,2,3))
            action_targets.extend([(x,3,z),(x,2,z)])
    elif crop=='twistingvine':
        for x,y,z in positions:
            assert state((x,y,z))=='minecraft:warped_nylium'
            assert all(air((x,yy,z)) for yy in range(1,5))
            action_targets.extend([(x,1,z),(x,2,z)])
    else:
        substrate='minecraft:crimson_nylium' if crop=='crimson' else 'minecraft:warped_nylium'
        for x,y,z in positions:
            assert state((x,y,z))==substrate
            assert all(air((x,yy,z)) for yy in range(1,5)), 'nylium cover/harvestable decoration'
            action_targets.extend([(x,0,z),(x,1,z)])
    # Conservative walkable standing cells, including the wet family's two steps.
    standing={p for p in cells if 1<=p[1]<=4 and air(p) and air((p[0],p[1]+1,p[2])) and state((p[0],p[1]-1,p[2])) in SOLID}
    start=(5,2 if crop in WET else 1,0);assert start in standing
    seen={start};queue=deque(seen)
    while queue:
        x,y,z=queue.popleft()
        for xx,zz in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            for yy in (y-1,y,y+1):
                p=(xx,yy,zz)
                # Full-height steps need head clearance above the lower cell.
                if p in standing and p not in seen and air((x,max(y,yy)+1,z)) and air((xx,max(y,yy)+1,zz)):
                    seen.add(p);queue.append(p)
    front=(anchor[0],anchor[1],anchor[2]-1)
    # Wet tile has reachable side access on dock instead of a floating north cell.
    approaches=[front,(anchor[0]-1,anchor[1],anchor[2]),(anchor[0]+1,anchor[1],anchor[2])]
    assert (any(sum((a-b)**2 for a,b in zip(anchor,q))<=9 for q in seen) if crop in WET else any(p in seen for p in approaches)), 'anchor inaccessible'
    for p in action_targets:
        assert any(sum((a-b)**2 for a,b in zip(p,q))<=16 for q in seen), ('native work target lacks nearby reachable dry cell',p)
    assert not any('fence_gate' in s['Name'] or s['Name'].endswith('_door') for s in cells.values())


class CropContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path=ROOT/'tools/arcology_crops.py'
        assert path.exists(), 'Crop extension generator has not been implemented'
        spec=importlib.util.spec_from_file_location('arcology_crops',path)
        cls.generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(cls.generator)
        cls.temp=tempfile.TemporaryDirectory(prefix='crop-contract-',dir=ROOT/'.setup');cls.addClassCleanup(cls.temp.cleanup)
        cls.before=hashes(SOURCE)
        cls.paths=cls.generator.generate(SOURCE,Path(cls.temp.name)/'a')
        cls.repeat=cls.generator.generate(SOURCE,Path(cls.temp.name)/'b')
        cls.by_crop={p.stem[:-1]:p for p in cls.paths}

    def test_exact_ten_new_categories(self):
        self.assertEqual(len(self.paths),10);self.assertEqual(set(self.by_crop),set(CROPS))
        self.assertFalse({'cactus','sugar','bamboo'} & set(self.by_crop))

    def test_typed_nbt_and_reproducibility(self):
        for a,b in zip(self.paths,self.repeat):
            self.assertEqual(a.read_bytes(),b.read_bytes())
            _,t,_=load(a)
            for field,kind in {'size_x':2,'size_y':2,'size_z':2,'blocks':11,'version':1,'mcversion':3,'palette':9,'tile_entities':9}.items():self.assertEqual(t.value[field].kind,kind)
            field=t.value['tile_entities'].value[1][0]
            self.assertEqual(field.value['rotMir'].kind,1)
            self.assertEqual(field.value['blueprintDataProvider'].kind,10)

    def test_rejects_unknown_palette_block(self):
        n=load(self.by_crop['cocoa'])[0]
        set_block(n,(0,5,0),{'Name':'missing_review_mod:unavailable_block'})
        with self.assertRaises(AssertionError):contract(n,'cocoa')

    def test_rejects_invalid_palette_property(self):
        n=load(self.by_crop['cocoa'])[0]
        set_block(n,(0,5,0),{'Name':'minecraft:spruce_planks','Properties':{'not_a_property':'bad'}})
        with self.assertRaises(AssertionError):contract(n,'cocoa')

    def test_original_source_unchanged(self):self.assertEqual(self.before,hashes(SOURCE))

    def test_rejects_bad_crop_geometry_individually(self):
        for crop,path in self.by_crop.items():
            n=load(path)[0];position=CROPS[crop][2][0]
            set_block(n,position,{'Name':'minecraft:air' if crop!='vine' else 'minecraft:stone_bricks'})
            with self.subTest(crop=crop),self.assertRaises(AssertionError):contract(n,crop)

    def test_rejects_bad_tag_water_and_entry(self):
        for issue in ('tag','water','entry'):
            n=load(self.by_crop['kelp'])[0]
            if issue=='tag':n['tile_entities'][0]['blueprintDataProvider']['posTagMap'][0]['tagNameList'][0]['tagName']='kelp'
            elif issue=='water':set_block(n,(2,1,4),{'Name':'minecraft:water','Properties':{'level':'3'}})
            else:
                for x in (4,5,6):
                    for y in (3,4):set_block(n,(x,y,0),{'Name':'minecraft:stone_bricks'})
            with self.subTest(issue=issue),self.assertRaises(AssertionError):contract(n,'kelp')

    def test_rejects_source_overlap_and_unowned_output(self):
        for output in (SOURCE,SOURCE/'newfields',Path(self.temp.name)):
            with self.assertRaises(ValueError):self.generator.generate(SOURCE,output)


def crop_test(crop):
    def test(self):contract(load(self.by_crop[crop])[0],crop)
    return test
for category in CROPS:setattr(CropContracts,'test_native_contract_'+category,crop_test(category))

if __name__=='__main__':unittest.main(verbosity=2)
