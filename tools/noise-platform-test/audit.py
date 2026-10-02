#!/usr/bin/env python3
"""Audit final per-target artifacts and execute relocated Noise using an isolated JDK-only parent."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
parser.add_argument('--java17-home', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
repo = args.repo.resolve()
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=False)
probe_dir = output / 'probe'
probe_dir.mkdir()
subprocess.run([str(args.java17_home/'bin/javac'), '--release', '17', '-d', str(probe_dir),
                str(repo/'tools/noise-platform-test/NoiseArtifactProbe.java')], check=True)
(probe_dir/'MANIFEST.MF').write_text('Manifest-Version: 1.0\nMain-Class: NoiseArtifactProbe\nPremain-Class: NoiseArtifactProbe\n\n')
probe = output/'probe.jar'
subprocess.run([str(args.java17_home/'bin/jar'), 'cfm', str(probe), str(probe_dir/'MANIFEST.MF'),
                '-C', str(probe_dir), '.'], check=True)

def properties(path):
    return dict(line.split('=',1) for line in path.read_text().splitlines() if '=' in line and not line.startswith('#'))

version = properties(repo/'gradle.properties')['mod_version']
artifacts = []
for branch in ['mods', 'paper']:
    for directory in sorted((repo/branch/'versions').iterdir()):
        if not (directory/'gradle.properties').is_file():
            continue
        loader = directory.name.rsplit('-',1)[1]
        mc_range = properties(directory/'gradle.properties')['mcVersionRange']
        artifact = directory/'build/libs'/f'server_waypoint-{version}-{loader}-mc{mc_range}.jar'
        artifacts.append((f'{branch}:{directory.name}', loader, artifact))
artifacts.append(('velocity', 'velocity', repo/'velocity/build/libs'/f'server_waypoint-{version}-velocity.jar'))
expected = {'fabric': 13, 'forge': 12, 'neoforge': 12, 'paper': 3, 'velocity': 1}
actual = dict(Counter(loader for _, loader, _ in artifacts))
if actual != expected:
    raise RuntimeError(f'Incomplete or changed version matrix: expected {expected}, found {actual}')
results=[]
for project, loader, artifact in artifacts:
    if not artifact.is_file(): raise RuntimeError(f'Missing final artifact: {artifact}')
    with zipfile.ZipFile(artifact) as jar:
        names = jar.namelist()
        assert len(names)==len(set(names)), f'Duplicate ZIP entries: {artifact}'
        noise = [n for n in names if n.startswith('_959/server_waypoint/internal/noisekk/') and n.endswith('.class')]
        assert noise, f'Missing relocated Noise in {artifact}'
        assert 'META-INF/LICENSE-noise-java' in names
        assert jar.read('META-INF/LICENSE-noise-java') == (repo/'gradle/licenses/noise-java.txt').read_bytes()
        assert '_959/server_waypoint/crossserver/transport/BackendTransport.class' in names
        assert not any(n.startswith(('com/southernstorm/noise/','com/eatthepath/noise/','org/junit/'))
                       or 'noisespike/' in n or 'NoiseArtifactProbe' in n for n in names)
        if loader=='paper':
            assert '_959/server_waypoint/ServerWaypointPaperMC.class' in names
            assert any(n.endswith('/Metrics.class') for n in names), 'Paper lost bStats'
        if loader=='velocity':
            metadata=json.loads(jar.read('velocity-plugin.json'))
            assert metadata['main']=='_959.server_waypoint.velocity.ServerWaypointVelocity'
            assert metadata['version']==version
            assert '_959/server_waypoint/proxy/ProxyPlayerRouter.class' in names
            assert not any(n.startswith(('com/velocitypowered/','com/google/inject/','org/slf4j/','io/netty/')) for n in names)
        for name in noise:
            assert int.from_bytes(jar.read(name)[6:8],'big')<=61, name
        assert len(noise)==len(set(noise))
    staged=output/'artifacts'/project.replace(':','/')/artifact.name
    staged.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(artifact,staged)
    run=subprocess.run([str(args.java17_home/'bin/java'),'-jar',str(probe),str(staged)],check=True,text=True,capture_output=True)
    result={'project':project,'loader':loader,'artifact':str(staged),'sha256':hashlib.sha256(staged.read_bytes()).hexdigest(),
            'bytes':staged.stat().st_size,'noise_classes':len(noise),'isolated_classloader':run.stdout.strip()}
    results.append(result)
    print('PASS',project,flush=True)
for module in ['common','proxy-common']:
    jars=list((repo/module/'build/libs').glob('*.jar'))
    assert jars, module
    for artifact in jars:
        with zipfile.ZipFile(artifact) as jar:
            for name in jar.namelist():
                if name.endswith('.class'):
                    code=jar.read(name)
                    assert int.from_bytes(code[6:8],'big')<=61, name
                    assert not any(p in code for p in [b'com/velocitypowered/',b'org/bukkit/',b'net/minecraft/',b'net/md_5/bungee/']), name
(output/'artifacts.json').write_text(json.dumps(results,indent=2)+'\n')
print('Verified',len(results),'artifacts; shared modules contain Java 17 bytecode and no platform API references.')
