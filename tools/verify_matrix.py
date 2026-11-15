"""Build version-specific packs/JARs and exercise real Paper lifecycle in isolated worlds.

Uses Java 21 for 1.21 and Java 25 for 26.x. No production server is accessed.
"""
from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import io
import json
import shutil
import socket
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TARGETS = {'1.21.4': [46, 0], '1.21.5': [55, 0], '1.21.6': [63, 0],
           '1.21.7': [64, 0], '1.21.8': [64, 0], '1.21.9': [69, 0],
           '1.21.10': [69, 0], '1.21.11': [75, 0], '26.1.1': [84, 0],
           '26.1.2': [84, 0], '26.2': [88, 0], '26.3': [97, 1]}
VERSION = ET.parse(ROOT / 'pom.xml').findtext('{*}version')
WORK = ROOT / '.tools' / ('matrix-' + VERSION)
DIST = ROOT / 'dist' / VERSION


def get(url):
    request = urllib.request.Request(url, headers={'User-Agent':
        f'AstralicaStudio/XmasLights/{VERSION} (https://modrinth.com/plugin/xmas-lights)'})
    with urllib.request.urlopen(request, timeout=90) as response:
        return response.read()


def prepare(mc):
    work = WORK / mc
    work.mkdir(parents=True, exist_ok=True)
    info = json.loads(get(f'https://fill.papermc.io/v3/projects/paper/versions/{mc}/builds/latest'))
    download = info['downloads']['server:default']
    target = work / 'paper.jar'
    digest = download['checksums']['sha256']
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != digest:
        data = get(download['url'])
        if hashlib.sha256(data).hexdigest() != digest:
            raise RuntimeError(f'Paper checksum mismatch: {mc}')
        target.write_bytes(data)
    (work / 'paper-build.json').write_text(json.dumps(info, indent=2), encoding='utf-8')
    print(f'Paper {mc} build {info["id"]}: SHA-256 verified', flush=True)


def package_patch(mc):
    """All artifacts use the oldest public API; packs and descriptors target each actual release."""
    DIST.mkdir(parents=True, exist_ok=True)
    artifact = ROOT / f'target/XmasLights-{VERSION}.jar'
    major, minor = TARGETS[mc]
    pack_buffer = io.BytesIO()
    with zipfile.ZipFile(ROOT / 'src/main/resources/packs/XmasLights-Models-1.21.4.zip') as source:
        with zipfile.ZipFile(pack_buffer, 'w', zipfile.ZIP_DEFLATED) as out:
            for name in source.namelist():
                if name == 'pack.mcmeta':
                    meta = {'pack': {'description': f'XmasLights {VERSION} - Minecraft {mc}', 'pack_format': major}}
                    if major >= 65:
                        meta['pack'].update(min_format=[major, minor], max_format=[major, minor])
                    data = json.dumps(meta).encode()
                else:
                    data = source.read(name)
                out.writestr(name, data)
    packname = f'XmasLights-Models-{mc}.zip'
    (DIST / packname).write_bytes(pack_buffer.getvalue())
    jar = DIST / f'XmasLights-{VERSION}+mc.v.{mc}.jar'
    with zipfile.ZipFile(artifact) as source, zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as out:
        for name in source.namelist():
            if name.startswith('packs/') and name.endswith('.zip'):
                continue
            data = source.read(name)
            if name == 'plugin.yml':
                data = data.decode().replace("api-version: '1.21.4'", f"api-version: '{mc}'").encode()
            out.writestr(name, data)
        out.writestr('packs/' + packname, pack_buffer.getvalue())
        out.writestr('compatibility.properties', f'minecraft={mc}\n')
    return jar


def package(mc):
    from branch_release import branch_for, package as package_branch
    return package_branch(branch_for(mc))


