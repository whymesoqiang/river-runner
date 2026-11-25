"""Stamp the running page and build a complete, runtime-specific offline manifest."""
import json
import os
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parent.parent
directory = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'web/build/dist/composeWebCompatibility/productionExecutable'
build = os.environ.get('BUILD_SHA', 'local-test')
version = re.search(r'^## (\d+\.\d+)', (root / 'CHANGELOG.md').read_text(), re.M)[1] + '.' + os.environ.get('GITHUB_RUN_NUMBER', '0')
for name in ('sw.js', 'index.html'):
    p = directory / name
    p.write_text(p.read_text().replace('__RR_BUILD__', build).replace('__RR_VERSION__', version))
common = ['index.html', 'riverRunner.js', 'viewport.js', 'updates.js', 'audio-player.js', 'compute-worker.js', 'strategyWorker.js', 'manifest.webmanifest']
common += [str(p.relative_to(directory)) for folder in ('composeResources', 'sfx') for p in (directory / folder).rglob('*') if p.is_file()]
common += [p.name for p in directory.glob('icon-*.png')]

def dependencies(entry):
    # Compose compatibility bundles contain embedded module/source-map names that
    # are not network dependencies. Only webpack's content-addressed Wasm URLs
    # are external; following every .js string would pull in both runtimes.
    binaries = set(re.findall(r'[0-9a-f]{16,}\.wasm', (directory / entry).read_text()))
    if not binaries or any(not (directory / name).is_file() for name in binaries):
        raise RuntimeError('Cannot determine runtime binaries for ' + entry)
    return sorted(set(common) | binaries | {entry})

(directory / 'release.json').write_text(json.dumps({'build': build, 'version': version, 'assets': {'js': dependencies('originJsRiverRunner.js'), 'wasm': dependencies('originWasmRiverRunner.js')}}, ensure_ascii=False))
(directory / '.nojekyll').touch()
print('Release', version, build)
