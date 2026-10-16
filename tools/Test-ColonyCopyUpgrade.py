"""Offline native load/save compatibility on a disposable copy, never the installed colony."""
import tempfile
import argparse, gzip, hashlib, importlib.util, json, pathlib, shutil, struct, subprocess, time, uuid, zlib
p=argparse.ArgumentParser();p.add_argument('--jar',type=pathlib.Path,required=True);p.add_argument('--instance',type=pathlib.Path,required=True);p.add_argument('--world',required=True);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
assert a.jar.resolve().is_relative_to(root/'build/supply-dev-libs')
source=a.instance/'saves'/a.world;assert source.is_dir()
spec=importlib.util.spec_from_file_location('audit',root/'tools/Audit-ArcologyPack.py');audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
def hashes(folder):return {str(f.relative_to(folder)):hashlib.sha256(f.read_bytes()).hexdigest() for f in folder.rglob('*') if f.is_file()}
def nbt(path):
 data=path.read_bytes();return audit.NBT(gzip.decompress(data) if data[:2]==b'\x1f\x8b' else data).root()
def colony_state(world):
 result={}
 for path in (world/'minecolonies').glob('*/*/colony*.dat'):
  n=nbt(path);buildings=n['buildingManager']['buildings'];citizens=n['citizenManager']['citizens']
  result[str(path.relative_to(world))]={'owner':n.get('ownerid'),'id':n['id'],'name':n['name'],
   'buildings':sorted([{k:b.get(k) for k in ('location','type','level','isBuilt','pack','path','Containers')} for b in buildings],key=lambda b:str(b['location'])),
   'citizens':sorted([{k:c.get(k) for k in ('id','name','inventory','armorinventory','beds','child')}|{'job':{k:c.get('job',{}).get(k) for k in ('type','workPos')}} for c in citizens],key=lambda c:c['id'])}
 return result
def inventories(world):
 result={}
 for path in world.glob('region/*.mca'):
  raw=path.read_bytes()
  for i in range(1024):
   offset=int.from_bytes(raw[i*4:i*4+3],'big')*4096
   if not offset:continue
   length=int.from_bytes(raw[offset:offset+4],'big');typ=raw[offset+4];data=raw[offset+5:offset+4+length]
   if typ==2:data=zlib.decompress(data)
   elif typ==1:data=gzip.decompress(data)
   elif typ!=3:raise ValueError('Unsupported region compression')
   n=audit.NBT(data).root()
   for b in n.get('block_entities',[]):
    contents={k:v for k,v in b.items() if 'inventor' in k.lower() or 'items' in k.lower() or k in ('ForgeCaps','neoforge:attachments')}
    if contents:result[str((b.get('x'),b.get('y'),b.get('z'),b.get('id')))]=contents
 return result
before_hash=hashes(source);before=colony_state(source);before_inv=inventories(source);assert before
run=root/'build/copy'/uuid.uuid4().hex[:8];run.mkdir(parents=True);print('Colony-copy report:',run,flush=True)
(run.parent/'latest.json').write_text(json.dumps({'report':str(run)}),encoding='utf-8')
server=run/'server';server.mkdir();(run/'instance.json').write_text(json.dumps({'server':str(server)}),encoding='utf-8');world=server/'colony-copy';shutil.copytree(source,world)
assert hashes(world)==before_hash,'Copy changed while being read'
base=root/'build/isolated-supply-server';shutil.copytree(base/'libraries',server/'libraries')
for name in ('config','defaultconfigs','blueprints'):
 if (a.instance/name).is_dir():shutil.copytree(a.instance/name,server/name)
(server/'mods').mkdir()
for mod in (a.instance/'mods').glob('*.jar'):
 if mod.name.startswith(('minecolonies-','structurize-','blockui-','domum-','multipiston-','amendments-','moonlight-')):shutil.copy2(mod,server/'mods'/mod.name)
shutil.copy2(a.jar,server/'mods'/a.jar.name)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25577\nlevel-name=colony-copy\nmax-players=1\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\n')
(run/'before.json').write_text(json.dumps({'colony':before,'inventories':before_inv},indent=2),encoding='utf-8')
for phase in ('upgrade','reload'):
 log=run/(phase+'.log');commands=[]
 for colony in before.values():
  for b in colony['buildings']:
   pos=b['location'];commands.append(f"forceload add {pos['x']} {pos['z']}")
 with log.open('w',encoding='utf-8') as output:
  proc=subprocess.Popen(['C:/Program Files/Java/jdk-21/bin/java.exe','-Xms1G','-Xmx4G','@libraries/net/neoforged/neoforge/21.1.250/win_args.txt','nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
  try:
   deadline=time.monotonic()+240
   while time.monotonic()<deadline:
    text=log.read_text(encoding='utf-8',errors='replace')
    if proc.poll() is not None:raise RuntimeError(f'Server exited early: {log}')
    if 'Done (' in text:break
    time.sleep(1)
   else:raise TimeoutError(str(log))
   proc.stdin.write('\n'.join(commands)+'\nsave-all flush\n');proc.stdin.flush();time.sleep(3)
   proc.stdin.write('forceload remove all\nsave-all flush\nstop\n');proc.stdin.flush();proc.wait(timeout=60)
   assert proc.returncode==0,log
  finally:
   if proc.poll() is None:proc.terminate();proc.wait(timeout=15)
 after=colony_state(world);after_inv=inventories(world)
 (run/(phase+'.json')).write_text(json.dumps({'colony':after,'inventories':after_inv},indent=2),encoding='utf-8')
 assert after==before,f'Colony identity/assignments/inventories changed: {run}'
 assert after_inv==before_inv,f'Block inventories changed: {run}'
 assert hashes(source)==before_hash,'Original world changed during test'
 print('PASS: '+phase+' preserved native colony and block inventories; original world hashes unchanged',flush=True)
(run/'result.json').write_text(json.dumps({'status':'PASS','buildings':sum(len(c['buildings']) for c in before.values()),'citizens':sum(len(c['citizens']) for c in before.values()),'blockInventories':len(before_inv),'scope':'Offline normal-server load/save/reload, no owner login or new outpost founding','jarSha256':hashlib.sha256(a.jar.read_bytes()).hexdigest()},indent=2),encoding='utf-8')
