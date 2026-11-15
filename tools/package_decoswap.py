"""Package the optional, separately licensed DecoSwap companion after its Gradle build.

python tools/package_decoswap.py --project <DecoSwap checkout>
No dependency classes are inserted into the XmasLights JAR or resource pack.
"""
from pathlib import Path
import argparse, hashlib, shutil, xml.etree.ElementTree as ET, zipfile

parser=argparse.ArgumentParser()
parser.add_argument('--project',required=True,type=Path)
args=parser.parse_args()
project=args.project.resolve()
root=Path(__file__).resolve().parents[1]
dist=root/'dist';dist.mkdir(exist_ok=True)
version='0.1.18'
jar=project/'build/libs'/f'DecoSwap-{version}-paper-1.21.4-1.21.11.jar'
reports=[ET.parse(p).getroot() for p in (project/'build/test-results/test').glob('TEST-*.xml')]
totals={key:sum(int(report.attrib.get(key,0)) for report in reports) for key in ['tests','failures','errors','skipped']}
if totals['tests']<50 or any(totals[key] for key in ['failures','errors','skipped']):
    raise RuntimeError(f'DecoSwap tests must pass first: {totals}')
with zipfile.ZipFile(jar) as archive:
    if archive.testzip() or 'es/mrdino/decoswap/compat/XmasLightsIntegration.class' not in archive.namelist():
        raise RuntimeError('Missing or corrupt DecoSwap integration')
    if f'version: \'{version}\'' not in archive.read('plugin.yml').decode('utf-8'):
        raise RuntimeError('Wrong companion version')
    if not any(name.startswith('META-INF/LICENSE') for name in archive.namelist()):
        raise RuntimeError('Missing companion license')
    classes=[name for name in archive.namelist() if name.endswith('.class')]
    if not classes or any(int.from_bytes(archive.read(name)[6:8],'big')!=65 for name in classes):
        raise RuntimeError('Expected Java 21 bytecode for the Paper 1.21.4 companion')
shutil.copyfile(jar,dist/jar.name)
source=dist/f'DecoSwap-{version}-source.zip'
files=[]
for directory in ['src','gradle','docs','.github']:
    files.extend(p for p in (project/directory).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
for name in ['README.md','LICENSE','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat','.gitignore','.editorconfig','EXPORTS.md']:
    if (project/name).is_file():files.append(project/name)
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(set(files)):archive.write(path,path.relative_to(project).as_posix())
    archive.write(jar,'release/'+jar.name)
    archive.write(root/'docs/DECOSWAP.md','release/XMASLIGHTS-INTEGRATION.md')
    archive.write(root/'docs/decoswap-0.1.18-integration.patch','release/xmaslights-integration.patch')
    archive.writestr('release/VERIFICATION.txt',f'DecoSwap {version}\nBase tests: {totals}\nBuilt separately; includes PolyForm Noncommercial License 1.0.0.\n')
with zipfile.ZipFile(source) as archive:
    if archive.testzip() or 'build.gradle.kts' not in archive.namelist() or 'LICENSE' not in archive.namelist():raise RuntimeError('Invalid companion source archive')
checksum=dist/'SHA256SUMS.txt'
entries={}
if checksum.exists():
    for line in checksum.read_text(encoding='utf-8').splitlines():
        digest,name=line.split('  ',1);entries[name]=digest
for path in [dist/jar.name,source]:entries[path.name]=hashlib.sha256(path.read_bytes()).hexdigest()
checksum.write_text(''.join(f'{digest}  {name}\n' for name,digest in sorted(entries.items())),encoding='utf-8')
print('DecoSwap base tests:',totals)
for path in [dist/jar.name,source]:print(path.name+f': {path.stat().st_size:,} bytes')
