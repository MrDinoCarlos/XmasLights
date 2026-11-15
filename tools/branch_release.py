"""One installable JAR per Minecraft branch, with automatic per-patch resource-pack selection."""
import hashlib
import io
import json
from pathlib import Path
import zipfile
import verify_matrix as matrix

BRANCHES = {
    '1.21.X': [v for v in matrix.TARGETS if v.startswith('1.21.')],
    '26.1.X': ['26.1.1', '26.1.2'],
    '26.2.X': ['26.2'],
    '26.3.X': ['26.3'],
}


def branch_for(mc):
    return next(branch for branch, versions in BRANCHES.items() if mc in versions)


def write(archive, name, data):
    archive.writestr(zipfile.ZipInfo(name, (2026, 11, 15, 0, 0, 0)), data,
                     compress_type=zipfile.ZIP_DEFLATED)


def pack(mc):
    major, minor = matrix.TARGETS[mc]
    result = io.BytesIO()
    source_path = matrix.ROOT / 'src/main/resources/packs/XmasLights-Models-1.21.4.zip'
    with zipfile.ZipFile(source_path) as source, zipfile.ZipFile(result, 'w') as out:
        for name in sorted(source.namelist()):
            data = source.read(name)
            if name == 'pack.mcmeta':
                meta = {'pack': {'description': f'XmasLights {matrix.VERSION} - Minecraft {mc}', 'pack_format': major}}
                if major >= 65:
                    meta['pack'].update(min_format=[major, minor], max_format=[major, minor])
                data = json.dumps(meta).encode()
            write(out, name, data)
    return result.getvalue()


def package(branch):
    versions = BRANCHES[branch]
    matrix.DIST.mkdir(parents=True, exist_ok=True)
    base_path = matrix.ROOT / f'target/XmasLights-{matrix.VERSION}.jar'
    base_hash = hashlib.sha256(base_path.read_bytes()).hexdigest()
    destination = matrix.DIST / f'XmasLights-{matrix.VERSION}+mc.v.{branch}.jar'
    properties = (f'branch={branch}\nminecraft-versions={",".join(versions)}\nbase-sha256={base_hash}\n').encode()
    if destination.exists():
        with zipfile.ZipFile(destination) as previous:
            if previous.read('compatibility.properties') == properties:
                return destination
    with zipfile.ZipFile(base_path) as base, zipfile.ZipFile(destination, 'w') as out:
        for name in sorted(base.namelist()):
            if name.startswith('packs/') and name.endswith('.zip') or name == 'compatibility.properties':
                continue
            data = base.read(name)
            if name == 'plugin.yml':
                data = data.decode().replace("api-version: '1.21.4'", f"api-version: '{versions[0]}'").encode()
            write(out, name, data)
        for mc in versions:
            data = pack(mc)
            pack_name = f'XmasLights-Models-{mc}.zip'
            write(out, 'packs/' + pack_name, data)
            folder = matrix.DIST / 'resource-packs'
            folder.mkdir(exist_ok=True)
            (folder / pack_name).write_bytes(data)
        write(out, 'compatibility.properties', properties)
    return destination


if __name__ == '__main__':
    for branch in BRANCHES:
        print(package(branch))
