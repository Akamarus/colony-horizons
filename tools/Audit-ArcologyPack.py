import gzip,struct,json,hashlib
from pathlib import Path
class NBT:
 def __init__(self,data): self.data=data;self.p=0
 def read(self,n):
  b=self.data[self.p:self.p+n];self.p+=n
  if len(b)!=n: raise ValueError('truncated')
  return b
 def num(self,f): return struct.unpack('>'+f,self.read(struct.calcsize('>'+f)))[0]
 def string(self): return self.read(self.num('H')).decode('utf-8')
 def tag(self,t):
  if t in (1,2,3,4,5,6): return self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return list(self.read(self.num('i')))
  if t==8:return self.string()
  if t==9:
   subtype=self.num('B');n=self.num('i');return [self.tag(subtype) for _ in range(n)]
  if t==10:
   out={}
   while True:
    subtype=self.num('B')
    if subtype==0:return out
    name=self.string();out[name]=self.tag(subtype)
  if t in (11,12):return [self.num('i' if t==11 else 'q') for _ in range(self.num('i'))]
  raise ValueError(t)
 def root(self):
  t=self.num('B');assert t==10;self.string();out=self.tag(t);assert self.p==len(self.data);return out
import argparse,re,csv,collections

def audit(pack,source,out):
 pack=pack.resolve();out.mkdir(parents=True,exist_ok=True)
 registry=source/'com/minecolonies/api/colony/buildings/ModBuildings.java'
 initializer=source/'com/minecolonies/apiimp/initializer/ModBuildingsInitializer.java'
 constants=dict(re.findall(r'public static final String\s+(\w+)\s*=\s*"([^"]+)"',registry.read_text()))
 init=re.sub(r'/\*.*?\*/','',initializer.read_text(),flags=re.S)
 init=re.sub(r'//[^\n]*','',init)
 registered=[constants[k] for k in re.findall(r'DEFERRED_REGISTER.register\(ModBuildings\.(\w+)',init)]
 rows=[];families=collections.defaultdict(list);alltags=collections.Counter();blockuse=collections.Counter();errors=[]
 for path in sorted(pack.rglob('*.blueprint')):
  raw=path.read_bytes();rel=path.relative_to(pack).as_posix()
  try:
   n=NBT(gzip.decompress(raw)).root();sx,sy,sz=[n['size_'+k] for k in 'xyz'];volume=sx*sy*sz
   assert min(sx,sy,sz)>0 and len(n['blocks'])==(volume+1)//2,'invalid dimensions/block count'
   indexes=[idx for word in n['blocks'] for idx in ((word>>16)&65535,word&65535)][:volume]
   palette=n['palette'];assert all(i<len(palette) for i in indexes),'invalid palette index'
   counts=collections.Counter(palette[i]['Name'] for i in indexes);blockuse.update(counts)
   anchor=n['optional_data']['structurize']['primary_offset'];ax,ay,az=[anchor[k] for k in 'xyz']
   assert 0<=ax<sx and 0<=ay<sy and 0<=az<sz,'invalid anchor'
   anchor_block=palette[indexes[(ay*sz+az)*sx+ax]]['Name']
   tes=n.get('tile_entities',[]);primary=[t for t in tes if all(t.get(k)==anchor[k] for k in 'xyz')]
   types=[t.get('type',t.get('id','')).removeprefix('minecolonies:') for t in primary]
   building=next((t for t in types if t in registered),'')
   tags=collections.Counter(v['tagName'] for t in tes for p in t.get('blueprintDataProvider',{}).get('posTagMap',[]) for v in p.get('tagNameList',[]))
   alltags.update(tags)
   match=re.search(r'(\d+)$',path.stem);level=int(match[1]) if match else 0
   if building:families[building].append(level)
   foreign={k:v for k,v in counts.items() if k.split(':')[0] not in ('minecraft','minecolonies','structurize','domum_ornamentum')}
   row=dict(path=rel,sha256=hashlib.sha256(raw).hexdigest(),building=building,level=level,size=f'{sx}x{sy}x{sz}',anchor=f'{ax},{ay},{az}',anchor_block=anchor_block,mcversion=n.get('mcversion'),required_mods=';'.join(n.get('required_mods',[])),tags=';'.join(sorted(tags)),outside_profile_blocks=json.dumps(foreign,sort_keys=True),petrified_oak_slabs=counts.get('minecraft:petrified_oak_slab',0))
   rows.append(row)
  except Exception as exc:errors.append(dict(path=rel,error=str(exc)))
 summary=dict(scope='Static installed-pack audit; not a native decode or all-building gameplay test',pack=str(pack),metadata=json.loads((pack/'pack.json').read_text()),pack_metadata_sha256=hashlib.sha256((pack/'pack.json').read_bytes()).hexdigest(),blueprints=len(rows),parse_errors=errors,building_blueprints=sum(bool(r['building']) for r in rows),other_blueprints=sum(not r['building'] for r in rows),registered_building_count=len(registered),families={k:sorted(v) for k,v in sorted(families.items())},missing_registered=[k for k in registered if k not in families],tag_occurrences=dict(sorted(alltags.items())),used_block_counts=dict(sorted(blockuse.items())),source_hashes={str(p.relative_to(source)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (registry,initializer)})
 (out/'arcology-full-audit.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
 with (out/'arcology-full-inventory.csv').open('w',newline='',encoding='utf-8') as f:
  writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
 print(json.dumps({k:v for k,v in summary.items() if k not in ('used_block_counts','source_hashes','metadata','pack','families','tag_occurrences')},indent=2))
 print('Building families:',len(families),'outside-profile blocks:',[(r['path'],r['outside_profile_blocks']) for r in rows if r['outside_profile_blocks']!='{}'])
 print('Petrified slabs:',[(r['path'],r['petrified_oak_slabs']) for r in rows if r['petrified_oak_slabs']])
 print('Tags:',summary['tag_occurrences'])
 if errors:raise SystemExit(1)

if __name__=='__main__':
 p=argparse.ArgumentParser(description='Read-only Arcology blueprint inventory against extracted pinned MineColonies sources; Python standard library only.')
 p.add_argument('--pack',type=Path,required=True);p.add_argument('--source',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
 a=p.parse_args();audit(a.pack,a.source,a.output)
