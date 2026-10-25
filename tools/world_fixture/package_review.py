"""Package a verified, editable world for user review; never create a final baseline."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile

root=Path(__file__).resolve().parents[2]
evidence=root/'docs/evidence/201-world-fixture-20261025'
run=evidence/sys.argv[1]
result=json.loads((run/'201-fixture-results.json').read_text(encoding='utf-8-sig'))
checks=json.loads((run/'run.json').read_text(encoding='utf-8-sig'))
assert checks['exitCode']==0 and result['complete'] and not result['formalServerConnected']
assert not checks['finalBaselineCreated'] and checks['awaitingUserArrangement']
source=Path(result['exportDirectory']).resolve()
assert source.is_relative_to(root/'client/build/run/clientGameTest')
assert (source/'level.dat').is_file() and (source/'dimensions/minecraft/the_end/region').is_dir()
delivery=root/'runtime/201-trading-hall-review-v2'
delivery.mkdir(exist_ok=False)
world=delivery/'201交易所-待整理-20261025'
shutil.copytree(source,world)
hashes={str(p.relative_to(world)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(world.rglob('*')) if p.is_file()}
archive=delivery/'201-trading-hall-review-20261025.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for name in hashes:z.write(world/name,str(Path(world.name)/name))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for name,digest in hashes.items():assert hashlib.sha256(z.read(str(Path(world.name)/name).replace('\\','/'))).hexdigest()==digest
notes='''201 交易所：待整理的单人存档

Minecraft 26.2。使用 PCL 独立版本「26.2-自动打装备」进入单人游戏。
存档显示名：201交易所-待整理-20261025。
交易所位于末地，原坐标保留。进入位置约为 (1068.72, 57, 21.50)。
交付模式为创造模式。此存档供用户检查和整理物品，不是最终测试基准。
整理完成后退出世界保存，再通知 Codex；后续从整理后的存档建立基准副本。
不要把测试存档的整理操作改到 201 正式服。
若新账号没有读取到保存的位置，可以在本单人存档输入：
/execute in minecraft:the_end run tp @s 1068.72 57 21.50
/gamemode creative

原始快照复制范围：X=1008..1135，Y=0..255，Z=-48..79。
快照包含 94 名村民、173 个实体、208 个方块实体；13 名无业村民原本没有报价。
存档使用原版数据还原，不包含正式服的 Carpet/插件规则或远程 Servux 网络环境。
'''
(delivery/'使用说明.txt').write_text(notes,encoding='utf-8-sig')
record={'worldDirectory':str(world),'archive':str(archive),'archiveSHA256':hashlib.sha256(archive.read_bytes()).hexdigest(),
        'archiveBytes':archive.stat().st_size,'worldFiles':len(hashes),'worldHashes':hashes,
        'sourceSnapshotSHA256':checks['snapshotSHA256'],'validation':str(run),
        'finalBaselineCreated':False,'awaitingUserArrangement':True,'creativeMode':True}
(evidence/'review-package.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in record.items() if k!='worldHashes'},ensure_ascii=False,indent=2))
