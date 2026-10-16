import os
"""Actual integrated-client founding in an isolated prepared copy. No installed pack writes."""
import argparse,json,pathlib,shutil,hashlib,uuid,subprocess,sys,re,importlib.util
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,required=True);p.add_argument('--resume',type=Path);p.add_argument('--source-world',type=Path,required=True);p.add_argument('--founders',choices=['free-four','legacy'],default='legacy');a=p.parse_args()
root=Path(__file__).resolve().parents[1]
installed=Path(os.environ['COLONY_HORIZONS_PLAYTEST_INSTANCE'])
source=a.source_world.resolve()
assert source.is_dir() and (source/'level.dat').is_file(),'A verified world snapshot is required'
assert not source.is_relative_to(installed.resolve()),'Use a stable backup, never the live installed world'
assert a.jar.resolve().is_relative_to(root/'build/supply-dev-libs')
def hashes(folder):return {str(p.relative_to(folder)):hashlib.sha256(p.read_bytes()).hexdigest() for p in folder.rglob('*') if p.is_file()}
before=hashes(installed/'mods')
ROOT=(a.resume.resolve() if a.resume else root/'build/copied-colony-client'/uuid.uuid4().hex)
assert ROOT.is_relative_to(root/'build/copied-colony-client')
assert not ROOT.is_relative_to(source) and not ROOT.is_relative_to(installed.resolve())
GAME=ROOT/'instance';world=GAME/'saves/copy-founding'
if not a.resume:
 (GAME/'mods').mkdir(parents=True);shutil.copytree(source,world)
 for name in ('config','defaultconfigs','blueprints'):
  if (installed/name).exists():shutil.copytree(installed/name,GAME/name)
 for mod in (installed/'mods').glob('*.jar'):
  if mod.name.startswith(('minecolonies-','structurize-','blockui-','domum-','multipiston-','amendments-','moonlight-')):shutil.copy2(mod,GAME/'mods'/mod.name)
 (GAME/'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nrenderDistance:6\nsimulationDistance:6\nmaxFps:30\n',encoding='utf-8')
for prior in (GAME/'mods').glob('colony-horizons*.jar'):
 # Keep prior dev artifacts recoverable outside the loader directory.
 archive=ROOT/'prior-jars';archive.mkdir(exist_ok=True);shutil.move(str(prior),str(archive/(uuid.uuid4().hex+'-'+prior.name)))
shutil.copy2(a.jar,GAME/'mods'/a.jar.name);shutil.copy2(root/'build/supply-qa-libs/colony-horizons-isolated-qa.jar',GAME/'mods/colony-horizons-isolated-qa.jar')
spec=importlib.util.spec_from_file_location('audit',root/'tools/Audit-ArcologyPack.py');audit=importlib.util.module_from_spec(spec);spec.loader.exec_module(audit)
colony=audit.NBT((world/'minecolonies/minecraft/overworld/colony1.dat').read_bytes()).root();owner_name=colony['owner'];owner_uuid=colony['ownerid']
print('Copied-colony live report:',ROOT,flush=True)
(ROOT.parent/'latest.json').write_text(json.dumps({'report':str(ROOT)}),encoding='utf-8')
extra=['-Dcolonyhorizons.copyFounding=true',f'-Dcolonyhorizons.copyFounders={a.founders}']
BASE=Path(os.environ['COLONY_HORIZONS_TEST_LAUNCHER'])
vanilla=json.loads((BASE/'versions/1.21.1/1.21.1.json').read_text())
neo=json.loads((BASE/'versions/neoforge-21.1.250/neoforge-21.1.250.json').read_text())
def allowed(rules):
    result=not rules
    for r in rules:
        if r.get('features'): continue
        os=r.get('os',{})
        if os.get('name','windows')!='windows' or os.get('arch','amd64') not in ('amd64','x86_64'): continue
        result=r['action']=='allow'
    return result
libs={}
for lib in vanilla['libraries']+neo['libraries']:
    if not allowed(lib.get('rules',[])): continue
    artifact=lib.get('downloads',{}).get('artifact')
    if not artifact: continue
    parts=lib['name'].split(':')
    key=tuple(parts[:2]+parts[3:])
    path=BASE/'libraries'/artifact['path']
    assert path.exists(),path
    libs[key]=str(path)
# NeoForge supplies its transformed client through its own libraries.
natives=next((BASE/'bin').iterdir())
variables={'auth_player_name':owner_name,'version_name':neo['id'],'game_directory':str(GAME),'assets_root':str(BASE/'assets'),'assets_index_name':vanilla['assetIndex']['id'],'auth_uuid':owner_uuid.replace('-',''),'auth_access_token':'0','clientid':'','auth_xuid':'','user_type':'msa','version_type':'release','natives_directory':str(natives),'launcher_name':'ColonyHorizonsLocalSmoke','launcher_version':'1','classpath':';'.join(libs.values()),'library_directory':str(BASE/'libraries'),'classpath_separator':';'}
def expand(items):
    result=[]
    for item in items:
        if isinstance(item,dict):
            if not allowed(item.get('rules',[])): continue
            item=item['value']
        for s in item if isinstance(item,list) else [item]:
            result.append(re.sub(r'\$\{([^}]+)\}',lambda m:variables[m[1]],s))
    return result

for phase in ('founding','reload'):
 log=ROOT/(phase+'-'+uuid.uuid4().hex+'.log')
 args=['C:/Program Files/Java/jdk-21/bin/javaw.exe','-Xms1G','-Xmx5G']+extra+expand(vanilla['arguments']['jvm']+neo['arguments']['jvm'])+[neo['mainClass']]+expand(vanilla['arguments']['game']+neo['arguments']['game'])+['--width','1280','--height','720','--quickPlaySingleplayer','copy-founding']
 with log.open('w',encoding='utf-8') as output:
  process=subprocess.Popen(args,cwd=GAME,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
  try:code=process.wait(timeout=900)
  except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=20);raise
 assert hashes(installed/'mods')==before,'Installed pack changed during test'
 text=log.read_text(encoding='utf-8',errors='replace')
 assert code==0 and 'COPY_FOUNDING_PASS' in text and 'COPY_FOUNDING_FAIL' not in text,log
 print('PASS:',phase,'integrated client copied-owner/native citizens; installed files unchanged',flush=True)
(ROOT/'result.json').write_text(json.dumps({'status':'PASS','jarSha256':hashlib.sha256(a.jar.read_bytes()).hexdigest(),'installedModFilesUnchanged':len(before), 'sourceWorld':str(source), 'founders':a.founders,'fixture':'Copied existing colony, Native original-adult snapshot; finite QA setup; peaceful difficulty, prepared supported flat ground and finite preapproval supplies in 16 QA racks; no postapproval resource injection or building-level setters','phases':['founding','reload']}),encoding='utf-8')
