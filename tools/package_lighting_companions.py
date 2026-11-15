"""Verify built lighting hosts and GPU reports before package_release.py.

Requires --strobe-project, --trp-project, --hybrid-pack, three --gpu-report
and two --optifine-report arguments. Does not build projects, modify servers or bundle Minecraft assets.
"""
from pathlib import Path
import argparse
import hashlib
import io
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
STROBE_JAR = 'StrobeLights-0.10.40+mc.v.1.21.4.jar'
TRP_JAR = 'TRP-ServerEdition-0.4.19+mc.v.1.21.4.jar'
STABLE_PROTOCOL = 'slots3x3_depth_v1'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def test_totals(project, minimum):
    reports = [ET.parse(path).getroot()
               for path in (project / 'build/test-results/test').glob('TEST-*.xml')]
    totals = {key: sum(int(report.attrib[key]) for report in reports)
              for key in ('tests', 'failures', 'errors', 'skipped')}
    require(totals['tests'] >= minimum and not any(totals[key] for key in
            ('failures', 'errors', 'skipped')), f'Failed or missing tests in {project}: {totals}')
    return totals


def validate_jar(path, version, embedded_pack, java_major):
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, f'Corrupt JAR: {path.name}')
        descriptor = archive.read('plugin.yml').decode('utf-8')
        require(f"version: '{version}'" in descriptor and '1.21.4' in descriptor,
                f'Wrong target or version: {path.name}')
        classes = [name for name in archive.namelist()
                   if name.startswith('es/mrdino/') and name.endswith('.class')]
        require(bool(classes), f'No plugin classes: {path.name}')
        require(all(int.from_bytes(archive.read(name)[6:8], 'big') == java_major
                    for name in classes), f'Unexpected bytecode: {path.name}')
        return archive.read(embedded_pack)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--strobe-project', type=Path, required=True)
    parser.add_argument('--trp-project', type=Path, required=True)
    parser.add_argument('--hybrid-pack', type=Path, required=True)
    parser.add_argument('--gpu-report', type=Path, action='append', required=True)
    parser.add_argument('--optifine-report', type=Path, action='append', required=True)
    args = parser.parse_args()
    strobe = args.strobe_project / 'build/libs' / STROBE_JAR
    trp = args.trp_project / 'build/libs' / TRP_JAR
    tests = {'strobelights': test_totals(args.strobe_project, 75),
             'trp': test_totals(args.trp_project, 310)}
    strobe_pack = validate_jar(strobe, '0.10.40',
                             'embedded/StrobeLights-ResourcePack-1.21.4.zip', 65)
    validate_jar(trp, '0.4.19', 'embedded/TRP-Live-ResourcePack.zip', 61)
    with zipfile.ZipFile(io.BytesIO(strobe_pack)) as archive:
        manifest = json.loads(archive.read('assets/strobelights/strobelights-integration.json'))
        require(manifest.get('stable_source_protocol') == STABLE_PROTOCOL,
                'Stable source protocol missing in the embedded pack')
        require(manifest.get('stable_source_coordinate_limit', 0) >= 1024,
                'The carrier pack does not cover diagonal 30/32-chunk views')
        require(json.loads(archive.read('pack.mcmeta'))['pack']['pack_format'] == 46,
                'Wrong StrobeLights pack target')
        for stage in ('common', 'packet', 'vertex', 'fragment'):
            path = f'assets/strobelights/shaders/include/stable_slot_{stage}.glsl'
            require(archive.read(path) == (args.strobe_project / 'resource-pack' / path).read_bytes(),
                    f'Embedded shader differs from its source: {path}')
    with zipfile.ZipFile(args.hybrid_pack) as archive:
        require(archive.testzip() is None, 'Corrupt combined GPU fixture')
        cores = [archive.read(name) for name in archive.namelist()
                 if '/shaders/core/' in name and name.endswith('.vsh')]
        require(any(b'stable_slot_vertex.glsl' in content for content in cores),
                'The production TRP merge did not insert the stable extension')

    profiles = []
    for path in args.gpu_report:
        report = json.loads(path.read_text(encoding='utf-8'))
        require(report['strobe_jar_sha256'] == sha256(strobe), f'Stale GPU report: {path}')
        hybrid = report['hybrid_pack_sha256']
        require(hybrid is None or hybrid == sha256(args.hybrid_pack), f'Stale hybrid report: {path}')
        require(report['stable'] and report['occupied'] and report['pipeline']
                and not report['source_pack_override'], f'Incomplete GPU scenario: {path}')
        cases = report['cases']
        require(len(cases) == 240, f'Expected 240 GPU cases: {path}')
        missing = sum(abs(case['sources'] - case['decoded']) for case in cases)
        corrupt = sum(len(case['corrupt']) for case in cases)
        error = max(case['position_error'] for case in cases)
        require(missing == corrupt == report['compositor_packet_leaks'] == 0 and error < .004,
                f'GPU verification failed: {path}')
        lighting = report.get('lighting_cases', [])
        require(len(lighting) == 20 and min(case['center_red'] for case in lighting) > .10
                and max(case['distance'] for case in lighting) >= 768,
                f'Missing long-distance RGB light-pass verification: {path}')
        require({480, 512, 680, 768}.issubset({case['distance'] for case in cases}),
                f'Missing 30/32-chunk transport cases: {path}')
        profiles.append({
            'profile': 'TRP combined' if hybrid else 'StrobeLights standalone',
            'gpu': report['gpu'], 'resolution': report['size'], 'cases': len(cases),
            'far_plane': report.get('far_plane'),
            'source_counts': sorted({case['sources'] for case in cases}),
            'distances_blocks': sorted({case['distance'] for case in cases}),
            'yaw_degrees': sorted({case['yaw'] for case in cases}),
            'fov_degrees': sorted({case['fov'] for case in cases}),
            'lighting_cases': len(lighting), 'missing': missing, 'corrupt': corrupt, 'max_position_error_blocks': error,
            'compositor_packet_leaks': 0, 'occupied_item_layer': True,
            'hybrid_pack_sha256': hybrid,
        })
    require(len(profiles) == 3 and sum(p['cases'] for p in profiles) == 720,
            'Expected one standalone and two combined GPU profiles')
    require(sum(p['profile'] == 'StrobeLights standalone' for p in profiles) == 1,
            'Missing standalone GPU verification')
    require({1024, 1920, 2048} == {p['far_plane'] for p in profiles},
            'Missing actual Minecraft 30/32-chunk camera projections')
    optifine_profiles = []
    for path in args.optifine_report:
        report = json.loads(path.read_text(encoding='utf-8'))
        require(report['strobe_jar_sha256'] == sha256(strobe), f'Stale OptiFine report: {path}')
        require(report['hybrid_pack_sha256'] in (None, sha256(args.hybrid_pack)), f'Stale hybrid report: {path}')
        require(report['carrier_rgba'] == [51, 204, 102, 5] and report['pipeline']
                and report['occupied'] and report['stable'] and not report['source_pack_override'],
                f'OptiFine client-prepared texture not tested: {path}')
        require(len(report['cases']) == 240 and all(c['sources'] == c['decoded']
                and not c['corrupt'] and c['position_error'] < .004 for c in report['cases']),
                f'OptiFine transport failed: {path}')
        require(report['compositor_packet_leaks'] == 0 and len(report['lighting_cases']) == 20
                and all(c['center_red'] > .10 for c in report['lighting_cases']),
                f'OptiFine illumination/composition failed: {path}')
        optifine_profiles.append({
            'profile': 'TRP combined' if report['hybrid_pack_sha256'] else 'StrobeLights standalone',
            'hybrid_pack_sha256': report['hybrid_pack_sha256'],
            'carrier_rgba': report['carrier_rgba'], 'cases': len(report['cases']),
            'lighting_cases': len(report['lighting_cases']), 'missing': 0, 'corrupt': 0,
            'compositor_packet_leaks': 0, 'gpu': report['gpu'], 'resolution': report['size'],
            'max_position_error_blocks': max(c['position_error'] for c in report['cases']),
        })
    require(len(optifine_profiles) == 2 and sum(p['hybrid_pack_sha256'] is None for p in optifine_profiles) == 1,
            'OptiFine standalone and TRP-combined reports are required')
    summary = {
        'xmaslights_version': ET.parse(ROOT / 'pom.xml').findtext('{http://maven.apache.org/POM/4.0.0}version'), 'minecraft_version': '1.21.4',
        'stable_source_protocol': STABLE_PROTOCOL,
        'hosts': {strobe.name: sha256(strobe), trp.name: sha256(trp)},
        'host_tests': tests, 'gpu_cases': 720, 'profiles': profiles,
        'optifine_gpu_cases': 480, 'optifine_profiles': optifine_profiles,
        'scope': 'Real GLSL transport, filtering, deduplication, six aggregation passes, RGB light passes to 768 blocks and Fabulous composition in synthetic OpenGL scenes.',
        'client_acceptance': 'Pending in Minecraft and the exact OptiFine installation. Fabulous and external shaderpacks disabled are required.',
        'model_previews': 'Blender geometry previews use simulated studio lighting.',
    }
    (ROOT / 'previews/rgb-gpu-verification.json').write_text(
        json.dumps(summary, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    for artifact in (strobe, trp):
        shutil.copyfile(artifact, dist / artifact.name)
    project = args.strobe_project
    files = [project / name for name in ('build.gradle.kts', 'settings.gradle.kts',
             'gradlew', 'gradlew.bat', 'README.md', '.gitignore', 'LICENSE', 'LICENSE.md',
             'LICENSE.txt', 'NOTICE', 'CREDITS.txt') if (project / name).is_file()]
    for folder in ('gradle', 'src', 'resource-pack'):
        files.extend(path for path in (project / folder).rglob('*') if path.is_file())
    source = dist / 'StrobeLights-0.10.40-paper-1.21.4-source.zip'
    temporary = source.with_suffix('.tmp')
    try:
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(files):
                archive.write(path, path.relative_to(project).as_posix())
        with zipfile.ZipFile(temporary) as archive:
            require(archive.testzip() is None, 'Corrupt StrobeLights source archive')
            for name in ('build.gradle.kts', 'settings.gradle.kts', 'gradlew',
                         'gradle/wrapper/gradle-wrapper.jar', 'README.md',
                         'resource-pack/assets/strobelights/shaders/include/stable_slot_packet.glsl'):
                require(name in archive.namelist(), f'Source archive missing {name}')
        temporary.replace(source)
    finally:
        temporary.unlink(missing_ok=True)
    print(json.dumps({'gpu_cases': 1200, 'host_tests': tests,
                      'artifacts': [strobe.name, trp.name, source.name]}))


if __name__ == '__main__':
    main()
