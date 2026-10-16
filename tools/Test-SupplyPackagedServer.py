"""Run the development JAR on an isolated installed NeoForge server with a separate QA harness."""
import json, pathlib, subprocess, time, uuid, argparse, shutil
parser=argparse.ArgumentParser()
parser.add_argument("--jar", type=pathlib.Path, required=True)
parser.add_argument("--version", default="0.1.0-dev.menus.1")
parser.add_argument("--suite", choices=["ExplorationDiscovery","ExplorationGathering","ExplorationLifecycle","ExplorationCourier","ExplorationMenus","ExplorationRestart","FreeFounderFoodRecovery","FreeFounderGrant","FreeFounderFlow","FreeFounderEndurance","CourierTravel","RemoteMenu","RemoteIllness","RemoteEndurance","MayorUpgrade","MayorProposals","MayorExpansion","MayorExpansionForester"], default="CourierTravel")
parser.add_argument("--resume-world")
parser.add_argument("--exploration-fault", choices=["payment-pending","payment-native","timer","earned","delivery-pending","delivery-native","delivered"], default="timer")
args=parser.parse_args()
suite=args.suite
expected={"FreeFounderFoodRecovery":1,"FreeFounderGrant":6,"FreeFounderFlow":13,"FreeFounderEndurance":1,"CourierTravel":3,"RemoteMenu":12,"RemoteIllness":20,"RemoteEndurance":1,"MayorUpgrade":1,"MayorProposals":9,"MayorExpansion":1,"MayorExpansionForester":1}.get(suite)

root = pathlib.Path(__file__).resolve().parents[1]
expected_ids = None
if suite.startswith("Exploration") and suite != "ExplorationRestart":
    import re
    match = re.search(r"^  " + re.escape(suite) + r" \{@\((.*?)\)\}", (root/"tools/Test-OutpostFoundation.ps1").read_text(), re.M)
    assert match, f"Missing exact suite contract: {suite}"
    expected_ids = re.findall(r"'([^']+)'", match.group(1))
    assert len(expected_ids) == len(set(expected_ids)) and expected_ids
fault=args.exploration_fault
server = root / 'build/isolated-supply-server'
assert args.jar.resolve().is_relative_to(root / 'build/supply-dev-libs')
subprocess.run([__import__('sys').executable, str(root/'tools/Test-SupplyDevPackage.py'), str(args.jar), '--version', args.version],check=True)
# Only this disposable server's previous development artifacts are archived.
archive=server/'previous-development-jars'/uuid.uuid4().hex
archive.mkdir(parents=True,exist_ok=True)
for old in (server/'mods').glob('colony-horizons-*-dev.*.jar'):
    shutil.move(str(old),str(archive/old.name))
shutil.copy2(args.jar,server/'mods'/args.jar.name)
shutil.copy2(root/'build/supply-qa-libs/colony-horizons-isolated-qa.jar',server/'mods/colony-horizons-isolated-qa.jar')
run_id = uuid.uuid4().hex
report = root / 'build/reports/courier-districts/packaged-server' / run_id
report.mkdir(parents=True, exist_ok=True)
print('Packaged server report:', report, flush=True)
(report.parent / 'latest.json').write_text(json.dumps({'report':str(report)}))
assert server.is_relative_to(root / 'build')
assert (server / 'libraries/net/neoforged/neoforge/21.1.250/win_args.txt').is_file()
assert (server/'mods'/args.jar.name).is_file()
if suite.startswith('Exploration'):
    pack=root/'build/surveyor-pack'
    assert (pack/'pack.json').is_file() and list(pack.rglob('surveyor1.blueprint')), 'Missing Explorer blueprint pack'
    shutil.copytree(pack,server/'blueprints/arcology',dirs_exist_ok=True)
(server / 'eula.txt').write_text('eula=true\n')
world=args.resume_world or f'packaged-qa-{run_id}'
assert __import__('re').fullmatch(r'packaged-qa-[0-9a-f]{32}',world)
(server / 'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port=25576\nlevel-name={world}\nlevel-type=minecraft:flat\nmax-players=2\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\n')
phases=['seed','reload'] if suite=='ExplorationRestart' else ['seed']
for phase in phases:
    phase_report=report/phase
    phase_report.mkdir()
    expected_phase=['restart_'+phase] if suite=='ExplorationRestart' else expected_ids
    command = ['C:/Program Files/Java/jdk-21/bin/java.exe', '-Xms1G', '-Xmx4G',
        '-Dcolonyhorizons.packagedQa=true', '-Dneoforge.enableGameTest=true', '-Dneoforge.enabledGameTestNamespaces=colonyhorizons',
        f'-Dcolonyhorizons.foundationSuite={suite}', f'-Dcolonyhorizons.probeOutput={phase_report}',
        f'-Dcolonyhorizons.probePhase={phase}', f'-Dcolonyhorizons.explorationFault={fault}',
        f'-Dcolonyhorizons.arcologyPath={server / "blueprints/arcology"}',
        '@libraries/net/neoforged/neoforge/21.1.250/win_args.txt', 'nogui']
    log = phase_report / 'server-console.log'
    with log.open('w', encoding='utf-8') as output:
        process = subprocess.Popen(command, cwd=server, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT,
                                   text=True, creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            deadline = time.monotonic() + 900
            started = False
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError(f'Server exited {process.returncode}; inspect {log}')
                text = log.read_text(encoding='utf-8', errors='replace')
                if not started and 'Done (' in text:
                    process.stdin.write('gamerule doDaylightCycle false\ntime set day\ntest runall\n' + ('tick sprint 300000t\n' if suite in ('FreeFounderEndurance','RemoteEndurance','MayorUpgrade','MayorExpansion','MayorExpansionForester') else ''))
                    process.stdin.flush()
                    started = True
                result = phase_report / (suite+'.json')
                if started and result.exists():
                    rows = json.loads(result.read_text())
                    ids=[r['caseId'] for r in rows]
                    assert len(ids)==len(set(ids)) and all(r['outcome']=='PASS' for r in rows), rows
                    assert (set(ids)==set(expected_phase) if expected_phase is not None else len(rows)==expected), rows
                    print(f'PASS: packaged production JAR, {suite}/{phase}: {len(rows)} named checks',flush=True)
                    break
                if (phase_report/'travel-failure.txt').exists():
                    raise RuntimeError((phase_report/'travel-failure.txt').read_text())
                if started and ('Unknown or incomplete command' in text or 'required tests failed' in text):
                    raise RuntimeError(f'Packaged QA failed; inspect {log}')
                time.sleep(1)
            else:
                raise TimeoutError(f'Packaged server test timed out; inspect {log}')
        finally:
            if process.poll() is None:
                time.sleep(15) # Allow initial world generation and chunk tasks to settle before shutdown.
                process.stdin.write('stop\n'); process.stdin.flush()
                try: process.wait(timeout=45)
                except subprocess.TimeoutExpired:
                    with (phase_report/'shutdown-threads.txt').open('w',encoding='utf-8') as dump:
                        subprocess.run(['C:/Program Files/Java/jdk-21/bin/jcmd.exe',str(process.pid),'Thread.print'],stdout=dump,stderr=subprocess.STDOUT,timeout=15)
                    try: process.wait(timeout=45)
                    except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=15)
            if process.returncode:
                raise RuntimeError(f'Server shutdown returned {process.returncode}')