def run(mc, jdk, eula_file, bootstrap=True):
    work = WORK / mc
    artifact = package(mc)
    # Refuse to overwrite a previous world's evidence accidentally.
    if (work / 'server.properties').exists():
        raise RuntimeError(f'Existing run at {work}; retain it and use a new version/work directory')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    (work / 'eula.txt').write_bytes(eula_file.read_bytes())
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
''')
    (work / 'bukkit.yml').write_text('settings:\n  allow-end: false\n')
    (work / 'config').mkdir(exist_ok=True)
    (work / 'config/paper-world-defaults.yml').write_text('chunks:\n  delay-chunk-unloads-by: 0s\n')
    plugins = work / 'plugins'
    (plugins / 'XmasLights').mkdir(parents=True, exist_ok=True)
    (plugins / 'XmasLights/config.yml').write_text('updates:\n  enabled: false\nmodel-pack:\n  enabled: false\n  embedded:\n    enabled: false\n')
    shutil.copyfile(artifact, plugins / artifact.name)
    classes = work / 'fixture-classes'
    classes.mkdir(exist_ok=True)
    # Bootstrap Paper first to populate its API and dependencies. Stop through stdin at readiness.
    command = [str(jdk / 'bin/java.exe'), '-Xms256M', '-Xmx1024M', '-XX:ActiveProcessorCount=2',
               '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Djavax.net.ssl.trustStoreType=Windows-ROOT',
               '-Djavax.net.ssl.trustStore=NONE', '-jar', 'paper.jar', '--nogui']
    with (work / 'bootstrap.log').open('w', encoding='utf-8') as log:
        if not bootstrap:
            log.write('Reusing verified Paper libraries, versions and Mojang cache from the previous isolated matrix.\n')
    if bootstrap:
        with (work / 'bootstrap.log').open('w', encoding='utf-8') as log:
            process = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace',
                                       creationflags=subprocess.CREATE_NO_WINDOW)
            import threading
            timeout = threading.Timer(240, process.kill)
            timeout.start()
            try:
                for line in process.stdout:
                    log.write(line)
                    if 'Done (' in line:
                        process.stdin.write('stop\n')
                        process.stdin.flush()
                if process.wait() != 0:
                    raise RuntimeError(f'Bootstrap failed: {work / "bootstrap.log"}')
            finally:
                timeout.cancel()
    classpath = ';'.join(str(p).replace('\\', '/') for p in [artifact, *sorted((work / 'libraries').rglob('*.jar'))])
    compile_args = work / 'javac.args'
    compile_args.write_text('\n'.join(['--release', '21', '-proc:none', '-cp', f'"{classpath}"',
        '-d', f'"{classes.as_posix()}"', *[f'"{p.as_posix()}"' for p in (ROOT / 'tools/lifecycle').glob('*.java')]]))
    result = subprocess.run([str(jdk / 'bin/javac.exe'), '@' + str(compile_args)], cwd=work,
                            capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    (work / 'compile.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(f'Fixture compile failed: {work / "compile.log"}')
    for stage in ('create', 'restore', 'absent'):
        if stage == 'absent':
            (plugins / artifact.name).rename(work / artifact.name)
            (plugins / 'XmasLights').rename(work / 'XmasLights-data-after-disable')
        with zipfile.ZipFile(plugins / 'LifecycleSmoke.jar', 'w', zipfile.ZIP_DEFLATED) as out:
            for path in classes.rglob('*.class'):
                out.write(path, path.relative_to(classes).as_posix())
            main = 'AbsenceSmoke' if stage == 'absent' else 'LifecycleSmoke'
            out.writestr('plugin.yml', f'name: LifecycleSmoke\nversion: 1\napi-version: \'{mc}\'\nmain: lifecycle.{main}\nsoftdepend: [XmasLights]\n')
        print(f'Paper {mc}: {stage}', flush=True)
        stage_command = command[:1] + [f'-Dxl.smoke.stage={stage}'] + command[1:]
        with (work / f'{stage}.log').open('w', encoding='utf-8') as log:
            result = subprocess.run(stage_command, cwd=work, stdin=subprocess.DEVNULL, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=240, creationflags=subprocess.CREATE_NO_WINDOW)
        if result.returncode or (work / 'failed.txt').exists() or not (work / f'{stage}-passed.txt').exists():
            raise RuntimeError(f'Lifecycle failed: {work / (stage + ".log")}')
    return {'minecraft': mc, 'paper_build': json.loads((work / 'paper-build.json').read_text())['id'],
            'jar': artifact.name, 'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
            'lifecycle': 'PASS', 'client_visual': 'NOT_RUN', 'log_directory': str(work)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare-only', action='store_true')
    parser.add_argument('--package-only', action='store_true', help='Build JARs/packs; does not claim server verification')
    parser.add_argument('--versions', nargs='+', default=list(TARGETS))
    parser.add_argument('--jdk21', type=Path, default=Path.home() / '.jdks/temurin-21.0.12.1')
    parser.add_argument('--jdk25', type=Path, default=Path.home() / '.jdks/temurin-25.0.4.1')
    parser.add_argument('--eula-file', type=Path, default=ROOT / '.tools/lifecycle-smoke-288-release/eula.txt',
                        help='Existing accepted Minecraft EULA file to copy into isolated test servers')
    args = parser.parse_args()
    if args.package_only:
        from branch_release import branch_for, package as package_branch
        for branch in dict.fromkeys(branch_for(mc) for mc in args.versions):
            print(package_branch(branch), flush=True)
        raise SystemExit(0)
    if not args.prepare_only and (not args.eula_file.is_file() or 'eula=true' not in args.eula_file.read_text().replace(' ', '').lower()):
        raise SystemExit('Provide --eula-file PATH to an existing accepted Minecraft EULA file.')
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(prepare, args.versions))
    if not args.prepare_only:
        results = []
        for mc in args.versions:
            try:
                results.append(run(mc, args.jdk21 if mc.startswith('1.') else args.jdk25, args.eula_file))
            except Exception as failure:
                print(f'FAIL {mc}: {failure}', flush=True)
                results.append({'minecraft': mc, 'lifecycle': 'FAIL', 'error': str(failure)})
            DIST.mkdir(parents=True, exist_ok=True)
            (DIST / 'verification.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
        if any(result['lifecycle'] != 'PASS' for result in results):
            raise SystemExit(1)
