package io.github.autoequipmentmaking.client;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffers;

/** Authoritative Servux observations; purchases must still validate the live merchant menu. */
final class RemoteOffers {
    static void refresh(Object manager,int entityId)throws ReflectiveOperationException{
        Object cache=manager.getClass().getMethod("getCache").invoke(manager);
        Object tracker=manager.getClass().getMethod("getRequestTracker").invoke(manager);
        cache.getClass().getMethod("removeFromCache",int.class).invoke(cache,entityId);
        tracker.getClass().getMethod("setPendingLocalEntityRequest",int.class,boolean.class).invoke(tracker,entityId,true);
        Method request=manager.getClass().getDeclaredMethod("requestServuxEntityData",int.class);request.setAccessible(true);request.invoke(manager,entityId);
    }
    static MerchantOffers read(Object manager,int entityId,UUID expected)throws ReflectiveOperationException{
        var mc=Minecraft.getInstance();var entity=mc.level.getEntity(entityId);
        if(!(entity instanceof Villager)||!entity.getUUID().equals(expected))throw new IllegalStateException("村民实体改变，停止读取报价");
        Object cache=manager.getClass().getMethod("getCache").invoke(manager),tracker=manager.getClass().getMethod("getRequestTracker").invoke(manager);
        if((boolean)tracker.getClass().getMethod("hasPendingLocalEntity",int.class).invoke(tracker,entityId))return null;
        CompoundTag nbt=(CompoundTag)cache.getClass().getMethod("getEntityNbtFromCache",int.class).invoke(cache,entityId);
        if(nbt==null)return null;
        if(!nbt.read("UUID",UUIDUtil.CODEC).orElseThrow(()->new IllegalStateException("Servux 村民回复缺少有效 UUID")).equals(expected))throw new IllegalStateException("Servux 村民 UUID 与请求不一致");
        if(nbt.contains("id")&&!nbt.getStringOr("id","").equals("minecraft:villager"))throw new IllegalStateException("Servux 村民类型与请求不一致");
        var ops=mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        // An uninitialized villager may not have Offers yet; the caller then opens its menu once.
        if(!nbt.contains("Offers"))return new MerchantOffers();
        return MerchantOffers.CODEC.parse(ops,nbt.getCompound("Offers").orElseThrow(()->new IllegalStateException("Servux 报价数据格式无效"))).getOrThrow();
    }
}
