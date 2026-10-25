package io.github.autoequipmentmaking.client;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Reads authoritative NBT with fixed container capacity, including empty shulkers. */
final class RemoteInventory {
    static BlockPos partner(BlockPos p){
        var level=Minecraft.getInstance().level;var state=level.getBlockState(p);
        if(state.getBlock() instanceof ChestBlock&&state.getValue(ChestBlock.TYPE)!=ChestType.SINGLE){
            BlockPos other=p.relative(ChestBlock.getConnectedDirection(state));var adjacent=level.getBlockState(other);
            if(adjacent.getBlock()!=state.getBlock()||adjacent.getValue(ChestBlock.TYPE)==ChestType.SINGLE||adjacent.getValue(ChestBlock.TYPE)==state.getValue(ChestBlock.TYPE)||adjacent.getValue(ChestBlock.FACING)!=state.getValue(ChestBlock.FACING)||!other.relative(ChestBlock.getConnectedDirection(adjacent)).equals(p))throw new IllegalStateException("双箱另一半未确认: "+p);
            return other;
        }return null;
    }
    static void refresh(Object manager,BlockPos p)throws ReflectiveOperationException{
        Object cache=manager.getClass().getMethod("getCache").invoke(manager);
        Object tracker=manager.getClass().getMethod("getRequestTracker").invoke(manager);
        Method remove=cache.getClass().getMethod("removeFromCache",BlockPos.class),pending=tracker.getClass().getMethod("setPendingLocalBlockEntityRequest",BlockPos.class,boolean.class);
        // requestBlockEntity() may immediately populate the cache from the client world.
        // This flag is cleared by handleBlockEntityData() only after an authoritative reply.
        Method request=manager.getClass().getDeclaredMethod("requestServuxBlockEntityData",BlockPos.class);request.setAccessible(true);
        BlockPos other=partner(p);for(BlockPos q:other==null?List.of(p):List.of(p,other)){remove.invoke(cache,q);pending.invoke(tracker,q,true);request.invoke(manager,q);}
    }
    static Container read(Object manager,BlockPos p)throws ReflectiveOperationException{
        var mc=Minecraft.getInstance();Object cache=manager.getClass().getMethod("getCache").invoke(manager),tracker=manager.getClass().getMethod("getRequestTracker").invoke(manager);
        Method pending=tracker.getClass().getMethod("hasPendingLocalBlockEntity",BlockPos.class);BlockPos other=partner(p);
        if((boolean)pending.invoke(tracker,p)||(other!=null&&(boolean)pending.invoke(tracker,other)))return null;
        Method entry=cache.getClass().getMethod("getBlockEntityDataEntryFromCache",BlockPos.class);
        if(entry.invoke(cache,p)==null||(other!=null&&entry.invoke(cache,other)==null))return null;
        Method nbt=cache.getClass().getMethod("getBlockEntityNbtFromCache",BlockPos.class);
        CompoundTag own=(CompoundTag)nbt.invoke(cache,p);if(own==null)return null;
        validateIdentity(own,p);Container first=decode(own);if(other==null)return first;
        CompoundTag paired=(CompoundTag)nbt.invoke(cache,other);if(paired==null)return null;
        validateIdentity(paired,other);Container second=decode(paired);
        return mc.level.getBlockState(p).getValue(ChestBlock.TYPE)==ChestType.RIGHT?new CompoundContainer(first,second):new CompoundContainer(second,first);
    }
    static void refreshMissing(Object manager,BlockPos p)throws ReflectiveOperationException{
        Object cache=manager.getClass().getMethod("getCache").invoke(manager),tracker=manager.getClass().getMethod("getRequestTracker").invoke(manager);
        Method pending=tracker.getClass().getMethod("hasPendingLocalBlockEntity",BlockPos.class),entry=cache.getClass().getMethod("getBlockEntityDataEntryFromCache",BlockPos.class);
        BlockPos other=partner(p);
        if(!(boolean)pending.invoke(tracker,p)&&(other==null||!(boolean)pending.invoke(tracker,other))
            &&(entry.invoke(cache,p)==null||(other!=null&&entry.invoke(cache,other)==null)))refresh(manager,p);
    }
    static void validateIdentity(CompoundTag nbt,BlockPos p){
        for(var entry:Map.of("x",p.getX(),"y",p.getY(),"z",p.getZ()).entrySet())if(nbt.contains(entry.getKey())&&nbt.getIntOr(entry.getKey(),Integer.MIN_VALUE)!=entry.getValue())throw new IllegalStateException("Servux 容器坐标与请求不一致: "+p);
        var be=Minecraft.getInstance().level.getBlockEntity(p);
        if(be==null)throw new IllegalStateException("Servux 容器位置已改变: "+p);
        if(nbt.contains("id")&&!nbt.getStringOr("id","").equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString()))throw new IllegalStateException("Servux 容器类型与请求不一致: "+p);
    }
    static Container decode(CompoundTag nbt){
        if(nbt.contains("LootTable"))throw new IllegalStateException("未开启的战利品容器不作为原料");
        var result=new SimpleContainer(27);var seen=new HashSet<Integer>();
        var ops=Minecraft.getInstance().level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var items=nbt.contains("Items")?nbt.getList("Items").orElseThrow(()->new IllegalStateException("Servux 物品列表格式无效")):new ListTag();
        for(Tag row:items){
            ItemStackWithSlot entry=ItemStackWithSlot.CODEC.parse(ops,row).getOrThrow();
            if(!entry.isValidInContainer(27)||!seen.add(entry.slot()))throw new IllegalStateException("容器槽位数据无效");
            result.setItem(entry.slot(),entry.stack().copy());
        }return result;
    }
}
