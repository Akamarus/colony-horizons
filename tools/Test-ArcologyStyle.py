"""Style-boundary contracts against the original Arcology residence module."""
import copy
from pathlib import Path
import unittest
from arcology_nbt import load,block,set_block
import arcology_stable,arcology_gatehouse,arcology_crops,arcology_quarry
ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('blueprints/arcology-horizons')

def contract(n,reference,gate):
 sx,sy,sz=(n['size_'+c] for c in 'xyz')
 assert sx%5==0 and sz%5==0 and sy==6
 assert n['optional_data']['structurize']['primary_offset']['y']==1
 # Hand-selected repeated five-cell facade from the original residence.
 for z in range(sz):
  for y in (0,1,2,3,4,5):
   expected=block(reference,(0,y,z%5))
   for x in (0,sx-1):
    expected=block(reference,(x%5,y,z%5))
    assert block(n,(x,y,z))==expected,('side seam',x,y,z,block(n,(x,y,z)),expected)
 # Paired roof seams at 4/5, 9/10 etc, not a single line every five cells.
 for x in range(sx):
  for z in range(sz):
   assert block(n,(x,5,z))==block(reference,(x%5,5,z%5)),('roof module',x,z)
 # Broad entrance remains clear despite the original module's narrower opening.
 for z in ((0,sz-1) if gate else (0,)):
  for x in range(5,10):
   for y in range(1,5):assert block(n,(x,y,z))['Name']=='minecraft:air'
 # Compare Domum texture identity as well as the block type.
 refs={tuple(t[k] for k in 'xyz'):t for t in reference['tile_entities']}
 tiles={tuple(t[k] for k in 'xyz'):t for t in n['tile_entities']}
 for p,t in tiles.items():
  if t.get('id')=='domum_ornamentum:materially_retexturable':
   rp=(p[0]%5,p[1],p[2]%5)
   assert t['textureData']==refs[rp]['textureData'],('roof texture',p)

class StyleContracts(unittest.TestCase):
 def test_stable_all_levels_match_original_module(self):
  for level in range(1,6):
   with self.subTest(level=level):contract(arcology_stable._build(SOURCE,level)[0],load(SOURCE/f'fundamentals/citizen{level}.blueprint')[0],False)
 def test_gatehouse_all_tiers_match_original_module(self):
  for level,equivalent in [(1,1),(2,3),(3,5)]:
   with self.subTest(level=level):contract(arcology_gatehouse._build(SOURCE,level)[0],load(SOURCE/f'fundamentals/citizen{equivalent}.blueprint')[0],True)
 def test_lanterns_have_full_roof_support(self):
  for generator,levels in [(arcology_stable,range(1,6)),(arcology_gatehouse,range(1,4))]:
   for level in levels:
    n=generator._build(SOURCE,level)[0]
    for x in range(n['size_x']):
     for z in range(n['size_z']):
      if block(n,(x,4,z))['Name']=='minecraft:lantern':
       self.assertIn(block(n,(x,5,z))['Name'],{'minecraft:oak_planks','minecraft:cobblestone','domum_ornamentum:beige_stone_bricks'},(level,x,z))
 def test_shell_does_not_copy_residence_furniture(self):
  for generator,level in [(arcology_stable,5),(arcology_gatehouse,3)]:
   n=generator._build(SOURCE,level)[0]
   self.assertFalse(any(block(n,(x,y,z))['Name']=='minecraft:red_carpet' for x in range(n['size_x']) for y in range(1,5) for z in range(n['size_z'])))
 def test_fields_repeat_original_roof_and_open_side_modules(self):
  ref=load(SOURCE/'fundamentals/citizen1.blueprint')[0]
  for crop in arcology_crops.CATEGORIES:
   n=arcology_crops._build(SOURCE,crop)[0]
   with self.subTest(crop=crop):
    for x in range(10):
     for z in range(10):self.assertEqual(block(n,(x,5,z)),block(ref,(x%5,5,z%5)),('roof',crop,x,z))
    for x in (0,9):
     for z in range(10):
      for y in (1,2,3):
       expected=block(ref,(x%5,y,z%5)) if z%5 in (0,4) else {'Name':'minecraft:air'}
       self.assertEqual(block(n,(x,y,z)),expected,('side',crop,x,y,z))
    lamps=[(x,y,z) for x in range(10) for y in range(1,5) for z in range(10) if block(n,(x,y,z))['Name']=='minecraft:lantern']
    self.assertEqual(len(lamps),4)
    for x,y,z in lamps:
     self.assertEqual(block(n,(x,y,z))['Properties']['hanging'],'true')
     self.assertIn(block(n,(x,y+1,z))['Name'],{'minecraft:spruce_planks','minecraft:oak_planks'})
 def test_quarry_canopies_match_original_roof(self):
  for family,w,d,a in arcology_quarry.SPECS:
   n=arcology_quarry._build(SOURCE,family,w,d,a)[0][1]
   ref=load(SOURCE/f'fundamentals/citizen{1 if family=="simplequarry" else 3}.blueprint')[0]
   for x in range(5):
    for z in range(5):self.assertEqual(block(n,(a-2+x,6,z)),block(ref,(x,5,z)),(family,x,z))
 def test_bad_seam_rejected(self):
  n=arcology_stable._build(SOURCE,1)[0];ref=load(SOURCE/'fundamentals/citizen1.blueprint')[0]
  for p in [(0,2,2),(4,5,2),(5,5,2)]:
   broken=copy.deepcopy(n);set_block(broken,p,{'Name':'minecraft:gold_block'})
   with self.assertRaises(AssertionError):contract(broken,ref,False)
if __name__=='__main__':unittest.main(verbosity=2)
