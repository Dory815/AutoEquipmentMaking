"""Audit a passed three-kit run, current jar and immutable arranged baseline."""
import hashlib, io, json, re, sys, zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
evidence = root / 'docs/evidence/v11-hall-navigation-20261025'
folder = evidence / sys.argv[1]
run = json.loads((folder/'run.json').read_text(encoding='utf-8-sig'))
result = json.loads((folder/'201-navigation-results.json').read_text(encoding='utf-8-sig'))
assert run['exitCode'] == 0 and result['complete']
assert result['deliveredKits'] == 3 and result['verifiedEquipment'] == 42
assert result['sprintHoldTicks'] > 50 and result['sprintToggleTicks'] > 50
assert not run['formalServerConnected']
for name, digest in run['sources'].items():
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest() == digest, name
events = [json.loads(s) for s in (folder/'task.jsonl').read_text(encoding='utf-8-sig').splitlines() if s.strip()]
assert sum(e['event'] == 'START' for e in events) == 1
assert sum(e['event'] == 'DELIVERED' for e in events) == 3
assert sum(e['event'].startswith('PACKED ') for e in events) == 42
assert events[-1]['event'] == 'COMPLETED' and events[-1]['completed'] == 42
assert len({e['task'] for e in events}) == 1
assert not any(e['event'].startswith(('PAUSED:', 'WAITING ', 'CANCELLED')) for e in events)
costs = [int(e['event'].split('cost=')[1]) for e in events if e['event'].startswith(('RESULT CONFIRMED cost=', 'RESULT RECONCILED AFTER MENU CLOSE cost='))]
assert sum(c for c in costs if c >= 0) == 319*3
assert any('OPEN ' in e['event'] and 'x=1088, y=63, z=16' in e['event'] for e in events), 'Difficult raw source never used'
positions = [tuple(map(int, s.split(', '))) for s in result['packingPositions']]
assert all(sum((a-b)**2 for a,b in zip(p,q)) >= 100 for i,p in enumerate(positions) for q in positions[i+1:])
baseline = json.loads((root/'runtime/test-baselines/201-trading-hall-user-arranged-20261025/manifest.json').read_text(encoding='utf-8'))
for name, digest in baseline['worldHashes'].items():
    assert hashlib.sha256((Path(baseline['baselineWorld'])/name).read_bytes()).hexdigest() == digest
assert hashlib.sha256(Path(baseline['archive']).read_bytes()).hexdigest() == baseline['archiveSHA256']
version = re.search(r'^mod_version=(.+)$', (root/'gradle.properties').read_text(), re.M)[1]
artifact = root/f'client/build/libs/auto-equipment-making-client-{version}.jar'
classes = {'client':0, 'engine':0}
runtime_classes = json.loads((folder/'runtime-classes.json').read_text(encoding='utf-8-sig'))
with zipfile.ZipFile(artifact) as jar:
    metadata = json.loads(jar.read('fabric.mod.json'))
    assert metadata['version'] == version
    assert 'io/github/autoequipmentmaking/client/PlayerNavigation.class' in jar.namelist()
    for name in jar.namelist():
        assert 'gametest' not in name.lower() and 'GameTest' not in name and 'experimentalNavigation' not in name
        if name.endswith('.class'):
            assert jar.read(name) == (root/'client/build/classes/java/main'/name).read_bytes()
            assert hashlib.sha256(jar.read(name)).hexdigest() == runtime_classes['client'][name]
            classes['client'] += 1
    with zipfile.ZipFile(io.BytesIO(jar.read(f'META-INF/jars/engine-{version}.jar'))) as engine:
        for name in engine.namelist():
            if name.endswith('.class'):
                assert engine.read(name) == (root/'engine/build/classes/java/main'/name).read_bytes()
                assert hashlib.sha256(engine.read(name)).hexdigest() == runtime_classes['engine'][name]
                classes['engine'] += 1
report = {'version':version, 'revision':metadata['custom']['aem:revision'], 'artifact':str(artifact),
          'sha256':hashlib.sha256(artifact.read_bytes()).hexdigest(), 'compiledClassesMatched':classes,
          'runtimeEvidence':str(folder), 'task':result['task'], 'seed':result['seed'], 'packingPositions':positions,
          'consecutiveKits':3, 'verifiedEquipment':42, 'confirmedAnvilLevels':957,
          'sprintHoldTicks':result['sprintHoldTicks'], 'sprintToggleTicks':result['sprintToggleTicks'],
          'navigationReplans':sum(e['event'].startswith('NAV REPLAN ') for e in events),
          'basePlans':[e['event'] for e in events if e['event'].startswith('BASE PLAN ')],
          'grinds':sum(e['event'].startswith('GRIND CONFIRMED ') for e in events),
          'taskPauses':0, 'taskRestarts':0, 'rawSourceOpened':True, 'trapdoorsUnchanged':result['trapdoorsUnchanged'],
          'arrangedBaselineFilesUnchanged':len(baseline['worldHashes']), 'baselineZipUnchanged':True,
          'gameTestsExcluded':True, 'runtimeProductionSourcesMatch':True,
          'formalServerConnected':False, 'pclUpdated':False, 'remoteNetworkVerified':False}
(evidence/'package-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
