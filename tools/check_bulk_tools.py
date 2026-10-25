"""Verify current 1.3 artifact against completed survival runs and immutable baseline."""
import hashlib
import io
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
evidence = root / 'docs/evidence/v13-bulk-tools-20261025'
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
load = lambda path: json.loads(path.read_text(encoding='utf-8-sig'))
passed = {}
for folder in sorted(evidence.iterdir()):
    if not folder.is_dir() or not (folder/'v13-results.json').is_file():
        continue
    run, result = load(folder/'run.json'), load(folder/'v13-results.json')
    if run['exitCode'] or not result.get('complete'):
        continue
    if not all(sha(Path(path)) == digest for path, digest in run['sources'].items()):
        continue
    passed[result['scenario']] = folder
assert set(passed) == {'netherite', 'diamond', 'elytra', 'newcomer', 'trade'}, passed
reports = {}
for scenario, folder in passed.items():
    run, result = load(folder/'run.json'), load(folder/'v13-results.json')
    assert not run['formalServerConnected'] and not run['arrangedBaselineOpened']
    rows = [json.loads(line) for line in (folder/'task.jsonl').read_text(encoding='utf-8-sig').splitlines() if line]
    events = [row['event'] for row in rows]
    assert events[-1] == 'COMPLETED' and len({row['task'] for row in rows}) == 1
    assert not any(e.startswith(('PAUSED:', 'WAITING ', 'CANCELLED')) for e in events)
    report = {'evidence': str(folder), 'task': result['task']}
    if scenario == 'trade':
        assert sum(e.startswith('TRADE BUY CONFIRMED') for e in events) == 4
        assert not any(e.startswith('TRADE COOLDOWN CHECK') for e in events[:events.index('COMPLETED')])
        assert sum(e.startswith('NOTICE 村民进入补货冷却') for e in events) == 1
        assert sum(e.startswith('NOTICE 村民补货完成') for e in events) == 1
        report.update(purchases=4, vendorSwitchWithoutWait=True, realTimeCooldownVerified=True)
    else:
        boxes = 2 if scenario == 'elytra' else 1
        items = 14 if scenario == 'newcomer' else 27*boxes
        assert result['deliveredBoxes'] == boxes and result['items'] == items
        assert sum(e == 'DELIVERED' for e in events) == boxes
        assert sum(e.startswith('PACKED ') for e in events) == items
        costs = [int(e.split('cost=')[1]) for e in events if e.startswith(('RESULT CONFIRMED cost=', 'RESULT RECONCILED AFTER MENU CLOSE cost='))]
        levels = sum(c for c in costs if c >= 0)
        assert levels == result['levelsSpent']
        renames = sum(e == 'RENAME INTENT 挖山镐' for e in events)
        assert renames == (27 if scenario in {'netherite', 'diamond'} else 0)
        if scenario == 'newcomer':
            assert result['reuseToggleBothPlanningStates']
            assert 'BASE PLAN netherite=0 grind=0 diamond=0 craft=11 diamonds=40 sticks=12 ingots=11 templates=11' in events
            assert levels == 319
        if scenario == 'netherite':
            assert 'BASE PLAN netherite=6 grind=6 diamond=6 craft=9 diamonds=27 sticks=18 ingots=21 templates=21' in events
            assert sum(e.startswith('GRIND CONFIRMED ') for e in events) == 6
        if scenario == 'diamond':
            assert not any(e.startswith('OPEN SmithingMenu') for e in events)
            assert result['legacySettingsPreserved']
        assert result['settingsTabsClicked'] == 3
        report.update(boxes=boxes, items=items, levels=levels, renames=renames, settingsTabsClicked=3)
    reports[scenario] = report

baseline = load(root/'runtime/test-baselines/201-trading-hall-user-arranged-20261025/manifest.json')
for name, digest in baseline['worldHashes'].items():
    assert sha(Path(baseline['baselineWorld'])/name) == digest
assert sha(Path(baseline['archive'])) == baseline['archiveSHA256']
version = re.search(r'^mod_version=(.+)$', (root/'gradle.properties').read_text(), re.M)[1]
artifact = root/f'client/build/libs/auto-equipment-making-client-{version}.jar'
classes = {'client': 0, 'engine': 0}
with zipfile.ZipFile(artifact) as jar:
    metadata = json.loads(jar.read('fabric.mod.json'))
    assert metadata['version'] == version
    assert metadata['custom']['aem:revision'] == 'bulk-tools-20261025'
    for name in jar.namelist():
        assert 'gametest' not in name.lower() and 'GameTest' not in name and 'experimentalNavigation' not in name
        if name.endswith('.class'):
            assert jar.read(name) == (root/'client/build/classes/java/main'/name).read_bytes()
            for folder in passed.values():
                assert hashlib.sha256(jar.read(name)).hexdigest() == load(folder/'runtime-classes.json')['client'][name], (folder, name)
            classes['client'] += 1
    with zipfile.ZipFile(io.BytesIO(jar.read(f'META-INF/jars/engine-{version}.jar'))) as engine:
        for name in engine.namelist():
            if name.endswith('.class'):
                assert engine.read(name) == (root/'engine/build/classes/java/main'/name).read_bytes()
                for folder in passed.values():
                    assert hashlib.sha256(engine.read(name)).hexdigest() == load(folder/'runtime-classes.json')['engine'][name], (folder, name)
                classes['engine'] += 1
tests = []
for path in (root/'engine/build/test-results/test').glob('TEST-*.xml'):
    suite = ET.parse(path).getroot()
    assert int(suite.attrib['failures']) == 0 and int(suite.attrib['errors']) == 0
    tests.extend(tc.attrib['name'] for tc in suite.findall('testcase'))
assert tests and any('finalRename' in name for name in tests)
report = dict(version=version, revision=metadata['custom']['aem:revision'], artifact=str(artifact), sha256=sha(artifact),
              runs=reports, compiledAndRuntimeClassesMatched=classes, engineTests=len(tests),
              arrangedBaselineFilesUnchanged=len(baseline['worldHashes']), baselineZipUnchanged=True,
              gameTestsExcluded=True, runtimeSourcesMatch=True, pclUpdated=False,
              formalServerConnected=False, remoteNetworkVerified=False)
(evidence/'package-checks.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
