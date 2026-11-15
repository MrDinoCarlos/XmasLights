"""Seal four branch JARs only after the same binaries pass every supported Paper patch."""
import hashlib
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile
import verify_matrix as matrix
from branch_release import BRANCHES

ROOT, DIST, VERSION = matrix.ROOT, matrix.DIST, matrix.VERSION

def require(condition, message):
    if not condition: raise RuntimeError(message)

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def code_hash(archive):
    digest=hashlib.sha256()
    for name in sorted(n for n in archive.namelist() if n.endswith('.class')):
        digest.update(name.encode()); digest.update(archive.read(name))
    return digest.hexdigest()

reports=[ET.parse(p).getroot() for p in (ROOT/'target/surefire-reports').glob('TEST-*.xml')]
totals={key:sum(int(r.attrib[key]) for r in reports) for key in ('tests','failures','errors','skipped')}
require(totals['tests']>=230 and not any(totals[k] for k in ('failures','errors','skipped')),f'Unit checks failed: {totals}')
results=json.loads((DIST/'verification.json').read_text(encoding='utf-8'))
require({r['minecraft'] for r in results}==set(matrix.TARGETS),'Incomplete branch/Paper matrix')
require(all(r['lifecycle']=='PASS' and r['result']=='PASS' for r in results),'A branch/Paper check failed')
with zipfile.ZipFile(ROOT/f'target/XmasLights-{VERSION}.jar') as base: baseline=code_hash(base)
artifacts=[]
for branch, versions in BRANCHES.items():
    jar=DIST/f'XmasLights-{VERSION}+mc.v.{branch}.jar'
    digest=sha(jar)
    tested=[r for r in results if r['minecraft'] in versions]
    require(len(tested)==len(versions) and all(r['jar']==jar.name and r['sha256']==digest for r in tested),f'Branch JAR changed after testing: {branch}')
    with zipfile.ZipFile(jar) as archive:
        require(archive.testzip() is None,f'Corrupt JAR: {branch}')
        require(code_hash(archive)==baseline,f'Unexpected bytecode: {branch}')
        descriptor=archive.read('plugin.yml').decode()
        require(f"api-version: '{versions[0]}'" in descriptor,f'Wrong minimum API: {branch}')
        props=archive.read('compatibility.properties').decode()
        require(f'branch={branch}\n' in props and f'minecraft-versions={",".join(versions)}\n' in props,f'Wrong branch selector: {branch}')
        embedded=[n for n in archive.namelist() if n.startswith('packs/') and n.endswith('.zip')]
        require(len(embedded)==len(versions),f'Missing per-patch packs: {branch}')
        for mc in versions:
            name=f'XmasLights-Models-{mc}.zip'; data=archive.read('packs/'+name)
            external=DIST/'resource-packs'/name
            require(data==external.read_bytes(),f'Pack export differs: {mc}')
            with zipfile.ZipFile(io.BytesIO(data)) as pack:
                require(pack.testzip() is None,f'Corrupt pack: {mc}')
                require(json.loads(pack.read('pack.mcmeta'))['pack']['pack_format']==matrix.TARGETS[mc][0],f'Pack format: {mc}')
                require(not any('/font/surface' in n for n in pack.namelist()),f'Rejected surface assets still present: {mc}')
            artifacts.append(external)
    artifacts.append(jar)
    for entry in tested:
        entry['branch']=branch; entry['unit_tests']=totals['tests']; entry['bytecode_sha256']=baseline
        entry['client_visual']='NOT_RUN: capture unavailable; protocol-client projection receipt tested separately'
require(len(list(DIST.glob('*.jar')))==4,'The installable delivery must contain exactly four JARs')
# Real StrobeLights host, confirmed resource-pack download and real RGB item metadata.
probes=[('player-strobe-capture-291','1.21.X')]
for name, branch in probes:
    work=ROOT/'.tools'/name
    require((work/'player-passed.txt').exists(),f'Missing actual StrobeLights proof: {name}')
    packets=json.loads((work/'client-packets.json').read_text())
    require(packets['projectionSpawns']>0 and packets['metadataPackets']>0 and packets['textDisplays']==0 and packets['strobePackVerified'],f'Actual StrobeLights client contract failed: {name}')
    installed=work/'plugins'/f'XmasLights-{VERSION}+mc.v.{branch}.jar'
    require(sha(installed)==sha(DIST/installed.name),f'StrobeLights integration used a different JAR: {name}')
    require('floor=true, wall=true, TextDisplay=0' in (work/'player-passed.txt').read_text(),'Not a real floor/wall carrier test')
