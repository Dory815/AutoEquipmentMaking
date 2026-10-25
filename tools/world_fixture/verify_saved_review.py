"""Verify saved entity records and entry settings without changing the user world."""
import collections
import json
from pathlib import Path
import struct
import sys
import zlib
from inspect_nbt import entity_uuid,read_bytes,read_file

root=Path(__file__).resolve().parents[2]
world=Path(sys.argv[1]).resolve()
assert world.is_relative_to(root/'runtime') or world.is_relative_to(root/'client/build/run/clientGameTest')
source=read_file(root/'docs/evidence/201-world-fixture-20261025/201-trading-hall.nbt')
settings=read_file(world/'level.dat')['Data']
assert settings['GameType']==1 and settings['allowCommands']==1
assert settings['spawn']['dimension']=='minecraft:the_end' and list(settings['spawn']['pos'])==[1068,57,21]
end=world/'dimensions/minecraft/the_end'
saved=[]
def collect(tag):
    saved.append(tag)
    for passenger in tag.get('Passengers',[]):collect(passenger)
for region in (end/'entities').glob('*.mca'):
    data=region.read_bytes()
    if not data:continue
    for index in range(1024):
        offset=int.from_bytes(data[index*4:index*4+3],'big')*4096
        if not offset:continue
        size=struct.unpack('>I',data[offset:offset+4])[0]
        compression=data[offset+4]
        assert compression==2, (region,compression)
        tag=read_bytes(zlib.decompress(data[offset+5:offset+4+size]))
        for entity in tag.get('Entities',[]):collect(entity)
expected={entity_uuid(tag):tag for tag in source['entities']}
actual={entity_uuid(tag):tag for tag in saved}
assert len(actual)==len(saved), 'Duplicate UUIDs in saved entity trees'
assert set(expected).issubset(actual), 'Captured entity missing from disk'
for uid,tag in expected.items():
    assert actual[uid]['id']==tag['id']
    if 'Offers' in tag:assert actual[uid]['Offers']==tag['Offers'],uid
    source_passengers=[entity_uuid(p) for p in tag.get('Passengers',[])]
    saved_passengers=[entity_uuid(p) for p in actual[uid].get('Passengers',[])]
    assert source_passengers==saved_passengers,uid
report={'savedEntitiesVerified':len(expected),'savedVillagersVerified':sum(t['id']=='minecraft:villager' for t in expected.values()),
        'savedPassengerLinksVerified':sum(len(t.get('Passengers',[])) for t in expected.values()),
        'savedOfferEntriesVerified':sum(len(t.get('Offers',{}).get('Recipes',[])) for t in expected.values()),
        'entityTypes':dict(collections.Counter(t['id'] for t in expected.values())),
        'newPlayerCreativeMode':True,'commandsEnabled':True,'newPlayerSpawn':'minecraft:the_end 1068 57 21',
        'finalBaselineCreated':False,'awaitingUserArrangement':True}
(root/'docs/evidence/201-world-fixture-20261025/saved-review-checks.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
