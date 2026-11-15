"""Run an isolated Paper 1.21.4 lifecycle smoke using an existing Paper test cache.

python tools/verify_lifecycle.py --paper-cache PATH --jdk PATH
Never uses the source server's worlds, plugins, configuration or ports.
"""
from pathlib import Path
import argparse
import shutil
import socket
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--paper-cache', type=Path, required=True)
parser.add_argument('--jdk', type=Path, required=True)
parser.add_argument('--work-name', default='lifecycle-smoke')
args = parser.parse_args()
version = ET.parse(ROOT / 'pom.xml').findtext('{*}version')
artifact = ROOT / f'target/XmasLights-{version}.jar'
work = ROOT / '.tools' / args.work_name
if work.resolve().parent != (ROOT / '.tools').resolve():
    raise SystemExit('work-name must be a directory name inside .tools')
if work.exists():
    raise SystemExit(f'Use a fresh directory; previous evidence retained at {work}')
work.mkdir(parents=True)
for directory in ('libraries', 'versions', 'cache'):
    shutil.copytree(args.paper_cache / directory, work / directory)
launcher = next(args.paper_cache.glob('paper-1.21.4-*.jar'))
shutil.copyfile(launcher, work / 'paper.jar')
shutil.copyfile(args.paper_cache / 'eula.txt', work / 'eula.txt')
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    port = sock.getsockname()[1]
(work / 'server.properties').write_text(f'''server-ip=127.0.0.1
server-port={port}
online-mode=false
level-name=lifecycle-world
level-type=minecraft:flat
generate-structures=false
allow-nether=false
view-distance=2
simulation-distance=2
max-players=1
enable-query=false
enable-rcon=false
motd=Isolated lifecycle verification
''', encoding='utf-8')
(work / 'bukkit.yml').write_text('settings:\n  allow-end: false\n', encoding='utf-8')
(work / 'config').mkdir(exist_ok=True)
(work / 'config/paper-world-defaults.yml').write_text('chunks:\n  delay-chunk-unloads-by: 0s\n', encoding='utf-8')
plugins = work / 'plugins'
(plugins / 'XmasLights').mkdir(parents=True)
(plugins / 'XmasLights/config.yml').write_text('model-pack:\n  enabled: false\n  embedded:\n    enabled: false\n', encoding='utf-8')
shutil.copyfile(artifact, plugins / artifact.name)
classes = work / 'fixture-classes'
classes.mkdir()
classpath = ';'.join(str(p).replace('\\', '/') for p in [artifact, *sorted((work / 'libraries').rglob('*.jar'))])
sources = list((ROOT / 'tools/lifecycle').glob('*.java'))
compile_args = work / 'javac.args'
compile_args.write_text('\n'.join(['--release', '21', '-proc:none', '-cp', f'"{classpath}"', '-d', f'"{classes.as_posix()}"', *[f'"{p.as_posix()}"' for p in sources]]), encoding='utf-8')
subprocess.run([str(args.jdk / 'bin/javac.exe'), '@' + str(compile_args)], check=True, cwd=work)

for stage in ('create', 'restore', 'absent'):
    if stage == 'absent':
        (plugins / artifact.name).rename(work / artifact.name)
        (plugins / 'XmasLights').rename(work / 'XmasLights-data-after-disable')
    with zipfile.ZipFile(plugins / 'LifecycleSmoke.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in classes.rglob('*.class'):
            archive.write(path, path.relative_to(classes).as_posix())
        main = 'AbsenceSmoke' if stage == 'absent' else 'LifecycleSmoke'
        archive.writestr('plugin.yml', f'name: LifecycleSmoke\nversion: 1\napi-version: \'1.21.4\'\nmain: lifecycle.{main}\nsoftdepend: [XmasLights]\n')
    command = [str(args.jdk / 'bin/java.exe'), '-Xms256M', '-Xmx1024M', '-Dterminal.jline=false', '-Dterminal.ansi=false', f'-Dxl.smoke.stage={stage}', '-jar', 'paper.jar', '--nogui']
    print(f'Paper stage: {stage}', flush=True)
    with (work / f'{stage}.log').open('w', encoding='utf-8') as output:
        result = subprocess.run(command, cwd=work, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT, timeout=240)
    if result.returncode or (work / 'failed.txt').exists() or not (work / f'{stage}-passed.txt').exists():
        raise SystemExit(f'Lifecycle failed; inspect {work / (stage + ".log")}')
print(f'PASS: chunk save/unload, reconstruction, restart, disable and restart without JAR/data. Logs: {work}')
