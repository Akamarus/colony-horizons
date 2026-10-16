"""Package the tested blueprint-only hotfix separately from the submitted alpha."""
import argparse, hashlib, json, zipfile
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('original_modpack',type=Path);p.add_argument('candidate',type=Path);p.add_argument('output',type=Path)
a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
packzip=a.output/'Arcology-Horizons-placement-fixes-dev.1.zip'
playzip=a.output/'Colony-Horizons-Playtest-placement-fixes-dev.1.zip'
for target in (packzip,playzip):
    if target.exists():raise ValueError('Refusing overwrite: '+str(target))
with zipfile.ZipFile(packzip,'w',zipfile.ZIP_DEFLATED) as z:
    for f in sorted(a.candidate.rglob('*')):
        if f.is_file():z.write(f,'arcology-horizons/'+f.relative_to(a.candidate).as_posix())
with zipfile.ZipFile(a.original_modpack) as old,zipfile.ZipFile(playzip,'w',zipfile.ZIP_DEFLATED) as new:
    for name in old.namelist():
        if name.startswith('overrides/blueprints/arcology-horizons/'):continue
        data=old.read(name)
        if name=='manifest.json':
            meta=json.loads(data);meta['version']='0.1.0-alpha.1-placement-fixes-dev.1';meta['name']='Colony Horizons Playtest - Placement Fixes';data=json.dumps(meta,indent=2).encode()
        if name=='overrides/PLAYTEST-README.txt':data+=b'\nDevelopment blueprint update: raft waterline and builder Recall safety rails. JAR unchanged.\nExisting buildings require a physical repair/rebuild; blueprints do not edit existing worlds.\n'
        new.writestr(name,data)
    for f in sorted(a.candidate.rglob('*')):
        if f.is_file():new.write(f,'overrides/blueprints/arcology-horizons/'+f.relative_to(a.candidate).as_posix())
with zipfile.ZipFile(a.original_modpack) as old,zipfile.ZipFile(playzip) as new:
    assert new.testzip() is None
    for name in old.namelist():
        if name.startswith(('overrides/mods/','overrides/config/','overrides/shaderpacks/')):assert old.read(name)==new.read(name),name
    assert not any('/saves/' in name or 'credentials' in name or 'launcher_accounts' in name for name in new.namelist())
for target in (packzip,playzip):
    digest=hashlib.sha256(target.read_bytes()).hexdigest()
    target.with_suffix('.zip.sha256').write_text(digest+'  '+target.name+'\n')
    print(target.name,digest)
print('PASS: packaged; all mod JARs, shader and configurations identical to original; no world/account files.')
