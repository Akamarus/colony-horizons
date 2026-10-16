"""Independent static quarry contracts; no native excavation claim."""
import copy
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
from arcology_nbt import load, block, set_block

ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('blueprints/arcology-horizons')
SPECS=(('simplequarry',15,32,7),('mediumquarry',25,64,12))
AIR={'Name':'minecraft:air'}
KEEP={'Name':'structurize:blocksubstitution'}
STONE={'Name':'minecraft:cobblestone'}
LADDER={'Name':'minecraft:ladder','Properties':{'facing':'east','waterlogged':'false'}}

def hashes(root):
    return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}

def contract(surface,shaft,family,w,d,a):
    for n,size in [(surface,(w,7,w)),(shaft,(w,d,w))]:
        assert tuple(n['size_'+k] for k in 'xyz')==size
        assert len(n['blocks'])==(size[0]*size[1]*size[2]+1)//2
        assert n['entities']==[]
        assert set(n['required_mods'])=={'minecolonies','structurize'}
        assert all(0<=((packed>>shift)&65535)<len(n['palette']) for packed in n['blocks'] for shift in (0,16))
    assert surface['name']==family+'1.blueprint'
    assert shaft['name']==family+'shaft1.blueprint'
    assert surface['optional_data']['structurize']['primary_offset']==dict(x=a,y=2,z=2)
    assert shaft['optional_data']['structurize']['primary_offset']==dict(x=a,y=d-2,z=2)
    huts=[]
    for y in range(7):
        for z in range(w):
            for x in range(w):
                if block(surface,(x,y,z))['Name'].startswith('minecolonies:') and 'rack' not in block(surface,(x,y,z))['Name']:
                    huts.append((x,y,z))
    assert huts==[(a,2,2)]
    assert block(surface,huts[0])=={'Name':'minecolonies:'+family,'Properties':{'facing':'south'}}
    assert not any(s['Name'].startswith('minecolonies:') for s in shaft['palette'])
    tiles={tuple(t[k] for k in 'xyz'):t for t in surface['tile_entities']}
    assert len(tiles)==len(surface['tile_entities'])==3
    hut=tiles[a,2,2]
    assert hut['id']=='minecolonies:colonybuilding' and hut['type']=='minecolonies:'+family
    provider=hut['blueprintDataProvider']
    assert provider['schematicName']==family+'1'
    assert provider['corner1']==dict(x=-a,y=-2,z=-2)
    assert provider['corner2']==dict(x=w-1-a,y=4,z=w-3)
    assert provider['posTagMap']==[{'tagPos':dict(x=0,y=0,z=0),'tagNameList':[{'tagName':'shaft=infrastructure/mineshafts/'+family+'shaft1.blueprint'}]}]
    for x in (a-2,a+2):
        assert tiles[x,2,2]['id']=='minecolonies:rack'
        assert block(surface,(x,2,2))['Name']=='minecolonies:blockminecoloniesrack'
        assert block(surface,(x,2,3))==AIR and block(surface,(x,3,3))==AIR
    assert shaft['tile_entities']==[]
    # Descent must remain backed and connect through both overlap levels.
    for y in range(1,d):
        assert block(shaft,(3,y,5))==LADDER
        assert block(shaft,(2,y,5))==STONE
        assert block(shaft,(4,y,5))==AIR
    for y in range(5):
        assert block(surface,(3,y,5))==LADDER
        assert block(surface,(2,y,5))==STONE
    for x in (3,4):
        assert block(surface,(x,1,4))==STONE
        for y in (2,3,4):assert block(surface,(x,y,4))==AIR
    # The surface must not pre-excavate ground or roof over the hole.
    for x in range(3,w-3):
        for z in range(5,w-3):
            if (x,z)==(3,5):continue
            for y in (0,1):assert block(surface,(x,y,z))==KEEP
            for y in range(2,7):assert block(surface,(x,y,z))==AIR
    # Explicit shaft excavation, permanent floor, sealed perimeter, untouched exterior.
    for x in range(w):
        for z in range(w):
            inside=2<=x<=w-3 and 4<=z<=w-3
            edge=inside and (x in (2,w-3) or z in (4,w-3))
            for y in range(d):
                state=block(shaft,(x,y,z))
                if not inside:assert state==KEEP
                elif y==0 or edge:assert state==STONE
                elif (x,z)==(3,5):assert state==LADDER
                elif state['Name']=='minecraft:wall_torch':
                    assert x==a and z==5 and state['Properties']=={'facing':'south'}
                    assert block(shaft,(x,y,z-1))==STONE
                else:assert state==AIR
    # Surface/shaft supports cannot disagree in their shared world-space cells.
    for sy,qy in ((0,d-2),(1,d-1)):
        for x in range(w):
            for z in range(w):
                q=block(shaft,(x,qy,z));s=block(surface,(x,sy,z))
                if q not in (KEEP,AIR):assert q==s,(x,sy,z,q,s)
    # World primary placement H-2 puts the shaft top at the walking-floor block H-1.
    for h in (80,120):
        origin=h-2-shaft['optional_data']['structurize']['primary_offset']['y']
        assert origin==h-d and origin+d-1==h-1

class QuarryContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.implementation=ROOT/'tools/arcology_quarry.py'
        cls.temp=tempfile.TemporaryDirectory(prefix='quarry-contract-',dir=ROOT/'.setup')
        cls.addClassCleanup(cls.temp.cleanup)
        cls.before=hashes(SOURCE)
        cls.generator=None
        if cls.implementation.exists():
            spec=importlib.util.spec_from_file_location('arcology_quarry',cls.implementation)
            cls.generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(cls.generator)
            cls.paths=cls.generator.generate(SOURCE,Path(cls.temp.name)/'a')
            cls.repeat=cls.generator.generate(SOURCE,Path(cls.temp.name)/'b')
    def ready(self):
        self.assertIsNotNone(self.generator,'Isolated quarry generator is not implemented')
    def test_surface_shaft_identity_alignment_and_safe_excavation(self):
        self.ready();self.assertEqual(len(self.paths),4)
        for family,w,d,a in SPECS:
            root=Path(self.temp.name)/'a'
            contract(load(root/'fundamentals'/f'{family}1.blueprint')[0],load(root/'infrastructure/mineshafts'/f'{family}shaft1.blueprint')[0],family,w,d,a)
    def test_typed_nbt_and_deterministic_bytes(self):
        self.ready()
        for p,r in zip(self.paths,self.repeat):
            self.assertEqual(p.read_bytes(),r.read_bytes())
            n,t,_=load(p)
            for field,kind in {'size_x':2,'size_y':2,'size_z':2,'blocks':11,'version':1,'mcversion':3,'palette':9,'tile_entities':9}.items():
                self.assertEqual(t.value[field].kind,kind,field)
            self.assertEqual(t.value['tile_entities'].value[0],10)
            for tile in t.value['tile_entities'].value[1]:
                # Arcology tile coordinates are TAG_Short, distinct from tagPos TAG_Int.
                for axis in 'xyz':self.assertEqual(tile.value[axis].kind,2)
    def test_original_assets_are_preserved(self):
        self.ready();self.assertEqual(self.before,hashes(SOURCE))
    def test_rejects_wrong_tag_missing_support_and_obstructed_landing(self):
        self.ready();r=Path(self.temp.name)/'a'
        surface=load(r/'fundamentals/simplequarry1.blueprint')[0]
        shaft=load(r/'infrastructure/mineshafts/simplequarryshaft1.blueprint')[0]
        for bad in ('tag_order','shaft_path','anchor','support','landing','roof','surface_clear','shaft_hut'):
            s=copy.deepcopy(surface);q=copy.deepcopy(shaft)
            hut=next(t for t in s['tile_entities'] if t.get('type')=='minecolonies:simplequarry')
            tags=hut['blueprintDataProvider']['posTagMap'][0]['tagNameList']
            if bad=='tag_order':tags.insert(0,{'tagName':'unrelated'})
            elif bad=='shaft_path':tags[0]['tagName']='shaft=wrong'
            elif bad=='anchor':q['optional_data']['structurize']['primary_offset']['y']-=1
            elif bad=='support':set_block(q,(2,20,5),AIR)
            elif bad=='landing':set_block(s,(3,2,4),STONE)
            elif bad=='roof':set_block(s,(7,6,7),STONE)
            elif bad=='surface_clear':set_block(s,(7,1,7),AIR)
            elif bad=='shaft_hut':set_block(q,(7,20,2),{'Name':'minecolonies:simplequarry','Properties':{'facing':'south'}})
            with self.subTest(mutation=bad),self.assertRaises(AssertionError):
                contract(s,q,'simplequarry',15,32,7)
    def test_refuses_source_overlap_and_unrecognized_destination(self):
        self.ready()
        for destination in (SOURCE,SOURCE/'candidate',SOURCE.parent,Path(self.temp.name)):
            with self.subTest(destination=str(destination)),self.assertRaises(ValueError):self.generator.generate(SOURCE,destination)

if __name__=='__main__':unittest.main(verbosity=2)
