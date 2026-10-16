"""Build a private hotfix from the immutable submitted alpha, without unrelated development code."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

parser = argparse.ArgumentParser()
parser.add_argument('--alpha', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
expected = '717164e87fc5cd7f3d659c0703caa68026247327f20e6d231178581be9b71bc0'
assert hashlib.sha256(args.alpha.read_bytes()).hexdigest() == expected, 'Unexpected alpha input'
assert not args.output.exists(), 'Refusing to overwrite a prior artifact'
class_name = 'dev/colonyhorizons/compat/mixin/ArcologyMinerFluidSealMixin.class'
with ZipFile(args.alpha) as archive:
    original = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
updated = dict(original)
assert class_name not in updated
updated[class_name] = (root / 'build/classes/java/main' / class_name).read_bytes()
config = json.loads(updated['colonyhorizons.mixins.json'])
config['mixins'].append('ArcologyMinerFluidSealMixin')
updated['colonyhorizons.mixins.json'] = (json.dumps(config, indent=2) + '\n').encode()
metadata = updated['META-INF/neoforge.mods.toml'].decode()
assert metadata.count('0.1.0-alpha.1') == 1
updated['META-INF/neoforge.mods.toml'] = metadata.replace('0.1.0-alpha.1', '0.1.0-fixes-dev.1').encode()
args.output.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(args.output, 'w', ZIP_DEFLATED) as archive:
    for name, data in sorted(updated.items()):
        archive.writestr(name, data)
with ZipFile(args.output) as archive:
    actual = {name: archive.read(name) for name in archive.namelist()}
changed = {name for name in original if actual[name] != original[name]}
assert changed == {'colonyhorizons.mixins.json', 'META-INF/neoforge.mods.toml'}
assert set(actual) - set(original) == {class_name}
assert actual[class_name] == (root / 'build/classes/java/main' / class_name).read_bytes()
assert hashlib.sha256(args.alpha.read_bytes()).hexdigest() == expected
digest = hashlib.sha256(args.output.read_bytes()).hexdigest()
args.output.with_suffix('.jar.sha256').write_text(f'{digest}  {args.output.name}\n')
print(f'PASS: alpha unchanged; only version, mixin registration, and new class differ. SHA256={digest}')
