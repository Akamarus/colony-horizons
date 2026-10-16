"""Package a committed beta source tree and matching addon/blueprints without dependencies."""
import argparse, hashlib, shutil, subprocess, zipfile
from pathlib import Path

p=argparse.ArgumentParser()
p.add_argument('--version',required=True)
a=p.parse_args()
import re
assert re.fullmatch(r'\d+\.\d+\.\d+-beta\.\d+',a.version)
root=Path(__file__).resolve().parents[1]
assert not subprocess.check_output(['git','status','--porcelain'],cwd=root).strip(), 'Commit reviewed changes first'
out=root/'build/releases'/a.version
out.mkdir(parents=True,exist_ok=True)
jar=root/'build/supply-dev-libs'/f'colony-horizons-{a.version}.jar'
subprocess.run([__import__('sys').executable,str(root/'tools/Test-SupplyDevPackage.py'),str(jar),'--version',a.version],check=True)
shutil.copy2(jar,out/jar.name)
pack=root/'blueprints/arcology-horizons'
files=sorted(f for f in pack.rglob('*') if f.is_file())
assert sum(f.suffix=='.blueprint' for f in files)==310
with zipfile.ZipFile(out/f'arcology-horizons-{a.version}-blueprints.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in files:
  assert not f.is_symlink()
  info=zipfile.ZipInfo('arcology-horizons/'+f.relative_to(pack).as_posix(),(2026,1,1,0,0,0))
  info.compress_type=zipfile.ZIP_DEFLATED
  z.writestr(info,f.read_bytes())
source=out/f'colony-horizons-{a.version}-source.zip'
subprocess.run(['git','archive','--format=zip',f'--prefix=colony-horizons-{a.version}/',f'--output={source}','HEAD'],cwd=root,check=True)
for name in ('BETA1.md','INSTALLATION.md'):
 shutil.copy2(root/'docs/release'/name,out/name)
payload=sorted(f for f in out.iterdir() if f.name!='SHA256SUMS.txt')
for f in payload:
 if f.suffix in ('.zip','.jar'):
  with zipfile.ZipFile(f) as z: assert z.testzip() is None
(out/'SHA256SUMS.txt').write_text(''.join(f'{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.name}\n' for f in payload),encoding='utf-8')
print('PASS: beta addon, 310-blueprint pack, committed source, notes and checksums:',out)
