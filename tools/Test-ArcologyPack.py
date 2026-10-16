"""Independent asset contracts. Run on the original to reproduce defects, then the generated pack."""
import argparse,gzip,importlib.util,json
from pathlib import Path
s=importlib.util.spec_from_file_location('audit',Path(__file__).with_name('Audit-ArcologyPack.py'));audit=importlib.util.module_from_spec(s);s.loader.exec_module(audit)
p=argparse.ArgumentParser();p.add_argument('--pack',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--complete',action='store_true');p.add_argument('--source',type=Path);a=p.parse_args()
rows=[]
def check(name,fn):
 try:fn();rows.append(dict(case=name,status='PASS'))
 except Exception as exc:rows.append(dict(case=name,status='FAIL',detail=str(exc)))
def need(b,message):
 if not b:raise AssertionError(message)
def load(path):return audit.NBT(gzip.decompress((a.pack/path).read_bytes())).root()
def markers(n):
 for t in n.get('tile_entities',[]):
  for entry in t.get('blueprintDataProvider',{}).get('posTagMap',[]):
   pos=tuple(t[k]+entry['tagPos'][k] for k in 'xyz')
   for tag in entry['tagNameList']:yield tag['tagName'],pos

def block(n,pos):
 x,y,z=pos;need(0<=x<n['size_x'] and 0<=y<n['size_y'] and 0<=z<n['size_z'],f'marker outside: {pos}')
 i=(y*n['size_z']+z)*n['size_x']+x;idx=(n['blocks'][i//2]>>(16 if i%2==0 else 0))&65535;return n['palette'][idx]['Name']

def miner(level):
 n=load(f'fundamentals/miner{level}.blueprint');tags=dict(markers(n))
 for tag,material in [('ladder','minecraft:ladder'),('cobble','minecraft:cobblestone')]:
  need(tag in tags,f'Mine L{level}: missing {tag}');need(block(n,tags[tag])==material,f'Mine L{level}: incorrect {tag} marker block')

def nether(level):
 n=load(f'mystic/netherworker{level}.blueprint');tags=list(markers(n));portals=[pos for tag,pos in tags if tag=='portal']
 need(len(portals)==1,f'Nether Mine L{level}: requires one unambiguous portal marker; got {portals}')
 need(block(n,portals[0])=='minecraft:obsidian','Portal marker must designate frame floor')
 need(any(tag=='vault' for tag,pos in tags),'Missing vault marker')

def plantation(level):
 n=load(f'agriculture/horticulture/plantation{level}.blueprint');tags=list(markers(n));kinds={tag for tag,pos in tags}
 wanted={'sugar'}|({'cactus'} if level>=2 else set())|({'bamboo'} if level>=3 else set())
 need(wanted<=kinds,f'Plantation L{level}: missing crop markers {wanted-kinds}')
 for tag,pos in tags:
  if tag in ('sugar','cactus','bamboo'):need(block(n,pos) in ('minecraft:sand','minecraft:dirt','minecraft:grass_block'),f'{tag} marker has unsuitable substrate at {pos}')

def plantation_irrigation(level):
 n=load(f'agriculture/horticulture/plantation{level}.blueprint')
 floor=n['optional_data']['structurize']['primary_offset']['y']-1
 from arcology_nbt import block as state_at
 wet=[(x,y,z) for y in range(n['size_y']) for z in range(n['size_z']) for x in range(n['size_x']) if state_at(n,(x,y,z)).get('Properties',{}).get('waterlogged')=='true']
 need(wet and all(y<=floor for x,y,z in wet),'Raised waterlogged stairs flood the walking floor during construction and trap native workers')
 for tag,pos in markers(n):
  if tag=='sugar':
   need(pos[1]==floor,'Sugarcane soil must follow lowered irrigation channel')
   need(any((pos[0]+dx,pos[1],pos[2]+dz) in wet for dx,dz in ((1,0),(-1,0),(0,1),(0,-1))),'Sugarcane plot lost its adjacent irrigation')

def plantation_crop_clearance(level):
 n=load(f'agriculture/horticulture/plantation{level}.blueprint')
 for tag,pos in markers(n):
  height=6 if tag=='bamboo' else 3 if tag in ('sugar','cactus') else 0
  for dy in range(1,height+1):
   q=(pos[0],pos[1]+dy,pos[2])
   if q[1]<n['size_y']:need(block(n,q)=='minecraft:air',f'{tag} cannot reach native harvest height {height}: obstruction at {q}')

def every_marker_inside():
 errors=[]
 for path in a.pack.rglob('*.blueprint'):
  n=load(path.relative_to(a.pack))
  for tag,pos in markers(n):
   try:block(n,pos)
   except AssertionError:errors.append(f'{path.relative_to(a.pack)} {tag} {pos}')
 need(not errors,'; '.join(errors[:12])+f' (total {len(errors)})')

def materials():
 forbidden={'minecraft:petrified_oak_slab','blocksyouneed_luna:steel_bent_pipe'}
 for path in a.pack.rglob('*.blueprint'):
  n=load(path.relative_to(a.pack));bad=forbidden&{v['Name'] for v in n['palette']};need(not bad,f'{path.name}: unavailable materials {bad}')
def archery(level):
 n=load(f'military/archery{level}.blueprint')
 stands={pos for tag,pos in markers(n) if tag=='work'}
 expected={tuple(t[k] for k in 'xyz') for t in n['tile_entities'] if block(n,tuple(t[k] for k in 'xyz'))=='structurize:blocktagsubstitution'}
 need(stands==expected,f'Archery L{level}: standing positions incomplete')
 targets=sum(block(n,(x,y,z))=='minecraft:target' for x in range(n['size_x']) for y in range(n['size_y']) for z in range(n['size_z']))
 need(targets==len(expected) and targets>0,f'Archery L{level}: requires {len(expected)} native targets, got {targets}')
for level in range(1,6):
 check(f'archery_{level}',lambda level=level:archery(level))
 check(f'plantation_irrigation_{level}',lambda level=level:plantation_irrigation(level))
 check(f'plantation_crop_clearance_{level}',lambda level=level:plantation_crop_clearance(level))
for l in range(1,6):
 check(f'mine_{l}',lambda l=l:miner(l));check(f'nether_mine_{l}',lambda l=l:nether(l));check(f'plantation_{l}',lambda l=l:plantation(l))
check('marker_bounds_all_blueprints',every_marker_inside);check('survival_materials',materials)
if a.source:
 def preserve_working_plantations():
  for level in (1,2,4,5):
   rel=f'agriculture/horticulture/plantation{level}.blueprint'
   original=audit.NBT(gzip.decompress((a.source/rel).read_bytes())).root()
   expected={(tag,(pos[0],0,pos[2]) if tag=='sugar' else {(3,0,1):(6,0,3),(5,0,1):(6,0,1),(3,0,3):(2,0,1),(3,0,5):(2,0,3),(3,0,7):(2,0,5),(5,0,3):(4,0,3),(5,0,5):(4,0,5),(5,0,7):(4,0,7)}.get(pos,pos) if tag=='bamboo' else (13,0,1) if level in (4,5) and tag=='cactus' and pos==(9,0,1) else pos) for tag,pos in markers(original)}
   need(expected==set(markers(load(rel))),f'Changed Plantation L{level} capacity or undocumented crop positions')
 check('preserve_existing_crop_capacity',preserve_working_plantations)
 def preserve_source_and_nbt():
  import hashlib
  from arcology_nbt import load as typed_load,rebuild,encode,string
  manifest=json.loads((a.pack/'compatibility-manifest.json').read_text())
  for relative,digest in manifest['source_hashes'].items():
   original=a.source/relative
   need(hashlib.sha256(original.read_bytes()).hexdigest()==digest,'Source changed: '+relative)
   if not relative.endswith('.blueprint'):continue
   n,t,name=typed_load(original)
   need(bytes([10])+string(name)+encode(rebuild(n,t))==gzip.decompress(original.read_bytes()),'NBT type/content roundtrip changed: '+relative)
   if relative not in manifest['repaired']:
    need(original.read_bytes()==(a.pack/relative).read_bytes(),'Untouched blueprint changed: '+relative)
 check('source_hashes_nbt_types_untouched_bytes',preserve_source_and_nbt)
# The recessed irrigation bed needs a walkable exit through its decorative border.
for level in range(1,6):
 def irrigation_exit(level=level):
  n=load(f'agriculture/horticulture/plantation{level}.blueprint')
  for x in (7,8):
   for y in (1,2):need(block(n,(x,y,13))=='minecraft:air',f'Blocked irrigation exit {x},{y},13')
 check(f'plantation_irrigation_exit_{level}',irrigation_exit)
 def bamboo_aisles(level=level):
  n=load(f'agriculture/horticulture/plantation{level}.blueprint')
  occupied={(p[0],p[1]+dy,p[2]) for tag,p in markers(n) if tag=='bamboo' for dy in range(1,7)}
  for x in (3,5,7):
   for z in range(2,9):
    for y in (1,2):need((x,y,z) not in occupied and block(n,(x,y,z))=='minecraft:air','Planted bamboo closes a continuous worker aisle')
 check(f'plantation_bamboo_aisles_{level}',bamboo_aisles)
 def hut_side_exit(level=level):
  n=load(f'agriculture/horticulture/plantation{level}.blueprint');x=11 if level==5 else 10
  for z in (11,12):need(block(n,(x,1,z))=='minecraft:air','Blocked plot-to-hut exit')
 check(f'plantation_hut_side_exit_{level}',hut_side_exit)
 def irrigation_surface(level=level):
  from arcology_nbt import block as state_at
  n=load(f'agriculture/horticulture/plantation{level}.blueprint')
  wet=[state_at(n,(x,0,z)) for x in range(n['size_x']) for z in range(n['size_z']) if state_at(n,(x,0,z)).get('Properties',{}).get('waterlogged')=='true']
  need(wet and all(v['Name']=='minecraft:spruce_slab' and v['Properties'].get('type')=='top' for v in wet),'Irrigation needs continuous upper walking surface')
 check(f'plantation_irrigation_surface_{level}',irrigation_surface)
# Pinned native BuildingConcreteMixer accepts only FLOWING_WATER amount <= 5.
# A serialized water level 1 is amount 7 and therefore never registers.
for level in range(1,6):
 def mixer_water(level=level):
  from arcology_nbt import block as state_at
  n=load(f'craftsmanship/luxury/concretemixer{level}.blueprint')
  accepted=[]
  for y in range(n['size_y']):
   for z in range(n['size_z']):
    for x in range(n['size_x']):
     state=state_at(n,(x,y,z))
     if state['Name']=='minecraft:water' and 3<=int(state.get('Properties',{}).get('level','0'))<=7:accepted.append((x,y,z))
  need(bool(accepted),'No shallow flowing-water positions accepted by native Concrete Mixer')
 check(f'concretemixer_native_water_{level}',mixer_water)
 def mixer_channel(level=level):
  from arcology_nbt import block as state_at
  n=load(f'craftsmanship/luxury/concretemixer{level}.blueprint')
  need((n['size_x'],n['size_y'],n['size_z'])==(5,6,5),'Mixer module footprint changed')
  wet={(x,y,z) for y in range(n['size_y']) for z in range(n['size_z']) for x in range(n['size_x']) if state_at(n,(x,y,z)).get('Properties',{}).get('waterlogged')=='true' or (block(n,(x,y,z))=='minecraft:water' and state_at(n,(x,y,z)).get('Properties',{}).get('level')=='0')}
  need(wet=={(0,1,1)},f'Channel must have one isolated source, got {wet}')
  source=state_at(n,(0,1,1));need(source['Name'].endswith('_stairs') and source['Properties'].get('half')=='bottom' and source['Properties'].get('facing')=='west','Source rim must contain outward flow')
  solid={'minecraft:spruce_planks','minecraft:oak_planks','minecraft:cobblestone','minecraft:stone','domum_ornamentum:beige_stone_bricks'}
  for z in (1,2,3):
   q=(1,1,z);state=state_at(n,q)
   need(state['Name']=='minecraft:water' and int(state['Properties']['level'])==z,f'Water at {q} must match horizontal source distance {z}')
   need(block(n,(1,0,z)) in solid,'Channel must have solid floor, no downward flow shortcut')
   need(block(n,(1,2,z))=='minecraft:air','Concrete workspace headroom obstructed')
   wall=state_at(n,(2,1,z))
   need(wall['Name'] in solid or (wall['Name'].endswith('_stairs') and wall['Properties'].get('facing')=='west' and wall['Properties'].get('half')=='bottom' and wall['Properties'].get('waterlogged')=='false'),'Worker-side channel wall leaks or refills from a source')
  for q in ((0,1,2),(0,1,3),(1,1,0),(1,1,4)):
   need(block(n,q) in solid,f'Channel perimeter must be solid at {q}')
  if a.source:
   original=audit.NBT(gzip.decompress((a.source/f'craftsmanship/luxury/concretemixer{level}.blueprint').read_bytes())).root()
   changed={(x,y,z) for y in range(n['size_y']) for z in range(n['size_z']) for x in range(n['size_x']) if state_at(n,(x,y,z))!=state_at(original,(x,y,z))}
   need(changed=={(0,1,2),(0,1,3),(1,1,2),(1,1,3)},f'Mixer repair changed unrelated geometry: {changed}')
   need(n['tile_entities']==original['tile_entities'] and n['optional_data']==original['optional_data'],'Mixer metadata changed')
 check(f'concretemixer_stable_channel_{level}',mixer_channel)


# Native BuildingCombatAcademy registers CarvedPumpkinBlock over HayBlock, not plain pumpkins.
# Expected dummy positions come from independently inspected original level geometry.
for level in range(1,6):
 def academy_targets(level=level):
  from arcology_nbt import block as state_at
  n=load(f'military/combatacademy{level}.blueprint')
  expected={(2,2,4)} | ({(2,2,6)} if level>=2 else set()) | ({(2,2,8)} if level>=3 else set()) | ({(2,2,2)} if level>=4 else set()) | ({(5,2,2)} if level>=5 else set())
  actual={(x,y,z) for y in range(1,n['size_y']) for z in range(n['size_z']) for x in range(n['size_x']) if block(n,(x,y,z)) in ('minecraft:carved_pumpkin','minecraft:jack_o_lantern') and block(n,(x,y-1,z))=='minecraft:hay_block'}
  need(actual==expected,f'Combat Academy L{level}: native dummy heads expected {sorted(expected)}, got {sorted(actual)}; plain pumpkins do not register')
  for pos in actual:
   need(state_at(n,pos).get('Properties',{}).get('facing')=='east','Dummy should face the clear inner practice aisle; original plain pumpkin had no facing')
   need(block(n,(pos[0]+1,pos[1],pos[2]))=='minecraft:air','Dummy face is obstructed')
  if a.source:
   original=audit.NBT(gzip.decompress((a.source/f'military/combatacademy{level}.blueprint').read_bytes())).root()
   need(all(n[k]==original[k] for k in ('size_x','size_y','size_z','tile_entities','optional_data')),'Academy repair changed footprint or metadata')
   changed={(x,y,z) for y in range(n['size_y']) for z in range(n['size_z']) for x in range(n['size_x']) if state_at(n,(x,y,z))!=state_at(original,(x,y,z))}
   need(changed==expected,f'Academy repair changed unrelated geometry: {changed}')
 check(f'combatacademy_native_dummy_{level}',academy_targets)

# Ladder crossing candidate: exact asset scope; native access and containment remain separate tests.
for level in range(1,6):
 def chicken_barrier(level=level):
  from arcology_nbt import block as state_at
  rel=f'agriculture/husbandry/chickenherder{level}.blueprint';n=load(rel)
  for y in (1,2):
   need(block(n,(2,y,4))=='minecraft:oak_planks','Two-high permanent chicken barrier missing')
   for z,facing in ((3,'north'),(5,'south')):
    state=state_at(n,(2,y,z))
    need(state=={'Name':'minecraft:ladder','Properties':{'facing':facing,'waterlogged':'false'}},f'Climb access missing or unsupported at {(2,y,z)}')
  for y in (3,4):
   for z in (3,4,5):need(block(n,(2,y,z))=='minecraft:air','Two-block worker clearance above ladder crossing obstructed')
  if a.source:
   original=audit.NBT(gzip.decompress((a.source/rel).read_bytes())).root()
   need(all(n[k]==original[k] for k in ('size_x','size_y','size_z','tile_entities','optional_data')),'Chicken repair changed footprint or metadata')
   changed={(x,y,z) for y in range(n['size_y']) for z in range(n['size_z']) for x in range(n['size_x']) if state_at(n,(x,y,z))!=state_at(original,(x,y,z))}
   need(changed=={(2,y,z) for y in (1,2) for z in (3,4,5)},f'Chicken ladder candidate changed unrelated geometry: {changed}')
 check(f'chickenherder_ladder_crossing_{level}',chicken_barrier)

if a.complete:
 for name,levels in [('military/gatehouse',3),('military/stable',5),('fundamentals/simplequarry',1),('fundamentals/mediumquarry',1)]:
  for l in range(1,levels+1):check(f'present_{name}_{l}',lambda name=name,l=l:load(name+str(l)+'.blueprint'))
 for name in ('cocoa','vine','kelp','seagrass','seapickle','glowberry','weepingvine','twistingvine','crimson','warped'):
  check('crop_'+name,lambda name=name:load('infrastructure/plantationfields/'+name+'1.blueprint'))
if a.complete:
 expected={f'military/{kind}{level}.blueprint' for kind,count in [('stable',5),('gatehouse',3)] for level in range(1,count+1)}
 expected|={f'fundamentals/{kind}quarry1.blueprint' for kind in ('simple','medium')}
 expected|={f'infrastructure/mineshafts/{kind}quarryshaft1.blueprint' for kind in ('simple','medium')}
 expected|={f'infrastructure/plantationfields/{kind}1.blueprint' for kind in ('cocoa','vine','kelp','seagrass','seapickle','glowberry','weepingvine','twistingvine','crimson','warped')}
 for name in ('simple','medium'):check('shaft_'+name,lambda name=name:load('infrastructure/mineshafts/'+name+'quarryshaft1.blueprint'))
 def complete_manifest():
  manifest=json.loads((a.pack/'compatibility-manifest.json').read_text())
  need(manifest.get('include_extensions') is True,'Complete assembly flag missing')
  need(len(manifest['new_blueprints'])==22 and set(manifest['new_blueprints'])==expected,'Missing/duplicate/unexpected declared extension assets')
 check('complete_manifest',complete_manifest)

a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(rows,indent=2)+'\n')
for r in rows:print(r)
raise SystemExit(1 if any(r['status']=='FAIL' for r in rows) else 0)
