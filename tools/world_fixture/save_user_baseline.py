"""Freeze the user's saved review world as a verified, never-tested baseline."""
import hashlib
import json
import msvcrt
from pathlib import Path
import shutil
import zipfile

root=Path(__file__).resolve().parents[2]
source=Path('D:/MC/PCL 正式版 2.10.6/.minecraft/versions/26.2-自动打装备/saves/201交易所-待整理-20261025').resolve()
destination=root/'runtime/test-baselines/201-trading-hall-user-arranged-20261025'
evidence=root/'docs/evidence/201-user-baseline-20261025'
assert source.is_dir() and (source/'level.dat').is_file()
assert not destination.exists() and not evidence.exists(), 'Never overwrite a saved baseline'

def hashes(directory):
    return {p.relative_to(directory).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(directory.rglob('*')) if p.is_file() and p.name!='session.lock'}

# Minecraft locks session.lock for the lifetime of an open world. Check an overlapping
# byte-range lock and hold it throughout the copy. Do not write to the user's files.
with (source/'session.lock').open('r+b') as lock:
    msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
    try:
        before=hashes(source)
        world=destination/'world';world.mkdir(parents=True)
        for name in before:
            target=world/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source/name,target)
        assert hashes(source)==before, 'Source changed while taking baseline'
        assert hashes(world)==before, 'Baseline copy differs'
    finally:
        lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_UNLCK,1)
archive=destination/'201-trading-hall-baseline-20261025.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for name in before:z.write(world/name,'world/'+name)
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for name,digest in before.items():assert hashlib.sha256(z.read('world/'+name)).hexdigest()==digest
record={'name':'201-trading-hall-user-arranged-20261025','sourceWorld':str(source),'baselineWorld':str(world),
        'archive':str(archive),'archiveSHA256':hashlib.sha256(archive.read_bytes()).hexdigest(),
        'worldFiles':len(before),'worldHashes':before,'sourceUnchanged':True,'sessionLockHeldWhileCopying':True,
        'sessionLockExcluded':True,'finalBaselineCreated':True,'userArrangementPreserved':True,
        'difficultContainerPositionPreserved':True,'difficultPositionNavigationTested':False,
        'testPolicy':'Clone baseline before scene tests; v1.1 partial-equipment feature may use old simple singleplayer fixtures per user exception.'}
evidence.mkdir(parents=True)
for p in [destination/'manifest.json',evidence/'baseline.json']:p.write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in record.items() if k!='worldHashes'},ensure_ascii=False,indent=2))
