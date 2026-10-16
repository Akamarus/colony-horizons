"""Independent complete-pack assembly, determinism and mode-preservation contracts."""
import hashlib,json,subprocess,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('blueprints/arcology-horizons')
EXPECTED={f'military/{kind}{level}.blueprint' for kind,count in [('stable',5),('gatehouse',3)] for level in range(1,count+1)}
EXPECTED|={f'fundamentals/{kind}quarry1.blueprint' for kind in ('simple','medium')}
EXPECTED|={f'infrastructure/mineshafts/{kind}quarryshaft1.blueprint' for kind in ('simple','medium')}
EXPECTED|={f'infrastructure/plantationfields/{kind}1.blueprint' for kind in ('cocoa','vine','kelp','seagrass','seapickle','glowberry','weepingvine','twistingvine','crimson','warped')}
def hashes(root):return {p.relative_to(root).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()}
def build(output,include=True):
 args=[sys.executable,str(ROOT/'tools/Build-ArcologyPack.py'),'--source',str(SOURCE),'--output',str(output)]
 if include:args.append('--include-extensions')
 return subprocess.run(args,capture_output=True,text=True)
class Assembly(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.temp=tempfile.TemporaryDirectory(prefix='assembly-contract-',dir=ROOT/'.setup');cls.addClassCleanup(cls.temp.cleanup)
  cls.before=hashes(SOURCE);cls.a=Path(cls.temp.name)/'a';cls.b=Path(cls.temp.name)/'b'
  for path in (cls.a,cls.b):
   result=build(path)
   if result.returncode:raise AssertionError(result.stdout+result.stderr)
 def test_exact_additions_and_manifest(self):
  manifest=json.loads((self.a/'compatibility-manifest.json').read_text())
  self.assertEqual(set(manifest['new_blueprints']),EXPECTED);self.assertEqual(len(manifest['new_blueprints']),22)
  original={p.relative_to(SOURCE).as_posix() for p in SOURCE.rglob('*.blueprint')}
  actual={p.relative_to(self.a).as_posix() for p in self.a.rglob('*.blueprint')}
  self.assertEqual(actual,original|EXPECTED);self.assertEqual(len(actual),305)
  self.assertEqual(manifest['source_hashes'],self.before);self.assertTrue(manifest['include_extensions'])
 def test_declares_required_addon(self):
  metadata=json.loads((self.a/'pack.json').read_text())
  self.assertIn('colonyhorizons',metadata['mods'],'Operational repairs require addon compatibility hooks')
  self.assertIn('BlockyUniverse',metadata['authors'])
 def test_repeat_and_source_preservation(self):
  self.assertEqual(hashes(self.a),hashes(self.b));self.assertEqual(hashes(SOURCE),self.before)
 def test_reject_mode_change_without_touching_output(self):
  before=hashes(self.a);result=build(self.a,False)
  self.assertNotEqual(result.returncode,0);self.assertIn('different extension mode',result.stderr)
  self.assertEqual(hashes(self.a),before)
if __name__=='__main__':unittest.main(verbosity=2)
