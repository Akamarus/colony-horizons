"""Create/verify a deterministic PRIVATE Arcology release (Python 3.11+, stdlib only)."""
import argparse
import hashlib
import io
import json
import re
import stat
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACK_ROOT = 'blueprints/arcology-horizons'
NOTICE = ('PRIVATE LOCAL COPY ONLY. Contains user-owned Arcology assets by BlockyUniverse.\n'
          'Do not upload or redistribute this bundle without the necessary rights.\n'
          'Third-party dependency jars are not included. This is a development candidate.\n')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + '\n').encode('utf-8')


def regular(path):
    if path.is_symlink() or getattr(path, 'is_junction', lambda: False)():
        raise ValueError(f'Links/junctions are not supported: {path}')
    attributes = getattr(path.lstat(), 'st_file_attributes', 0)
    if attributes & getattr(stat, 'FILE_ATTRIBUTE_REPARSE_POINT', 0):
        raise ValueError(f'Reparse points are not supported: {path}')


def files_under(root):
    regular(root)
    result = {}
    for path in sorted(root.rglob('*')):
        regular(path)
        if path.is_file():
            name = path.relative_to(root).as_posix()
            if '\n' in name or '\r' in name:
                raise ValueError('Newlines in file names are unsupported')
            result[name] = path.read_bytes()
    return result


def requirements():
    props = dict(re.findall(r'^(\w+)=(.+)$', (ROOT / 'gradle.properties').read_text(), re.M))
    build = (ROOT / 'build.gradle').read_text()
    pins = {name: re.search(r'com\.ldtteam:' + re.escape(name) + r':([^\x27\x22]+)', build).group(1)
            for name in ('structurize', 'blockui', 'domum-ornamentum', 'multipiston')}
    pins.update(java='21', minecraft='1.21.1', neoforge=props['neo_version'],
                minecolonies=props['minecolonies_version'])
    return props['mod_version'], pins


def package(jar, pack, output):
    jar, pack, output = jar.absolute(), pack.absolute(), output.absolute()
    archive = Path(str(output) + '.zip')
    checksum = Path(str(archive) + '.sha256')
    for target in (output, archive, checksum):
        if target.exists() or target.is_symlink():
            raise ValueError(f'Refusing to overwrite: {target}')
    for source in (jar.resolve(), pack.resolve()):
        if source == output.resolve() or source in output.resolve().parents or output.resolve() in source.parents:
            raise ValueError('Output must be separate from both inputs')
    regular(jar)
    version, pins = requirements()
    jar_data = jar.read_bytes()
    with zipfile.ZipFile(io.BytesIO(jar_data)) as z:
        if z.testzip():
            raise ValueError('Corrupt addon jar')
        meta = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
        if not any(m.get('modId') == 'colonyhorizons' and m.get('version') == version for m in meta.get('mods', [])):
            raise ValueError('Addon mod ID/version differs from gradle.properties; build the release first')
        if not any(n.startswith('dev/colonyhorizons/') and n.endswith('.class') for n in z.namelist()):
            raise ValueError('Addon jar has no compiled Colony Horizons classes')
        if any(n.startswith('META-INF/jarjar/') and n.endswith('.jar') for n in z.namelist()):
            raise ValueError('Bundled dependency jars are not allowed')
    assets = files_under(pack)
    for name in assets:
        if Path(name).suffix not in ('.blueprint', '.json', '.png') and name != '.arcology-horizons-generated':
            raise ValueError(f'Unexpected pack file (not copied): {name}')
    metadata = json.loads(assets['pack.json'])
    provenance = json.loads(assets['compatibility-manifest.json'])
    if metadata.get('name') != 'Arcology Horizons' or provenance.get('include_extensions') is not True:
        raise ValueError('Expected the generated Arcology Horizons complete extension pack')
    if '.arcology-horizons-generated' not in assets or not any(n.endswith('.blueprint') for n in assets):
        raise ValueError('Missing generated-pack marker or blueprints')
    payload = {f'{PACK_ROOT}/{name}': data for name, data in assets.items()}
    addon_name = f'mods/colony-horizons-{version}.jar'
    payload[addon_name] = jar_data
    payload['ARCOLOGY-INSTALLATION.md'] = (ROOT / 'docs/outposts/ARCOLOGY-INSTALLATION.md').read_bytes()
    payload['PRIVATE-LOCAL-ONLY.txt'] = NOTICE.encode()
    manifest = {
        'format': 1, 'addon': 'colonyhorizons', 'version': version,
        'distribution': 'private-local-only', 'requirements': pins,
        'optional_tested_integration': {'amendments': '2.0.5', 'moonlight': '2.23.8'},
        'addon_file': addon_name, 'pack_root': PACK_ROOT,
        'blueprint_count': sum(n.endswith('.blueprint') for n in assets),
        'pack_file_count': len(assets),
        'reproducibility': 'Same input bytes yield identical ZIP bytes; sorted entries, fixed timestamp, ZIP_STORED.',
        'files': {name: {'sha256': sha(data), 'bytes': len(data)} for name, data in sorted(payload.items())},
    }
    payload['release-manifest.json'] = json_bytes(manifest)
    payload['SHA256SUMS.txt'] = ''.join(f'{sha(data)}  {name}\n' for name, data in sorted(payload.items())).encode()
    # Exclusive creation throughout: no cleanup, overwrite, or deletion of existing data.
    # An interrupted run leaves inspectable partial output; use a new output name to retry.
    output.mkdir(parents=True, exist_ok=False)
    for name, data in sorted(payload.items()):
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as stream:
            stream.write(data)
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_STORED) as z:
        for name, data in sorted(payload.items()):
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            z.writestr(entry, data)
    with checksum.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(f'{sha(archive.read_bytes())}  {archive.name}\n')
    verify(output, archive)
    return manifest


