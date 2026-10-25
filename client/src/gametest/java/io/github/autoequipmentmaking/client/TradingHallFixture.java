package io.github.autoequipmentmaking.client;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/** Immutable source snapshot; import into a fresh independent singleplayer save. */
final class TradingHallFixture {
    static final Path SNAPSHOT=findSnapshot();
    static Path findSnapshot(){
        String configured=System.getProperty("aem.fixture.snapshot",System.getenv("AEM_FIXTURE_SNAPSHOT"));
        if(configured!=null)return Path.of(configured).toAbsolutePath().normalize();
        for(Path dir=Path.of("").toAbsolutePath();dir!=null;dir=dir.getParent()){
            Path candidate=dir.resolve("test-fixtures/201-trading-hall/201-trading-hall.nbt");
            if(Files.isRegularFile(candidate))return candidate;
        }
        throw new IllegalStateException("201 fixture missing; supply AEM_FIXTURE_SNAPSHOT. See docs/201_TEST_WORLD.md");
    }
    static CompoundTag read() throws Exception {
        var data=NbtIo.readCompressed(SNAPSHOT,NbtAccounter.unlimitedHeap());
        if(!data.getStringOr("format","").equals("aem-world-fixture-v1")||!data.getStringOr("dimension","").equals("minecraft:the_end"))throw new IllegalStateException("Wrong fixture format/dimension");
        return data;
    }
    static BlockState[] palette(ServerLevel level,CompoundTag data){
        var tags=data.getListOrEmpty("palette");var result=new BlockState[tags.size()];
        for(int i=0;i<result.length;i++)result[i]=NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK),tags.getCompoundOrEmpty(i));
        return result;
    }
    static ServerLevel install(MinecraftServer server,CompoundTag data){
        var level=Objects.requireNonNull(server.getLevel(Level.END));
        int[] min=data.getIntArray("min").orElseThrow(),size=data.getIntArray("size").orElseThrow(),states=data.getIntArray("states").orElseThrow();
        if(min.length!=3||size.length!=3||states.length!=Math.multiplyExact(Math.multiplyExact(size[0],size[1]),size[2]))throw new IllegalStateException("Corrupt fixture dimensions");
        var palette=palette(level,data);var pos=new BlockPos.MutableBlockPos();
        // Keep all imported chunks loaded until validation and save finish.
        for(int cx=min[0]>>4;cx<(min[0]+size[0])>>4;cx++)for(int cz=min[2]>>4;cz<(min[2]+size[2])>>4;cz++)level.setChunkForced(cx,cz,true);
        int i=0;
        for(int y=0;y<size[1];y++)for(int z=0;z<size[2];z++)for(int x=0;x<size[0];x++){
            pos.set(min[0]+x,min[1]+y,min[2]+z);int index=states[i++];
            if(index<0||index>=palette.length)throw new IllegalStateException("Corrupt palette index");
            var state=palette[index];
            if(level.getBlockState(pos)!=state)level.setBlock(pos,state,Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SUPPRESS_DROPS|Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS|Block.UPDATE_SKIP_ON_PLACE);
        }
        for(var raw:data.getListOrEmpty("blockEntities")){
            var tag=(CompoundTag)raw;var at=new BlockPos(tag.getIntOr("x",0),tag.getIntOr("y",0),tag.getIntOr("z",0));
            var be=BlockEntity.loadStatic(at,level.getBlockState(at),tag.copy(),level.registryAccess());
            if(be==null)throw new IllegalStateException("Cannot restore block entity at "+at);
            level.setBlockEntity(be);be.setChanged();
        }
        // The client lists minecart passengers separately. Use their individual authoritative
        // records, then restore riding links; parent NBT may contain older copies.
        for(var raw:data.getListOrEmpty("entities")){
            var tag=((CompoundTag)raw).copy();tag.remove("Passengers");
            var entity=EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,level.registryAccess(),tag),level,EntitySpawnReason.LOAD,EntityProcessor.NOP);
            if(entity==null)throw new IllegalStateException("Cannot restore captured entity");
            if(level.getEntity(entity.getUUID())!=null)throw new IllegalStateException("Fixture installed twice into one save");
            if(!level.addFreshEntity(entity))throw new IllegalStateException("Cannot register captured entity "+entity.getUUID());
        }
        for(var raw:data.getListOrEmpty("entities"))restorePassengers(level,(CompoundTag)raw);
        return level;
    }
    static void restorePassengers(ServerLevel level,CompoundTag parent){
        var vehicle=level.getEntity(parent.read("UUID",UUIDUtil.CODEC).orElseThrow());
        for(var raw:parent.getListOrEmpty("Passengers")){
            var tag=(CompoundTag)raw;var passenger=level.getEntity(tag.read("UUID",UUIDUtil.CODEC).orElseThrow());
            if(passenger==null){if(tag.getStringOr("id","").equals("minecraft:player"))continue;throw new IllegalStateException("Passenger missing from source capture");}
            if(!passenger.startRiding(vehicle,true,true))throw new IllegalStateException("Cannot restore riding relationship");
        }
    }
    static Map<String,Object> verify(ServerLevel level,CompoundTag data){
        int[] min=data.getIntArray("min").orElseThrow(),size=data.getIntArray("size").orElseThrow(),states=data.getIntArray("states").orElseThrow();
        var palette=palette(level,data);var pos=new BlockPos.MutableBlockPos();int i=0,nonAir=0,trapdoors=0;
        for(int y=0;y<size[1];y++)for(int z=0;z<size[2];z++)for(int x=0;x<size[0];x++){
            pos.set(min[0]+x,min[1]+y,min[2]+z);var expected=palette[states[i++]];
            if(level.getBlockState(pos)!=expected)throw new AssertionError("Restored block state differs at "+pos);
            if(!expected.isAir())nonAir++;
            if(expected.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock)trapdoors++;
        }
        int containers=0;
        for(var raw:data.getListOrEmpty("blockEntities")){
            var tag=(CompoundTag)raw;var at=new BlockPos(tag.getIntOr("x",0),tag.getIntOr("y",0),tag.getIntOr("z",0));var be=level.getBlockEntity(at);
            if(be==null)throw new AssertionError("Missing restored block entity "+at);
            var restored=be.saveWithFullMetadata(level.registryAccess());
            // Check every original field that survives the vanilla serialization schema.
            // Container contents are compared after canonical load/save to include empty slots correctly.
            var canonical=BlockEntity.loadStatic(at,level.getBlockState(at),tag.copy(),level.registryAccess());
            if(!restored.equals(canonical.saveWithFullMetadata(level.registryAccess())))throw new AssertionError("Block entity differs at "+at);
            if(be instanceof net.minecraft.world.Container)containers++;
        }
        int villagers=0,offers=0;
        for(var raw:data.getListOrEmpty("entities")){
            var tag=(CompoundTag)raw;var uuid=tag.read("UUID",UUIDUtil.CODEC).orElseThrow();var entity=level.getEntity(uuid);
            if(entity==null)throw new AssertionError("Missing entity "+uuid);
            if(entity instanceof Villager v){
                villagers++;
                if(tag.contains("Offers")){
                    var expected=net.minecraft.world.item.trading.MerchantOffers.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE),tag.get("Offers")).getOrThrow();
                    var actual=v.getOffers();
                    var ops=level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                    if(!net.minecraft.world.item.trading.MerchantOffers.CODEC.encodeStart(ops,expected).getOrThrow().equals(net.minecraft.world.item.trading.MerchantOffers.CODEC.encodeStart(ops,actual).getOrThrow()))throw new AssertionError("Villager offers differ "+uuid);
                    offers+=actual.size();
                }
            }
        }
        return Map.of("blocksVerified",states.length,"nonAirBlocks",nonAir,"trapdoors",trapdoors,"blockEntitiesVerified",data.getListOrEmpty("blockEntities").size(),"containers",containers,"entitiesVerified",data.getListOrEmpty("entities").size(),"villagers",villagers,"offers",offers);
    }
}
