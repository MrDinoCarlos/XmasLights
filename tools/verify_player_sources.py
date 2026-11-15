"""Real player/protocol reproduction for source selection; uses only a local isolated Paper server."""
from pathlib import Path
import argparse, json, os, shutil, socket, subprocess, zipfile
import branch_release as branches
import verify_matrix as matrix

parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--work-name',default='player-source-probe'); parser.add_argument('--minecraft',choices=list(matrix.TARGETS),default='1.21.4'); parser.add_argument('--strobe-jar',type=Path,default=matrix.ROOT/'dist/StrobeLights-0.10.41+mc.v.1.21.4.jar'); options=parser.parse_args()
work=matrix.ROOT/'.tools'/options.work_name
if work.resolve().parent != (matrix.ROOT/'.tools').resolve(): raise SystemExit('work-name must be a directory name inside .tools')
if work.exists(): raise SystemExit('Keep previous evidence; choose a fresh probe directory.')
work.mkdir(parents=True)
cache=matrix.ROOT/'.tools/matrix-2.9.0'/options.minecraft
for folder in ('cache','libraries','versions'): shutil.copytree(cache/folder,work/folder,copy_function=os.link)
for name in ('paper.jar','eula.txt'): shutil.copyfile(cache/name,work/name)
with socket.socket() as sock:
    sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
(work/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=probe\nlevel-type=minecraft:flat\ngenerate-structures=false\nallow-nether=false\nview-distance=3\nsimulation-distance=2\n')
(work/'bukkit.yml').write_text('settings:\n  allow-end: false\n')
data=work/'plugins/XmasLights'; data.mkdir(parents=True)
(data/'config.yml').write_text('updates:\n  enabled: false\nmodel-pack:\n  enabled: false\n  embedded:\n    enabled: false\n')
artifact=branches.package(branches.branch_for(options.minecraft)); shutil.copyfile(artifact,work/'plugins'/artifact.name)
shutil.copyfile(options.strobe_jar,work/'plugins'/options.strobe_jar.name)
strobe_data=work/'plugins/StrobeLights'; strobe_data.mkdir()
with socket.socket() as sock:
    sock.bind(('127.0.0.1',0)); pack_port=sock.getsockname()[1]
(strobe_data/'config.yml').write_text(f'resource-pack:\n  enabled: true\n  mode: EMBEDDED\n  required: false\n  public-url: "http://127.0.0.1:{pack_port}/strobelights/{{token}}.zip"\n  embedded:\n    enabled: true\n    bind-address: "127.0.0.1"\n    port: {pack_port}\n    reuse-trp-server: false\n  nexo-integration:\n    enabled: false\nvanilla-fallback:\n  enabled: false\ndiscovery:\n  enabled: false\n')
jdk=Path.home()/('.jdks/temurin-21.0.12.1' if options.minecraft.startswith('1.') else '.jdks/temurin-25.0.4.1'); classes=work/'fixture-classes'; classes.mkdir()
cp=';'.join(str(p).replace('\\','/') for p in [artifact,*sorted((work/'libraries').rglob('*.jar'))])
args=work/'javac.args'; args.write_text('\n'.join(['--release','21','-proc:none','-cp',f'"{cp}"','-d',f'"{classes.as_posix()}"',f'"{(matrix.ROOT/"tools/player/PlayerSourceSmoke.java").as_posix()}"']))
subprocess.run([str(jdk/'bin/javac.exe'),'@'+str(args)],check=True,creationflags=subprocess.CREATE_NO_WINDOW)
with zipfile.ZipFile(work/'plugins/PlayerSourceSmoke.jar','w',zipfile.ZIP_DEFLATED) as z:
    for f in classes.rglob('*.class'): z.write(f,f.relative_to(classes).as_posix())
    z.writestr('plugin.yml',f'name: PlayerSourceSmoke\nversion: 1\napi-version: \'{options.minecraft}\'\nmain: es.astralicastudio.xmaslights.PlayerSourceSmoke\nsoftdepend: [XmasLights]\n')
command=[str(jdk/'bin/java.exe'),'-Xms256M','-Xmx1024M','-XX:ActiveProcessorCount=2','-Djavax.net.ssl.trustStoreType=Windows-ROOT','-Djavax.net.ssl.trustStore=NONE','-jar','paper.jar','--nogui']
client=None
with (work/'server.log').open('w',encoding='utf-8') as log, (work/'client.log').open('w',encoding='utf-8') as clientlog:
    server=subprocess.Popen(command,cwd=work,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=subprocess.CREATE_NO_WINDOW)
    import threading
    timer=threading.Timer(150,server.kill); timer.start()
    try:
        for line in server.stdout:
            log.write(line); log.flush()
            if 'Done (' in line and client is None:
                client=subprocess.Popen(['node',str(matrix.ROOT/'tools/player/client.cjs'),str(port),str(work/'client-packets.json'),options.minecraft],cwd=matrix.ROOT,stdout=clientlog,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        code=server.wait()
    finally:
        timer.cancel()
        if client is not None: client.wait(timeout=120)
if code or not (work/'player-passed.txt').exists(): raise SystemExit(f'FAIL: {work/"server.log"}')
packets=json.loads((work/'client-packets.json').read_text())
if not packets['projectionSpawns'] or not packets['metadataPackets'] or packets['textDisplays'] or not packets['strobePackVerified']: raise SystemExit('Real StrobeLights client contract failed')
print((work/'player-passed.txt').read_text())
print('Client received:',packets)