def verify(output, archive=None):
    payload = files_under(output)
    manifest = json.loads(payload['release-manifest.json'])
    expected_names = set(manifest['files']) | {'release-manifest.json', 'SHA256SUMS.txt'}
    if set(payload) != expected_names:
        raise ValueError('Missing or unexpected files in release directory')
    for name, expected in manifest['files'].items():
        data = payload[name]
        if sha(data) != expected['sha256'] or len(data) != expected['bytes']:
            raise ValueError(f'Payload hash/size mismatch: {name}')
    sums = ''.join(f'{sha(data)}  {name}\n' for name, data in sorted(payload.items()) if name != 'SHA256SUMS.txt').encode()
    if payload['SHA256SUMS.txt'] != sums:
        raise ValueError('SHA256SUMS mismatch')
    if archive:
        with zipfile.ZipFile(archive) as z:
            if len(z.namelist()) != len(payload) or set(z.namelist()) != set(payload):
                raise ValueError('ZIP inventory differs from directory')
            for name, data in payload.items():
                if z.read(name) != data:
                    raise ValueError(f'ZIP bytes differ: {name}')
        expected = f'{sha(archive.read_bytes())}  {archive.name}\n'
        if Path(str(archive) + '.sha256').read_text() != expected:
            raise ValueError('ZIP SHA256 mismatch')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    create = sub.add_parser('create', help='Snapshot built addon + complete pack; no build or launch')
    create.add_argument('--jar', type=Path, default=ROOT / 'build/libs/colony-horizons-0.1.0-dev.jar')
    create.add_argument('--pack', type=Path, default=ROOT / '.setup/arcology-horizons-complete')
    create.add_argument('--output', type=Path, required=True, help='New directory; sibling .zip and .zip.sha256 also created')
    check = sub.add_parser('verify', help='Verify a directory and optional archive without modifying either')
    check.add_argument('--output', type=Path, required=True)
    check.add_argument('--archive', type=Path)
    args = parser.parse_args()
    try:
        result = package(args.jar, args.pack, args.output) if args.command == 'create' else verify(args.output, args.archive)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f'ERROR: {error}\n')
    print(json.dumps({'status': 'verified', 'version': result['version'], 'blueprints': result['blueprint_count'],
                      'pack_files': result['pack_file_count'], 'output': str(args.output.absolute())}, indent=2))


if __name__ == '__main__':
    main()
