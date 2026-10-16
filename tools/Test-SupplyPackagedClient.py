import os
import json, re, subprocess, shutil, sys, argparse, uuid
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--jar',type=Path,required=True)
parser.add_argument('--version',default='0.1.0-dev.remote.1')
parser.add_argument('--catalog',action='store_true')
parser.add_argument('--catalog-world',type=Path)
parser.add_argument('--navigation',action='store_true')
parser.add_argument('--interactive-world',type=Path)
parser.add_argument('--player-uuid',default='00000000000000000000000000000001')
args=parser.parse_args()
args_catalog=args.catalog
catalog_world=args.catalog_world
args_navigation=args.navigation
interactive=args.interactive_world
player_uuid=uuid.UUID(args.player_uuid).hex
assert not (interactive and args_navigation)
root=Path(__file__).resolve().parents[1]
subprocess.run([sys.executable,str(root/'tools/Test-SupplyDevPackage.py'),str(args.jar),'--version',args.version],check=True)
ROOT=root/'build/packaged-client'/uuid.uuid4().hex
GAME=ROOT/'instance'
(GAME/'mods').mkdir(parents=True)
for mod in (root/'build/isolated-supply-server/mods').glob('*.jar'):
    if not mod.name.startswith('colony-horizons'):shutil.copy2(mod,GAME/'mods'/mod.name)
shutil.copy2(args.jar,GAME/'mods'/args.jar.name)
if not interactive:
    shutil.copy2(root/'build/supply-qa-libs/colony-horizons-isolated-qa.jar',GAME/'mods/colony-horizons-isolated-qa.jar')
else:
    source=interactive.resolve()
    assert source.is_relative_to(root/'runs/foundation-sessions') or source.is_relative_to(root/'build'), 'Use a disposable test world only'
    assert (source/'level.dat').is_file()
    shutil.copytree(source,GAME/'saves/Exploration QA')
pack=root/'build/surveyor-pack'
assert (pack/'pack.json').is_file()
shutil.copytree(pack,GAME/'blueprints/arcology')
if args_catalog:
    assert catalog_world and catalog_world.resolve().is_relative_to(root/'runs/foundation-sessions')
    shutil.copytree(catalog_world,GAME/'saves/Catalog QA')
print('Packaged client report:',ROOT,flush=True)
(ROOT.parent/'latest.json').write_text(json.dumps({'report':str(ROOT)}))
extra=[] if interactive else ['-Dcolonyhorizons.menuPreview=true']+(['-Dcolonyhorizons.menuRegression=true'] if args.navigation else [])
if args_catalog: extra=['-Dcolonyhorizons.catalogPreview=true']
marker='MENU_NAVIGATION_RESULT failures=0 cases=13' if args.navigation else 'MENU_PREVIEW_COMPLETE'

if args_catalog: marker='CATALOG_PREVIEW_COMPLETE'
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
variables={'auth_player_name':'Akamarus','version_name':neo['id'],'game_directory':str(GAME),'assets_root':str(BASE/'assets'),'assets_index_name':vanilla['assetIndex']['id'],'auth_uuid':player_uuid,'auth_access_token':'0','clientid':'','auth_xuid':'','user_type':'msa','version_type':'release','natives_directory':str(natives),'launcher_name':'ColonyHorizonsLocalSmoke','launcher_version':'1','classpath':';'.join(libs.values()),'library_directory':str(BASE/'libraries'),'classpath_separator':';'}
def expand(items):
    result=[]
    for item in items:
        if isinstance(item,dict):
            if not allowed(item.get('rules',[])): continue
            item=item['value']
        for s in item if isinstance(item,list) else [item]:
            result.append(re.sub(r'\$\{([^}]+)\}',lambda m:variables[m[1]],s))
    return result
args=['C:/Program Files/Java/jdk-21/bin/javaw.exe','-Xms1G','-Xmx4G']+extra+expand(vanilla['arguments']['jvm']+neo['arguments']['jvm'])+[neo['mainClass']]+expand(vanilla['arguments']['game']+neo['arguments']['game'])+['--width','1920','--height','1080']
if interactive: args+=['--quickPlaySingleplayer','Exploration QA']
if args_catalog: args+=['--quickPlaySingleplayer','Catalog QA']
log=open(ROOT/'smoke-console.log','w')
process=subprocess.Popen(args,cwd=GAME,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
if interactive:
    (ROOT/'interactive.json').write_text(json.dumps({'pid':process.pid,'instance':str(GAME),'worldSource':str(source),'qa':False}))
    log.close()
    print('Normal client launched without QA hooks; close it through Save and Quit after checking native controls.',flush=True)
    sys.exit(0)
try:
    code=process.wait(timeout=240)
except subprocess.TimeoutExpired:
    process.terminate();process.wait(timeout=15);raise
finally:
    log.close()
assert code==0, f'Client exit {code}: {ROOT}'
text=(ROOT/'smoke-console.log').read_text(errors='replace')
assert marker in text, f'Missing completion marker: {ROOT}'
if args_navigation:
    expected={'BACK','CREATED','COORDINATES','LEVEL','BUDGET','UPGRADES','UPGRADE_BACK','EXPLORATION_LOCKED','EXPLORATION_READY','EXPLORATION_RUNNING','EXPLORATION_FULL','EXPLORATION_HISTORY','EXPLORATION_FEEDBACK'}
    found=re.findall(r'MENU_NAVIGATION_([A-Z_]+): PASS',text)
    assert len(found)==len(expected) and set(found)==expected,found
print('PASS: exact packaged development client; '+marker)
