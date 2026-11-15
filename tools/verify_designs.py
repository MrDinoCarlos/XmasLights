"""After lifecycle verification, test all 51 designs with and without the pack on real Paper."""
from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import json
import shutil
import subprocess
import zipfile
import verify_matrix as matrix


def verify(mc, jdk):
    work = matrix.WORK / mc
    if not (work / 'absent-passed.txt').exists():
        raise RuntimeError(f'Lifecycle not complete: {mc}')
    artifact = matrix.package(mc)
    # Preserve earlier per-patch plugin JARs outside plugins so Paper sees exactly one XmasLights.
    for previous in (work / 'plugins').glob('XmasLights-*.jar'):
        if previous.name != artifact.name:
            archive = work / 'previous-test-artifacts'
            archive.mkdir(exist_ok=True)
            previous.replace(archive / previous.name)
    shutil.copyfile(artifact, work / 'plugins' / artifact.name)
    data = work / 'plugins/XmasLights'
    data.mkdir(exist_ok=True)
    (data / 'config.yml').write_text('updates:\n  enabled: false\nmodel-pack:\n  enabled: false\n  embedded:\n    enabled: false\n')
    classes = work / 'fixture-classes'
    classpath = ';'.join(str(p).replace('\\', '/') for p in [artifact, *sorted((work / 'libraries').rglob('*.jar'))])
    compile_args = work / 'designs-javac.args'
    compile_args.write_text('\n'.join(['--release', '21', '-proc:none', '-cp', f'"{classpath}"',
        '-d', f'"{classes.as_posix()}"', *[f'"{p.as_posix()}"' for p in (matrix.ROOT / 'tools/lifecycle').glob('*.java')]]))
    result = subprocess.run([str(jdk / 'bin/javac.exe'), '@' + str(compile_args)], cwd=work,
                            capture_output=True, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    (work / 'designs-compile.log').write_text(result.stdout + result.stderr, encoding='utf-8')
    if result.returncode:
        raise RuntimeError(f'Compile failed: {work / "designs-compile.log"}')
    with zipfile.ZipFile(work / 'plugins/LifecycleSmoke.jar', 'w', zipfile.ZIP_DEFLATED) as out:
        for path in classes.rglob('*.class'):
            out.write(path, path.relative_to(classes).as_posix())
        out.writestr('plugin.yml', f'name: LifecycleSmoke\nversion: 1\napi-version: \'{mc}\'\nmain: lifecycle.AllDesignsSmoke\nsoftdepend: [XmasLights]\n')
    command = [str(jdk / 'bin/java.exe'), '-Xms256M', '-Xmx1024M', '-XX:ActiveProcessorCount=2',
        '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Djavax.net.ssl.trustStoreType=Windows-ROOT',
        '-Djavax.net.ssl.trustStore=NONE', '-jar', 'paper.jar', '--nogui']
    with (work / 'designs.log').open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=work, stdin=subprocess.DEVNULL, stdout=log,
            stderr=subprocess.STDOUT, timeout=240, creationflags=subprocess.CREATE_NO_WINDOW)
    if result.returncode or (work / 'failed.txt').exists() or not (work / 'designs-passed.txt').exists():
        raise RuntimeError(f'Designs failed: {work / "designs.log"}')
    print(f'PASS {mc}: 51 designs x 2 model modes; actual StrobeLights carrier contract; no surface decals', flush=True)
    return {'minecraft': mc, 'jar': artifact.name, 'designs': 51, 'pack_modes': 2, 'result': 'PASS',
            'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', nargs='+', default=list(matrix.TARGETS))
    parser.add_argument('--report', default='designs-verification.json')
    parser.add_argument('--jdk21', type=Path, default=Path.home() / '.jdks/temurin-21.0.12.1')
    parser.add_argument('--jdk25', type=Path, default=Path.home() / '.jdks/temurin-25.0.4.1')
    args = parser.parse_args()
    from branch_release import BRANCHES, package
    for branch in BRANCHES:
        package(branch)
    def run(mc):
        jdk = args.jdk21 if mc.startswith('1.') else args.jdk25
        return verify(mc, jdk)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(run, args.versions))
    (matrix.DIST / args.report).write_text(json.dumps(results, indent=2), encoding='utf-8')
