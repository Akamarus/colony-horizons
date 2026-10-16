"""Download and safely extract the exact official MineColonies audit artifacts."""
from pathlib import Path
import hashlib, urllib.request, zipfile
ROOT=Path('.setup/minecolonies-audit')
BASE='https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/1.1.1403-1.21.1/'
ARTIFACTS=[('minecolonies.jar','minecolonies-1.1.1403-1.21.1.jar','c8d4373ffc8a76638c8edf2bb6716f9209599032209c48d0d559b787f69a0561','runtime'),('minecolonies-sources.jar','minecolonies-1.1.1403-1.21.1-sources.jar','7444e11f912b92fdfa590d5914388b611e124e2a32b7de09a5e12a6134a80be6','source')]
ROOT.mkdir(parents=True,exist_ok=True)
for local,remote,expected,directory in ARTIFACTS:
    archive=ROOT/local
    if not archive.exists():urllib.request.urlretrieve(BASE+remote,archive)
    actual=hashlib.sha256(archive.read_bytes()).hexdigest()
    if actual!=expected:raise ValueError(f'Artifact hash mismatch: {archive}')
    target=(ROOT/directory).resolve()
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            if not (name.endswith('.java') or (name.startswith(('data/','META-INF/')) and name.endswith(('.json','.toml','.MF')))):continue
            destination=(target/name).resolve()
            if not destination.is_relative_to(target):raise ValueError('Unsafe archive path')
            destination.parent.mkdir(parents=True,exist_ok=True)
            destination.write_bytes(z.read(name))
    print(f'Verified and extracted {local}: {actual}')
