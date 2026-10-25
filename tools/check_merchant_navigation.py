"""Bind the merchant fix to the reproduced fault, real menus, purchases and final jar."""
import hashlib
import io
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
base = root/'docs/evidence/v13-villager-navigation-20261025'
load = lambda path: json.loads(path.read_text(encoding='utf-8-sig'))
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
before = load(base/'before-01/merchant-navigation-results.json')
after = load(base/'after-03/merchant-navigation-results.json')
assert before['complete'] and before['expectedBeforeFailure']
assert before['cases'][0]['failureReproduced'] and before['cases'][0]['state'] == 'RECOVERY_REQUIRED'
assert after['complete'] and not after['expectedBeforeFailure'] and len(after['cases']) == 3
assert all(c['passenger'] and c['state'] == 'COMPLETED' and c['actualMerchantMenuOpened'] for c in after['cases'])
assert before['trapdoorsUnchanged'] == after['trapdoorsUnchanged'] == 298
original = root/'docs/evidence/artifacts/v13-tested-20261025/auto-equipment-making-client-1.3.0.jar'
assert sha(original) == '120f208c1f4dd4687e242f458e2fe750846f2503139ab1bbc8e33173b2c97ba7'
with zipfile.ZipFile(original) as jar:
    captured = load(base/'before-01/runtime-classes.json')
    for name in jar.namelist():
        if name.endswith('.class'):
            assert hashlib.sha256(jar.read(name)).hexdigest() == captured['client'][name]
trade = load(base/'trade-regression-01/v13-results.json')
assert trade['complete'] and trade['purchases'] == 4 and trade['vendorSwitchWithoutWait']
assert trade['allExhaustedWaitMsAtLeast'] == 19000
events = trade['events']
assert sum(e.startswith('TRADE BUY CONFIRMED') for e in events) == 4
assert not any(e.startswith('TRADE COOLDOWN CHECK') for e in events[:events.index('COMPLETED')])
assert sum(e.startswith('NOTICE 村民进入补货冷却') for e in events) == 1
assert sum(e.startswith('NOTICE 村民补货完成') for e in events) == 1
folders = [base/'after-03', base/'trade-regression-01']
for folder in folders:
    run = load(folder/'run.json')
    assert run['exitCode'] == 0 and not run['formalServerConnected'] and not run['arrangedBaselineOpened']
    for path, digest in run['sources'].items():
        # after-03 ran before the metadata-only 1.3.1 version bump.
        if folder.name == 'trade-regression-01' or path.endswith('.java'):
            assert sha(Path(path)) == digest, path
artifact = root/'client/build/libs/auto-equipment-making-client-1.3.1.jar'
counts = {'client': 0, 'engine': 0}
with zipfile.ZipFile(artifact) as jar:
    metadata = json.loads(jar.read('fabric.mod.json'))
    assert metadata['version'] == '1.3.1' and metadata['custom']['aem:revision'] == 'merchant-target-fix-20261025'
    for name in jar.namelist():
        assert 'gametest' not in name.lower() and 'GameTest' not in name and 'experimentalNavigation' not in name
        if name.endswith('.class'):
            assert jar.read(name) == (root/'client/build/classes/java/main'/name).read_bytes()
            for folder in folders:
                assert hashlib.sha256(jar.read(name)).hexdigest() == load(folder/'runtime-classes.json')['client'][name]
            counts['client'] += 1
    with zipfile.ZipFile(io.BytesIO(jar.read('META-INF/jars/engine-1.3.1.jar'))) as engine:
        for name in engine.namelist():
            if name.endswith('.class'):
                assert engine.read(name) == (root/'engine/build/classes/java/main'/name).read_bytes()
                for folder in folders:
                    assert hashlib.sha256(engine.read(name)).hexdigest() == load(folder/'runtime-classes.json')['engine'][name]
                counts['engine'] += 1
baseline = load(root/'runtime/test-baselines/201-trading-hall-user-arranged-20261025/manifest.json')
for path, digest in baseline['worldHashes'].items():
    assert sha(Path(baseline['baselineWorld'])/path) == digest
assert sha(Path(baseline['archive'])) == baseline['archiveSHA256']
tests = 0
for path in (root/'engine/build/test-results/test').glob('TEST-*.xml'):
    xml = ET.parse(path).getroot()
    assert int(xml.attrib['failures']) == 0 and int(xml.attrib['errors']) == 0
    tests += int(xml.attrib['tests'])
assert tests == 10
report = dict(version='1.3.1', revision=metadata['custom']['aem:revision'], artifact=str(artifact), sha256=sha(artifact),
              originalArtifactFaultReproduced=True, originalVillagerMenusOpened=3,
              cases=[dict(target=c['target'], uuid=c['uuid'], arrival=c['arrival']) for c in after['cases']],
              actualRegressionPurchases=4, vendorRotationAndRealTimeCooldownVerified=True,
              trapdoorsUnchanged=298, compiledAndRuntimeClassesMatched=counts,
              engineTests=tests, baselineFilesUnchanged=len(baseline['worldHashes']), baselineZipUnchanged=True,
              gameTestsExcluded=True, pclUpdated=False, formalServerConnected=False,
              remoteNetworkVerified=False, fullEquipmentProductionRerun=False)
(base/'package-checks.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
