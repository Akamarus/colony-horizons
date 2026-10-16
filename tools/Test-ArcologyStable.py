"""Independent contracts for the proposed stable, not native operational tests."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from collections import deque
from arcology_nbt import load, block, set_block
import copy

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path('blueprints/arcology-horizons')


def hashes(root):
    return {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob('*') if p.is_file()}


def contract(n, level):
    assert tuple(n['size_' + k] for k in 'xyz') == (15, 6, 20)
    assert n['name'] == f'stable{level}.blueprint'
    assert n['optional_data']['structurize']['primary_offset'] == dict(x=12, y=1, z=1)
    assert n['entities'] == []
    assert len(n['blocks']) == 900
    cells = {(x,y,z): block(n,(x,y,z)) for x in range(15) for y in range(6) for z in range(20)}
    huts = [p for p,s in cells.items() if s['Name'].startswith('minecolonies:blockhut')]
    assert huts == [(12,1,1)], huts
    assert cells[huts[0]] == {'Name':'minecolonies:blockhutstable','Properties':{'facing':'south'}}
    tiles = {tuple(t[k] for k in 'xyz'): t for t in n['tile_entities']}
    assert len(tiles) == len(n['tile_entities']), 'duplicate tile entities'
    hut = tiles[huts[0]]
    assert hut['id'] == 'minecolonies:colonybuilding' and hut['type'] == 'minecolonies:stable'
    provider = hut['blueprintDataProvider']
    assert provider['schematicName'] == f'stable{level}'
    assert provider['corner1'] == dict(x=-12,y=-1,z=-1)
    assert provider['corner2'] == dict(x=2,y=4,z=18)
    tags = {}
    for entry in provider['posTagMap']:
        p = tuple(huts[0][i] + entry['tagPos'][k] for i,k in enumerate('xyz'))
        assert p in cells
        for tag in entry['tagNameList']:
            tags.setdefault(tag['tagName'], []).append(p)
    expected = {(x,1,z) for z in (5,8,11,14,17)[:level] for x in (2,11)}
    assert set(tags) == {'stall','patrol_point','groundlevel'}
    assert len(tags['stall']) == 2*level and set(tags['stall']) == expected
    assert tags['patrol_point'] == [(7,1,1)] and tags['groundlevel'] == [(7,0,1)]
    heads = {p for p,s in cells.items() if s['Name']=='minecraft:red_bed' and s['Properties']['part']=='head'}
    assert heads == {(x,1,1) for x in (1,2,3,4,10,11)[:level+1]}
    for x,y,z in heads:
        for part,dz in [('head',0),('foot',1)]:
            state = cells[x,y,z+dz]
            assert state == {'Name':'minecraft:red_bed','Properties':{'part':part,'facing':'north','occupied':'false'}}
            assert tiles[x,y,z+dz]['id'] == 'minecraft:bed'
    assert sum(s['Name']=='minecraft:red_bed' for s in cells.values()) == 2*(level+1)
    assert cells[13,1,1]['Name']=='minecolonies:blockminecoloniesrack'
    assert tiles[13,1,1]['id']=='minecolonies:rack'
    # Design clearances are deliberately stricter than point pathfinding: all
    # five aisle cells and every four-by-three bay retain four clear cells above.
    for x in range(1,14):
        for z in range(4,19):
            assert cells[x,0,z]['Name'] not in ('minecraft:air','minecraft:water')
            assert all(cells[x,y,z]['Name']=='minecraft:air' for y in range(1,5)), ('bay obstruction',x,z)
    for x in range(5,10):
        for z in range(19):
            assert all(cells[x,y,z]['Name']=='minecraft:air' for y in range(1,5)), ('aisle obstruction',x,z)
    # A 2x2 horizontal envelope bounds a 1.3964844-wide adult horse, with four
    # cells headroom. Flood fill that envelope, not a one-cell citizen route.
    def passable(x,z):
        return all((xx,0,zz) in cells and cells[xx,0,zz]['Name'] not in ('minecraft:air','minecraft:water')
                   and all(cells[xx,y,zz]['Name']=='minecraft:air' for y in range(1,5))
                   for xx in (x,x+1) for zz in (z,z+1))
    seen={(7,0)}; queue=deque(seen)
    while queue:
        x,z=queue.popleft()
        for p in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
            if p not in seen and passable(*p): seen.add(p);queue.append(p)
    assert all((x,z) in seen for x,_,z in expected), 'horse-sized route missing'
    assert not any('gate' in s['Name'] or s['Name'].endswith('_door') for s in cells.values())
    for p,s in cells.items():
        if s['Name']=='domum_ornamentum:vanilla_slab_compat':
            assert tiles[p]['id']=='domum_ornamentum:materially_retexturable'
            assert tiles[p]['textureData'], 'missing Domum material data'
    for p,t in tiles.items():
        assert p in cells and cells[p]['Name']!='minecraft:air'
        assert t['id'] in {'minecolonies:colonybuilding','minecolonies:rack','minecraft:bed','domum_ornamentum:materially_retexturable'}


class StableContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        implementation=ROOT/'tools/arcology_stable.py'
        assert implementation.exists(), 'Stable generation entry point has not been implemented'
        spec=importlib.util.spec_from_file_location('arcology_stable', implementation)
        cls.generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(cls.generator)
        cls.temp=tempfile.TemporaryDirectory(prefix='stable-contract-',dir=ROOT/'.setup')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.before=hashes(SOURCE)
        cls.paths=cls.generator.generate(SOURCE,Path(cls.temp.name)/'a')
        cls.repeat=cls.generator.generate(SOURCE,Path(cls.temp.name)/'b')

    def test_five_levels_and_independent_geometry_contract(self):
        self.assertEqual(len(self.paths),5)
        for level,path in enumerate(self.paths,1):
            with self.subTest(level=level): contract(load(path)[0],level)

    def test_typed_nbt_and_byte_determinism(self):
        for path,repeated in zip(self.paths,self.repeat):
            self.assertEqual(path.read_bytes(),repeated.read_bytes())
            _,typed,_=load(path)
            for field,kind in {'size_x':2,'size_y':2,'size_z':2,'blocks':11,'version':1,'mcversion':3,'palette':9,'tile_entities':9}.items():
                self.assertEqual(typed.value[field].kind,kind,field)
            self.assertEqual(typed.value['tile_entities'].value[0],10)

    def test_original_assets_unchanged(self):
        self.assertEqual(self.before,hashes(SOURCE))

    def test_bad_markers_and_collisions_are_rejected(self):
        n=load(self.paths[-1])[0]
        for bad in ('tag','aisle','bed','bay'):
            copy_n=copy.deepcopy(n)
            if bad=='tag':
                hut=next(t for t in copy_n['tile_entities'] if t.get('type')=='minecolonies:stable')
                hut['blueprintDataProvider']['posTagMap'][0]['tagPos']['x']=100
            else:
                set_block(copy_n, {'aisle':(7,1,3),'bed':(1,1,1),'bay':(2,2,5)}[bad], {'Name':'minecraft:stone'})
            with self.subTest(mutation=bad), self.assertRaises(AssertionError): contract(copy_n,5)

    def test_refuses_source_overlap(self):
        with self.assertRaises(ValueError): self.generator.generate(SOURCE,SOURCE/'candidate')
        with self.assertRaises(ValueError): self.generator.generate(SOURCE,SOURCE)


if __name__=='__main__': unittest.main(verbosity=2)