strobe=ROOT/'dist/StrobeLights-0.10.41+mc.v.1.21.4.jar'
gpu_reports=[ROOT/'.tools/strobe-runtime-291-gpu.json',ROOT/'.tools/strobe-optifine-291-gpu.json']
for path in gpu_reports:
    gpu=json.loads(path.read_text())
    require(gpu['strobe_jar_sha256']==sha(strobe),'GPU proof used a different StrobeLights JAR')
    require(gpu['stable'] and gpu['pipeline'] and gpu['occupied'] and not gpu['source_pack_override'],'Not the actual StrobeLights pipeline')
    require(len(gpu['cases'])==240 and all(c['sources']==c['decoded'] and not c['corrupt'] for c in gpu['cases']),'StrobeLights GPU transport failed')
    require(gpu['runtime_replay']['decoded']==7 and not gpu['runtime_replay']['corrupt'],'Captured Paper carriers failed on the actual shader')
    require(len(gpu['lighting_cases'])==20 and all(c['center_red']>.10 for c in gpu['lighting_cases']) and gpu['compositor_packet_leaks']==0,'Real RGB lighting/composition failed')
for entry in results:
    entry['renderer']='StrobeLights only; no TextDisplay or decals'
    entry['strobe_rgb_integration']='real StrobeLights 0.10.41 tested on 1.21.4' if entry['minecraft']=='1.21.4' else 'requires a compatible StrobeLights host/pack; API lifecycle tested separately'
(DIST/'verification.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
evidence=DIST/f'XmasLights-{VERSION}-tests.zip'
with zipfile.ZipFile(evidence,'w',zipfile.ZIP_DEFLATED) as archive:
    for entry in results:
        work=Path(entry['log_directory'])
        require(work.resolve().is_relative_to((ROOT/'.tools').resolve()),'Evidence outside test workspace')
        for pattern in ('*.log','*-passed.txt','paper-build.json'):
            for path in sorted(work.glob(pattern)): archive.write(path,entry['minecraft']+'/'+path.name)
    for name in [name for name,branch in probes]:
        for pattern in ('*.log','client-packets.json','player-*.txt','strobe-runtime-sources.json'):
            for path in sorted((ROOT/'.tools'/name).glob(pattern)): archive.write(path,'connected-client/'+name+'/'+path.name)
    for path in gpu_reports: archive.write(path,'gpu/'+path.name)
    for path in sorted((ROOT/'target/surefire-reports').glob('TEST-*.xml')): archive.write(path,'unit/'+path.name)
artifacts.extend([evidence,DIST/'verification.json'])
source=DIST/f'XmasLights-{VERSION}-source.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED) as archive:
    for name in ('pom.xml','build.ps1','mvnw','mvnw.cmd','README.md','.gitignore','.editorconfig','.gitattributes'): archive.write(ROOT/name,name)
    for folder in ('.mvn','.github','src','docs','tools','model-pack','previews','original'):
        for path in sorted((ROOT/folder).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts and path.suffix not in ('.pyc','.blend1','.log'):
                archive.write(path,path.relative_to(ROOT).as_posix())
    for path in artifacts: archive.write(path,'release/'+path.relative_to(DIST).as_posix())
with zipfile.ZipFile(source) as archive: require(archive.testzip() is None,'Corrupt source ZIP')
artifacts.append(source)
(DIST/'SHA256SUMS.txt').write_text(''.join(f'{sha(p)}  {p.relative_to(DIST).as_posix()}\n' for p in artifacts),encoding='utf-8')
print(f'XmasLights {VERSION}: 4 branch JARs, {len(results)} real Paper lifecycles, 51 designs x 2 modes, {totals["tests"]} unit tests.')
print('Actual StrobeLights 0.10.41: floor/wall carriers, pack acceptance, 0 TextDisplays; 480 GPU cases + runtime replay PASS.')
print(f'Delivery: {DIST}')
