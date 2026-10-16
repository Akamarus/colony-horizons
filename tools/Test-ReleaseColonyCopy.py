"""Bounded diagnostics in disposable copies; never writes the supplied backup or installed pack."""
import argparse,hashlib,json,pathlib,shutil,subprocess,time,uuid,os
p=argparse.ArgumentParser();p.add_argument('--kind',choices=['miner','recipes'],required=True);p.add_argument('--instance',type=pathlib.Path,required=True);p.add_argument('--jar',type=pathlib.Path,required=True);a=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1];source=a.instance.resolve()/'saves/New World';server=root/'build'/('miner-copy' if a.kind=='miner' else 'recipe-copy');server.mkdir(parents=True,exist_ok=True)
assert source.is_dir() and not source.is_relative_to(server)
assert a.jar.resolve().is_relative_to(root/'build/supply-dev-libs')
def hashes(folder):return {str(f.relative_to(folder)):hashlib.sha256(f.read_bytes()).hexdigest() for f in folder.rglob('*') if f.is_file()}
before=hashes(source);world=server/'colony-copy'
if world.exists():world.rename(server/('previous-world-'+uuid.uuid4().hex[:8]))
shutil.copytree(source,world);assert hashes(world)==before==hashes(source)
if not (server/'libraries').exists():shutil.copytree(root/'build/isolated-supply-server/libraries',server/'libraries')
for name in ('config','defaultconfigs','blueprints'):
 if (a.instance/name).is_dir():shutil.copytree(a.instance/name,server/name,dirs_exist_ok=True)
mods=server/'mods';mods.mkdir(exist_ok=True)
archive=server/('prior-mods-'+uuid.uuid4().hex[:8]);archive.mkdir()
for old in mods.glob('*.jar'):old.rename(archive/old.name)
for mod in (a.instance/'mods').glob('*.jar'):
 if mod.name.startswith(('minecolonies-','structurize-','blockui-','domum-','multipiston-','amendments-','moonlight-')):shutil.copy2(mod,mods/mod.name)
for jar in (a.jar,root/'build/supply-qa-libs/colony-horizons-isolated-qa.jar'):shutil.copy2(jar,mods/jar.name)
(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port='+('25579' if a.kind=='miner' else '25580')+'\nlevel-name=colony-copy\nmax-players=1\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\n')
report=server/('report-'+uuid.uuid4().hex[:8]);report.mkdir();print('Copy test report:',report,flush=True)
phases=['endurance'] if a.kind=='miner' else ['seed','reload']
for phase in phases:
 log=report/(phase+'.log');props=['-Dcolonyhorizons.copiedMiner=true','-Dcolonyhorizons.minerEndurance=true'] if a.kind=='miner' else ['-Dcolonyhorizons.recipeRestart=true']
 with log.open('w',encoding='utf-8') as output:
  proc=subprocess.Popen([str(pathlib.Path(os.environ['JAVA_HOME'])/'bin/java.exe'),'-Xms1G','-Xmx4G']+props+['@libraries/net/neoforged/neoforge/21.1.250/win_args.txt','nogui'],cwd=server,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
  try:
   deadline=time.monotonic()+720;accelerated=False
   while proc.poll() is None and time.monotonic()<deadline:
    text=log.read_text(encoding='utf-8',errors='replace')
    if a.kind=='miner' and not accelerated and 'Done (' in text:proc.stdin.write('tick rate 100\n');proc.stdin.flush();accelerated=True
    time.sleep(1)
   if proc.poll() is None:raise TimeoutError(str(log))
   assert proc.returncode==0,log
  finally:
   if proc.poll() is None:
    proc.stdin.write('stop\n');proc.stdin.flush()
    try:proc.wait(timeout=30)
    except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)
 marker='MINER_ENDURANCE_PASS' if a.kind=='miner' else 'RECIPE_RESTART_'+phase.upper()+'_PASS'
 text=log.read_text(encoding='utf-8',errors='replace');assert marker in text and '_FAIL' not in text,log
 print('PASS:',marker,flush=True)
assert hashes(source)==before,'Backup source changed'
(report/'result.json').write_text(json.dumps({'status':'PASS','kind':a.kind,'sourceUnchanged':True,'jarSha256':hashlib.sha256(a.jar.read_bytes()).hexdigest()},indent=2))
