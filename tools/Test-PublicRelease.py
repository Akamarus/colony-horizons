"""Validate the public binary boundary; run against any candidate jar."""
import json,sys,zipfile
from pathlib import Path
p=Path(sys.argv[1])
with zipfile.ZipFile(p) as z:
 names=z.namelist(); failures=[]
 def check(ok,message):
  if not ok: failures.append(message)
 check('license="GPL-3.0-only"' in z.read('META-INF/neoforge.mods.toml').decode(),'metadata must declare GPL-3.0-only')
 for n in ('LICENSE','NOTICE','TEMPLATE_LICENSE.txt'): check(n in names,'missing '+n)
 check(not any('/qa/' in n or n.startswith(('qa/','data/','assets/')) or n.endswith(('.nbt','.blueprint','.jar')) for n in names),'QA, external assets or embedded jars present')
 check(not any('FoundationTestSaveMixin' in n for n in names),'test save mixin present')
 mix=json.loads(z.read('colonyhorizons.mixins.json'))
 check(len(mix['mixins'])==13 and 'FoundationTestSaveMixin' not in mix['mixins'],'incorrect production mixin list')
 for n in names:
  if n.endswith('.class'):
   check(n.startswith('dev/colonyhorizons/'),'foreign class '+n)
   b=z.read(n)
   check(b'dev/colonyhorizons/qa/' not in b,'QA class reference in '+n)
 check(not any(b'C:/Users/' in z.read(n) or b'C:\\Users\\' in z.read(n) for n in names if not n.endswith('/')),'private absolute path present')
 if failures:
  print('\n'.join('FAIL: '+f for f in failures));sys.exit(1)
 print('PASS: license, notices, 13 production mixins, no QA references/assets/private paths/embedded jars')
