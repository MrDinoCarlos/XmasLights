"""Compile effective 1.21.4 shaders after resource-pack overlays, with the real client fallback."""
import argparse
import json
import re
import zipfile
from pathlib import Path
import moderngl

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pack', type=Path, required=True)
parser.add_argument('--minecraft-jar', type=Path, required=True)
parser.add_argument('--report', type=Path, required=True)
args = parser.parse_args()
pack = zipfile.ZipFile(args.pack)
client = zipfile.ZipFile(args.minecraft_jar)
effective = {name: name for name in pack.namelist() if name.startswith('assets/')}
meta = json.loads(pack.read('pack.mcmeta'))
for overlay in meta.get('overlays', {}).get('entries', []):
    formats = overlay['formats']
    low, high = (formats, formats) if isinstance(formats, int) else (formats[0], formats[-1]) if isinstance(formats, list) else (formats['min_inclusive'], formats['max_inclusive'])
    if low <= 46 <= high:
        prefix = overlay['directory'] + '/'
        effective.update({name[len(prefix):]: name for name in pack.namelist() if name.startswith(prefix + 'assets/')})

def read(name):
    return (pack.read(effective[name]) if name in effective else client.read(name)).decode('utf-8')

def location(name, folder, suffix):
    ns, path = name.split(':', 1) if ':' in name else ('minecraft', name)
    return f'assets/{ns}/{folder}/{path}{suffix}'

def shader(name, nested=False, seen=None):
    seen = set() if seen is None else seen
    if name in seen:
        return ''
    seen.add(name)
    source = read(name)
    source = re.sub(r'#moj_import\s+[<"]([^>\"]+)[>\"]',
                    lambda match: shader(location(match[1], 'shaders/include', ''), True, seen), source)
    return re.sub(r'^#version[^\n]*', '', source, flags=re.M) if nested else source

ctx = moderngl.create_standalone_context(require=330)
results = []
for name in sorted(effective):
    if '/shaders/' not in name or not name.endswith('.json'):
        continue
    definition = json.loads(read(name))
    if 'vertex' not in definition or 'fragment' not in definition:
        continue
    try:
        program = ctx.program(vertex_shader=shader(location(definition['vertex'], 'shaders', '.vsh')),
                              fragment_shader=shader(location(definition['fragment'], 'shaders', '.fsh')))
        program.release()
        results.append({'program': name, 'source': effective[name], 'ok': True})
    except Exception as exc:
        results.append({'program': name, 'source': effective[name], 'ok': False, 'error': str(exc)})
report = {'pack': str(args.pack), 'gpu': ctx.info['GL_RENDERER'], 'programs': results}
args.report.write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps({'compiled': sum(p['ok'] for p in results), 'failed': [p for p in results if not p['ok']]}))
raise SystemExit(1 if any(not p['ok'] for p in results) else 0)
