"""Independent Gatehouse geometry/native metadata contracts; no gameplay claims."""
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


def hashes(root):
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}


def contract(n,level):
    assert tuple(n['size_'+k] for k in 'xyz')==(15,6,10)
    assert n['name']==f'gatehouse{level}.blueprint'
    assert n['optional_data']['structurize']['primary_offset']==dict(x=12,y=1,z=6)
    assert not n['entities'] and len(n['blocks'])==450
    cells={(x,y,z):block(n,(x,y,z)) for x in range(15) for y in range(6) for z in range(10)}
    huts=[p for p,s in cells.items() if s['Name'].startswith('minecolonies:blockhut')]
    assert huts==[(12,1,6)]
    assert cells[huts[0]]=={'Name':'minecolonies:blockhutgatehouse','Properties':{'facing':'north'}}
    tiles={tuple(t[k] for k in 'xyz'):t for t in n['tile_entities']}
    assert len(tiles)==len(n['tile_entities'])
    hut=tiles[huts[0]]
    assert hut['id']=='minecolonies:colonybuilding' and hut['type']=='minecolonies:gatehouse'
    provider=hut['blueprintDataProvider']
    assert provider['schematicName']==f'gatehouse{level}'
    assert provider['corner1']==dict(x=-12,y=-1,z=-6)
    assert provider['corner2']==dict(x=2,y=4,z=3)
    tags={}
    for entry in provider['posTagMap']:
        p=tuple(huts[0][i]+entry['tagPos'][k] for i,k in enumerate('xyz'))
        assert p in cells
        for tag in entry['tagNameList']:tags.setdefault(tag['tagName'],[]).append(p)
    assert tags=={'knight':[(6,1,1),(8,1,1)],'archer':[(2,1,2),(12,1,2)],
                  'gate':[(7,1,5)],'patrol_point':[(7,1,8)],'groundlevel':[(7,0,5)]}
    for kind,positions in tags.items():
        if kind=='groundlevel':continue
        for x,y,z in positions:
            assert cells[x,y-1,z]['Name'] not in ('minecraft:air','minecraft:water')
            assert all(cells[x,yy,z]['Name']=='minecraft:air' for yy in range(y,5))
    for x in (1,12):
        for part,z in [('foot',7),('head',8)]:
            assert cells[x,1,z]=={'Name':'minecraft:red_bed','Properties':{'part':part,'facing':'south','occupied':'false'}}
            assert tiles[x,1,z]['id']=='minecraft:bed'
    assert sum(s['Name']=='minecraft:red_bed' for s in cells.values())==4
    assert cells[13,1,6]['Name']=='minecolonies:blockminecoloniesrack'
    assert tiles[13,1,6]['id']=='minecolonies:rack'
    for x in range(5,10):
        for z in range(10):
            assert cells[x,0,z]['Name'] not in ('minecraft:air','minecraft:water')
            assert all(cells[x,y,z]['Name']=='minecraft:air' for y in range(1,5)), ('passage blocked',x,z)
    # Two-dimensional movement envelopes: citizen access to guard/service cells,
    # and adult-horse bounding envelope through both entrances.
    def reachable(width,height):
        def clear(x,z):
            return all((xx,0,zz) in cells and cells[xx,0,zz]['Name'] not in ('minecraft:air','minecraft:water')
                       and all(cells[xx,y,zz]['Name']=='minecraft:air' for y in range(1,height+1))
                       for xx in range(x,x+width) for zz in range(z,z+width))
        seen={(7,0)};q=deque(seen)
        while q:
            x,z=q.popleft()
            for p in ((x+1,z),(x-1,z),(x,z+1),(x,z-1)):
                if p not in seen and clear(*p):seen.add(p);q.append(p)
        return seen
    citizen=reachable(1,2)
    assert all((x,z) in citizen for key in ('knight','archer','gate','patrol_point') for x,_,z in tags[key])
    assert {(12,5),(13,5),(2,7),(11,7)}<=citizen
    assert (7,8) in reachable(2,4)
    # Front-facing firing lanes at standing eye height; no glass/fence assumed transparent.
    for x in (2,12):
        assert all(cells[x,y,z]['Name']=='minecraft:air' for z in (0,1,2) for y in (2,3))
    assert not any('gate' in s['Name'] and s['Name']!='minecolonies:blockhutgatehouse' or s['Name'].endswith('_door') for s in cells.values())
    for p,s in cells.items():
        if s['Name']=='domum_ornamentum:vanilla_slab_compat':
            assert tiles[p]['id']=='domum_ornamentum:materially_retexturable' and tiles[p]['textureData']
    for p,t in tiles.items():
        assert p in cells and cells[p]['Name']!='minecraft:air'
        assert t['id'] in {'minecolonies:colonybuilding','minecolonies:rack','minecraft:bed','domum_ornamentum:materially_retexturable'}


class GatehouseContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        implementation=ROOT/'tools/arcology_gatehouse.py'
        assert implementation.exists(), 'Gatehouse generator has not been implemented'
        spec=importlib.util.spec_from_file_location('arcology_gatehouse',implementation)
        cls.generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(cls.generator)
        cls.temp=tempfile.TemporaryDirectory(prefix='gatehouse-contract-',dir=ROOT/'.setup')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.before=hashes(SOURCE)
        cls.paths=cls.generator.generate(SOURCE,Path(cls.temp.name)/'a')
        cls.repeat=cls.generator.generate(SOURCE,Path(cls.temp.name)/'b')

    def test_three_levels_meet_independent_contract(self):
        self.assertEqual(len(self.paths),3)
        for level,path in enumerate(self.paths,1):
            with self.subTest(level=level):contract(load(path)[0],level)

    def test_deterministic_typed_nbt(self):
        for path,repeat in zip(self.paths,self.repeat):
            self.assertEqual(path.read_bytes(),repeat.read_bytes())
            _,t,_=load(path)
            for field,kind in {'size_x':2,'size_y':2,'size_z':2,'blocks':11,'version':1,'mcversion':3,'palette':9,'tile_entities':9}.items():
                self.assertEqual(t.value[field].kind,kind)
            self.assertEqual(t.value['tile_entities'].value[0],10)

    def test_source_unchanged(self):self.assertEqual(self.before,hashes(SOURCE))

    def test_rejects_bad_marker_route_bed_sightline_and_arrival(self):
        original=load(self.paths[-1])[0]
        for mutation in ('marker','route','bed','sightline','arrival'):
            n=copy.deepcopy(original)
            if mutation=='marker':
                hut=next(t for t in n['tile_entities'] if t.get('type')=='minecolonies:gatehouse')
                hut['blueprintDataProvider']['posTagMap'][0]['tagNameList'][0]['tagName']='work'
            elif mutation=='arrival':set_block(n,(7,0,5),{'Name':'minecraft:air'})
            else:set_block(n,{'route':(7,1,4),'bed':(1,1,8),'sightline':(2,2,0)}[mutation],{'Name':'minecraft:stone'})
            with self.subTest(mutation=mutation),self.assertRaises(AssertionError):contract(n,3)

    def test_rejects_overlapping_or_unowned_output(self):
        for output in (SOURCE,SOURCE/'candidate',Path(self.temp.name)):
            with self.assertRaises(ValueError):self.generator.generate(SOURCE,output)


if __name__=='__main__':unittest.main(verbosity=2)
