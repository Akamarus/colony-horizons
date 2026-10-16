"""Generate a private compatibility copy; never edit the source pack."""
import argparse,copy,hashlib,json,tempfile
from pathlib import Path
from arcology_nbt import load,save,block,set_block

def valid_markers(n):
 result=[]
 for tile in n['tile_entities']:
  for entry in tile.get('blueprintDataProvider',{}).get('posTagMap',[]):
   pos=tuple(tile[k]+entry['tagPos'][k] for k in 'xyz')
   if block(n,pos) is not None:result.append((copy.deepcopy(entry),pos))
 return result

def main(source,output,include_extensions=False):
 source=source.resolve();output=output.resolve()
 if source==output or source in output.parents or output in source.parents:raise ValueError('Output must be separate from source')
 if output.exists() and not (output/'.arcology-horizons-generated').exists():raise ValueError('Refusing to modify an unrecognized existing output directory')
 if (output/'compatibility-manifest.json').is_file():
  old_manifest=json.loads((output/'compatibility-manifest.json').read_text())
  if bool(old_manifest.get('include_extensions',False))!=include_extensions:raise ValueError('Output has a different extension mode; use a separate output directory')
 before={p.relative_to(source).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in source.rglob('*') if p.is_file()}
 output.mkdir(parents=True,exist_ok=True)
 (output/'.arcology-horizons-generated').write_text('Generated from external user-owned Arcology assets; do not redistribute without appropriate rights.\n')
 print('Source hashed; copying assets',flush=True)
 for path in source.rglob('*'):
  if path.is_file():
   dest=output/path.relative_to(source);dest.parent.mkdir(parents=True,exist_ok=True)
   data=path.read_bytes()
   if not dest.exists() or dest.read_bytes()!=data:dest.write_bytes(data)
 print('Assets copied; applying repairs',flush=True)
 donors={family:load(source/(family+str(level)+'.blueprint'))[0] for family,level in [('fundamentals/miner',1),('mystic/netherworker',1),('agriculture/horticulture/plantation',4),('military/archery',1)]}
 changes=[]
 for path in sorted(source.rglob('*.blueprint')):
  n,template,name=load(path);old=copy.deepcopy(n);rel=path.relative_to(source).as_posix()
  for tile in n['tile_entities']:
   provider=tile.get('blueprintDataProvider')
   if provider:
    provider['posTagMap']=[e for e in provider.get('posTagMap',[]) if block(n,tuple(tile[k]+e['tagPos'][k] for k in 'xyz')) is not None]
  family=next((f for f in donors if rel.startswith(f) and rel[len(f):].split('.')[0].isdigit()),None)
  if family and not (family=='agriculture/horticulture/plantation' and rel!='agriculture/horticulture/plantation3.blueprint'):
   donor=donors[family];anchor=n['optional_data']['structurize']['primary_offset'];primary=next(t for t in n['tile_entities'] if all(t.get(k)==anchor[k] for k in 'xyz'))
   entries=[]
   for entry,pos in valid_markers(donor):
    state=block(n,pos)
    if state is None:continue
    tags={v['tagName'] for v in entry['tagNameList']}
    if tags&{'sugar','cactus','bamboo'} and state['Name'] not in ('minecraft:sand','minecraft:dirt','minecraft:grass_block'):continue
    entry['tagPos']={k:pos[i]-primary[k] for i,k in enumerate('xyz')};entries.append(entry)
   primary['blueprintDataProvider']['posTagMap']=entries
  if rel in ('agriculture/horticulture/plantation4.blueprint','agriculture/horticulture/plantation5.blueprint'):
   # The original (9,0,1) plot touches a solid outer column. Move the plot,
   # not the column, preserving the module frame and number of crop positions.
   old_plot=(9,0,1);new_plot=(13,0,1)
   old_floor=copy.deepcopy(block(n,old_plot));new_floor=copy.deepcopy(block(n,new_plot))
   if old_floor['Name']!='minecraft:sand':raise ValueError('Unexpected cactus source geometry: '+rel)
   set_block(n,old_plot,new_floor);set_block(n,new_plot,old_floor)
   for tile in n['tile_entities']:
    for entry in tile.get('blueprintDataProvider',{}).get('posTagMap',[]):
     pos=tuple(tile[k]+entry['tagPos'][k] for k in 'xyz')
     if pos==old_plot and any(t['tagName']=='cactus' for t in entry['tagNameList']):
      entry['tagPos']={k:new_plot[i]-tile[k] for i,k in enumerate('xyz')}
  if family=='agriculture/horticulture/plantation':
   # Native builder and planter both become pinned against the raised wet stair
   # channel. Recess irrigation and its sugarcane soil into the existing floor.
   # Keep horizontal layout, crop capacity, frame, and dry decorative borders.
   sugar={pos for entry,pos in valid_markers(n) if any(t['tagName']=='sugar' for t in entry['tagNameList'])}
   wet={(x,y,z) for y in range(n['size_y']) for z in range(n['size_z']) for x in range(n['size_x']) if block(n,(x,y,z)).get('Properties',{}).get('waterlogged')=='true'}
   for pos in sorted(sugar|wet):
    if pos[1]!=1:raise ValueError('Unexpected raised irrigation geometry: '+rel)
    state=({'Name':'minecraft:spruce_slab','Properties':{'type':'top','waterlogged':'true'}} if pos in wet else copy.deepcopy(block(n,pos)))
    set_block(n,(pos[0],0,pos[2]),state);set_block(n,pos,{'Name':'minecraft:air'})
   for tile in n['tile_entities']:
    for entry in tile.get('blueprintDataProvider',{}).get('posTagMap',[]):
     if any(t['tagName']=='sugar' for t in entry['tagNameList']):entry['tagPos']['y']-=1
  if family=='agriculture/horticulture/plantation':
   # Align bamboo in rows: staggered planting can enclose workers on all four sides.
   # Preserve every plot and the raised beds, leaving continuous x=3/5/7 aisles.
   # Reserve open sky above every bamboo plot; never cut the module's outer beam.
   moves={(3,0,1):(6,0,3),(5,0,1):(6,0,1),(3,0,3):(2,0,1),(3,0,5):(2,0,3),(3,0,7):(2,0,5),(5,0,3):(4,0,3),(5,0,5):(4,0,5),(5,0,7):(4,0,7)}
   for old_plot,new_plot in moves.items():
    tagged=any(pos==old_plot and any(t['tagName']=='bamboo' for t in entry['tagNameList']) for entry,pos in valid_markers(n))
    if not tagged:continue
    old_floor=copy.deepcopy(block(n,old_plot));new_floor=copy.deepcopy(block(n,new_plot))
    if block(n,(new_plot[0],1,new_plot[2]))['Name']!='minecraft:air':raise ValueError('Occupied bamboo relocation: '+rel)
    set_block(n,new_plot,old_floor);set_block(n,old_plot,new_floor)
    for tile in n['tile_entities']:
     for entry in tile.get('blueprintDataProvider',{}).get('posTagMap',[]):
      pos=tuple(tile[k]+entry['tagPos'][k] for k in 'xyz')
      if pos==old_plot and any(t['tagName']=='bamboo' for t in entry['tagNameList']):entry['tagPos']={k:new_plot[i]-tile[k] for i,k in enumerate('xyz')}
   # Retain both lanterns, shifting their full roof supports one block sideways.
   for x,new_x in ((2,1),(12,13)):
    old_lamp=(x,4,2);new_lamp=(new_x,4,2);old_support=(x,5,2);new_support=(new_x,5,2)
    if block(n,old_lamp)['Name']!='minecraft:lantern' or block(n,new_lamp)['Name']!='minecraft:air':raise ValueError('Unexpected plantation light geometry: '+rel)
    set_block(n,new_lamp,copy.deepcopy(block(n,old_lamp)));set_block(n,old_lamp,{'Name':'minecraft:air'})
    set_block(n,new_support,copy.deepcopy(block(n,old_support)))
    donor_tile=next((copy.deepcopy(t) for t in n['tile_entities'] if tuple(t[k] for k in 'xyz')==old_support),None)
    n['tile_entities']=[t for t in n['tile_entities'] if tuple(t[k] for k in 'xyz')!=new_support]
    if donor_tile:
     donor_tile.update(zip('xyz',new_support));n['tile_entities'].append(donor_tile)
   removed=set()
   for entry,pos in valid_markers(n):
    if not any(t['tagName']=='bamboo' for t in entry['tagNameList']):continue
    for dy in range(1,7):
     q=(pos[0],pos[1]+dy,pos[2]);state=block(n,q)
     if state is None or state['Name']=='minecraft:air':continue
     if q[1]!=5:raise ValueError('Unexpected bamboo obstacle: '+rel+' '+str(q))
     set_block(n,q,{'Name':'minecraft:air'});removed.add(q)
   n['tile_entities']=[t for t in n['tile_entities'] if tuple(t[k] for k in 'xyz') not in removed]
  if family=='agriculture/horticulture/plantation':
   # Preserve the decorative border but give the recessed bed a two-wide exit.
   for x in (7,8):set_block(n,(x,1,13),{'Name':'minecraft:air'})
   # Native inventory delivery needs a one-block hut approach from the bed.
   side=11 if rel.endswith('5.blueprint') else 10
   for z in (11,12):set_block(n,(side,1,z),{'Name':'minecraft:air'})
  if rel in {f'craftsmanship/luxury/concretemixer{level}.blueprint' for level in range(1,6)}:
   # Native mixers require fluid amount <=5: use one source feeding a three-cell
   # channel, not three adjacent source stairs that keep every cell at amount7.
   # Solid matching rim blocks stop lateral refilling while preserving footprint.
   wall=copy.deepcopy(block(n,(2,1,2)))
   if wall['Name'] not in ('minecraft:spruce_planks','minecraft:cobblestone','minecraft:stone'):raise ValueError('Unexpected mixer rim: '+rel)
   for z in (2,3):set_block(n,(0,1,z),wall)
   for z in (1,2,3):set_block(n,(1,1,z),{'Name':'minecraft:water','Properties':{'level':str(z)}})
  if rel in {f'agriculture/husbandry/chickenherder{level}.blueprint' for level in range(1,6)}:
   # Experimental two-high barrier with native climb access on both faces.
   # The prior fence/carpet candidate prevented egg collection over 24000 ticks.
   # This six-block candidate still requires native production and containment.
   if block(n,(2,1,4))['Name']!='minecraft:oak_fence_gate' or block(n,(2,2,4))['Name']!='minecraft:air':raise ValueError('Unexpected chicken barrier geometry: '+rel)
   for z in (3,5):
    for y in (1,2):
     if block(n,(2,y,z))['Name'] not in ('minecraft:air','minecraft:white_carpet'):raise ValueError('Occupied chicken ladder access: '+rel)
   if any(block(n,(2,y,z))['Name']!='minecraft:air' for y in (3,4) for z in (3,4,5)):raise ValueError('Obstructed chicken ladder headroom: '+rel)
   for y in (1,2):
    set_block(n,(2,y,4),{'Name':'minecraft:oak_planks'})
    for z,facing in ((3,'north'),(5,'south')):set_block(n,(2,y,z),{'Name':'minecraft:ladder','Properties':{'facing':facing,'waterlogged':'false'}})
  if rel in {f'military/combatacademy{level}.blueprint' for level in range(1,6)}:
   # Native training registers CarvedPumpkinBlock over HayBlock. Original heads
   # are plain pumpkins with no facing property; face the clear inner east aisle.
   # Limit conversion to actual hay dummies, preserving all decorative pumpkins.
   for y in range(1,n['size_y']):
    for z in range(n['size_z']):
     for x in range(n['size_x']):
      pos=(x,y,z);state=block(n,pos)
      if state['Name']=='minecraft:pumpkin' and block(n,(x,y-1,z))['Name']=='minecraft:hay_block':
       facing=state.get('Properties',{}).get('facing','east')
       set_block(n,pos,{'Name':'minecraft:carved_pumpkin','Properties':{'facing':facing}})
  if family=='military/archery':
   anchor=n['optional_data']['structurize']['primary_offset']
   primary=next(t for t in n['tile_entities'] if all(t.get(k)==anchor[k] for k in 'xyz'))
   stands=[t for t in n['tile_entities'] if block(n,tuple(t[k] for k in 'xyz'))['Name']=='structurize:blocktagsubstitution']
   primary['blueprintDataProvider']['posTagMap']=[{'tagPos':{k:t[k]-primary[k] for k in 'xyz'},'tagNameList':[{'tagName':'work'}]} for t in stands]
   for stand in stands:stand['blueprintDataProvider']['posTagMap']=[{'tagPos':{'x':0,'y':0,'z':0},'tagNameList':[{'tagName':'work'}]}]
   for state in n['palette']:
    if state['Name']=='minecraft:hay_block':state.clear();state.update(Name='minecraft:target',Properties={'power':'0'})
  for state in n['palette']:
   if state['Name']=='minecraft:petrified_oak_slab':state['Name']='minecraft:oak_slab'
   elif state['Name']=='blocksyouneed_luna:steel_bent_pipe':state.clear();state.update(Name='minecraft:iron_bars',Properties={'north':'false','south':'false','east':'false','west':'false','waterlogged':'false'})
  n['required_mods']=[m for m in n.get('required_mods',[]) if m!='blocksyouneed_luna']
  if n!=old:
   save(output/path.relative_to(source),n,template,name);changes.append(rel)
 additions=[]
 if include_extensions:
  import arcology_stable,arcology_gatehouse,arcology_quarry,arcology_crops
  # Generate in fresh private directories; only explicit blueprint outputs enter
  # the final pack. Original source paths can never be overwritten by additions.
  with tempfile.TemporaryDirectory(prefix='arcology-assembly-') as scratch:
   for family,generator in [('stable',arcology_stable),('gatehouse',arcology_gatehouse),('quarry',arcology_quarry),('crops',arcology_crops)]:
    candidate=Path(scratch)/family
    for path in generator.generate(source,candidate):
     relative=path.relative_to(candidate);name=relative.as_posix()
     if name in before or name in additions:raise ValueError('Extension collides with existing blueprint: '+name)
     destination=output/relative;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(path.read_bytes());additions.append(name)
  if len(additions)!=22:raise ValueError('Incomplete extension inventory')
 metadata=json.loads((source/'pack.json').read_text());metadata.update(name='Arcology Horizons',desc='Arcology compatibility repairs and extensions for Colony Horizons. Original style by BlockyUniverse.',mods=['structurize','minecolonies','domum_ornamentum','colonyhorizons']);metadata['authors']=list(dict.fromkeys(metadata.get('authors',[])+['Colony Horizons compatibility project']))
 (output/'pack.json').write_text(json.dumps(metadata,indent=2)+'\n')
 after={p.relative_to(source).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in source.rglob('*') if p.is_file()}
 if before!=after:raise RuntimeError('Source changed during generation')
 manifest=dict(pack='Arcology Horizons',source=str(source),source_hashes=before,repaired=changes,new_blueprints=sorted(additions),include_extensions=include_extensions,verification='Scoped native results and remaining checks are documented in ARCOLOGY-BUILDING-VERIFICATION.md; complete operational acceptance is pending')
 (output/'compatibility-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 print(json.dumps(dict(output=str(output),repaired=len(changes),added=len(additions),files=changes),indent=2))

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--include-extensions',action='store_true');a=p.parse_args();main(a.source,a.output,a.include_extensions)
