"""Test the SAME branch JAR on every supported Paper patch, using fresh isolated worlds."""
from pathlib import Path
import argparse
import concurrent.futures
import json
import os
import shutil
import verify_matrix as matrix
import verify_designs
from branch_release import BRANCHES, package


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', nargs='+', choices=list(matrix.TARGETS), default=list(matrix.TARGETS))
    parser.add_argument('--jdk21', type=Path, default=Path.home() / '.jdks/temurin-21.0.12.1')
    parser.add_argument('--jdk25', type=Path, default=Path.home() / '.jdks/temurin-25.0.4.1')
    parser.add_argument('--work-name', default='branch-matrix-' + matrix.VERSION)
    parser.add_argument('--paper-cache', type=Path, default=matrix.ROOT / '.tools/matrix-2.9.0')
    args = parser.parse_args()
    cache_root = args.paper_cache
    matrix.WORK = matrix.ROOT / '.tools' / args.work_name
    if matrix.WORK.resolve().parent != (matrix.ROOT / '.tools').resolve():
        raise SystemExit('work-name must be a directory name inside .tools')
    for branch in BRANCHES:
        package(branch)

    def run(mc):
        previous, work = cache_root / mc, matrix.WORK / mc
        if work.exists():
            raise RuntimeError(f'Previous branch evidence retained at {work}; use a fresh work directory')
        work.mkdir(parents=True)
        for directory in ('libraries', 'versions', 'cache'):
            # Immutable JAR caches only. Worlds, configs and plugin data are always fresh.
            shutil.copytree(previous / directory, work / directory, copy_function=os.link)
        for name in ('paper.jar', 'paper-build.json'):
            shutil.copyfile(previous / name, work / name)
        jdk = args.jdk21 if mc.startswith('1.') else args.jdk25
        lifecycle = matrix.run(mc, jdk, previous / 'eula.txt', bootstrap=False)
        designs = verify_designs.verify(mc, jdk)
        lifecycle.update(designs)
        return lifecycle

    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        futures = {pool.submit(run, mc): mc for mc in args.versions}
        for future in concurrent.futures.as_completed(futures):
            mc = futures[future]
            try:
                results.append(future.result())
            except Exception as failure:
                print(f'FAIL {mc}: {failure}', flush=True)
                results.append({'minecraft': mc, 'lifecycle': 'FAIL', 'error': str(failure)})
            results.sort(key=lambda r: list(matrix.TARGETS).index(r['minecraft']))
            (matrix.DIST / 'verification.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    if any(entry['lifecycle'] != 'PASS' for entry in results):
        raise SystemExit(1)
