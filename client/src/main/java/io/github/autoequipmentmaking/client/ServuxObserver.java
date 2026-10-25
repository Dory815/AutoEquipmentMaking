package io.github.autoequipmentmaking.client;

import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

/** Reuses MiniHUD's receiver; never registers another Servux channel. */
final class ServuxObserver {
    private final java.util.Map<BlockPos,java.util.concurrent.CompletableFuture<Container>> local=new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID,java.util.concurrent.CompletableFuture<net.minecraft.world.item.trading.MerchantOffers>> localOffers=new java.util.HashMap<>();
    private Object manager;
    private net.minecraft.client.multiplayer.ClientLevel world;
    private Object connection;
    void reset() {
        local.values().forEach(f->f.cancel(false));localOffers.values().forEach(f->f.cancel(false));
        local.clear();localOffers.clear();manager=null;world=null;connection=null;
    }
    void connect() {
        var mc=Minecraft.getInstance();reset();world=mc.level;connection=mc.getConnection();
        if(mc.hasSingleplayerServer())return;
        try {
            Class<?> type=Class.forName("fi.dy.masa.minihud.data.EntityDataManager");
            manager=type.getMethod("getInstance").invoke(null);
            int protocol=Class.forName("fi.dy.masa.minihud.network.ServuxEntitiesPacket").getField("PROTOCOL_VERSION").getInt(null);
            if(protocol!=1)throw new IllegalStateException("Servux 0.11.2 使用协议 1；请安装兼容的 MiniHUD 0.40.4（当前协议 "+protocol+"）");
            if(!(boolean)type.getMethod("isEnabled").invoke(manager))throw new IllegalStateException("请开启 MiniHUD entityDataSync");
            try{ServuxSession.requireReady(connection);}catch(IllegalStateException pending){type.getMethod("requestMetadata").invoke(manager);throw pending;}
            requireRemote();
            // Verify the APIs actually used, including private request methods, before starting any work.
            var cache=type.getMethod("getCache").invoke(manager).getClass();var tracker=type.getMethod("getRequestTracker").invoke(manager).getClass();
            for(var key:java.util.List.of(BlockPos.class,int.class)){
                cache.getMethod("removeFromCache",key);
                String kind=key==int.class?"Entity":"BlockEntity";
                cache.getMethod("get"+kind+"NbtFromCache",key);
                cache.getMethod("get"+kind+"DataEntryFromCache",key);
                tracker.getMethod("setPendingLocal"+kind+"Request",key,boolean.class);
                tracker.getMethod("hasPendingLocal"+kind,key);
                type.getDeclaredMethod("requestServux"+kind+"Data",key).setAccessible(true);
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException("需要兼容的 MiniHUD 0.40.4 与 MaLiLib 0.29.3",e);}
    }
    void requireWorld(){var mc=Minecraft.getInstance();if(world!=mc.level||connection!=mc.getConnection())throw new IllegalStateException("观察连接或世界改变，请重新开始扫描");}
    void requireRemote()throws ReflectiveOperationException{
        requireWorld();ServuxSession.requireReady(connection);
        if(manager==null||!(boolean)manager.getClass().getMethod("isEnabled").invoke(manager)||!(boolean)manager.getClass().getMethod("hasServuxServer").invoke(manager))throw new IllegalStateException("Servux 同步连接失效，请检查 MiniHUD entityDataSync");
    }
    void refresh(BlockPos p) {
        var mc=Minecraft.getInstance();requireWorld();
        if(mc.hasSingleplayerServer()){
            var server=mc.getSingleplayerServer();var dimension=mc.level.dimension();
            local.put(p,server.submit(()->{var level=server.getLevel(dimension);var be=level.getBlockEntity(p);if(!(be instanceof Container source))throw new IllegalStateException("容器消失");
                if(be instanceof net.minecraft.world.RandomizableContainer random&&random.getLootTable()!=null)throw new IllegalStateException("未开启的战利品容器不作为原料");
                var state=level.getBlockState(p);if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest){var combined=net.minecraft.world.level.block.ChestBlock.getContainer(chest,state,level,p,true);if(combined!=null)source=combined;}
                var snapshot=new net.minecraft.world.SimpleContainer(source.getContainerSize());for(int i=0;i<source.getContainerSize();i++)snapshot.setItem(i,source.getItem(i).copy());return (Container)snapshot;}));return;
        }
        try {requireRemote();RemoteInventory.refresh(manager,p);
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Servux 请求失败",e);}
    }
    Container cached(BlockPos p) {
        var mc=Minecraft.getInstance();requireWorld();
        if(mc.hasSingleplayerServer()){
            var pending=local.get(p);if(pending==null||!pending.isDone())return null;
            return pending.join();
        }
        try {
            requireRemote();
            Container snapshot=RemoteInventory.read(manager,p);
            if(snapshot==null)RemoteInventory.refreshMissing(manager,p);
            return snapshot;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Servux 库存读取失败",e);}
    }
    void refreshOffers(net.minecraft.world.entity.npc.villager.Villager villager){
        var mc=Minecraft.getInstance();requireWorld();
        if(mc.hasSingleplayerServer()){
            var dimension=mc.level.dimension();var uuid=villager.getUUID();
            localOffers.put(uuid,mc.getSingleplayerServer().submit(()->{
                var entity=mc.getSingleplayerServer().getLevel(dimension).getEntity(uuid);
                if(!(entity instanceof net.minecraft.world.entity.npc.villager.Villager v))throw new IllegalStateException("村民消失");
                return v.getOffers().copy();
            }));return;
        }
        try{requireRemote();RemoteOffers.refresh(manager,villager.getId());}catch(ReflectiveOperationException e){throw new IllegalStateException("Servux 村民请求失败",e);}
    }
    net.minecraft.world.item.trading.MerchantOffers cachedOffers(net.minecraft.world.entity.npc.villager.Villager villager){
        requireWorld();
        if(Minecraft.getInstance().hasSingleplayerServer()){
            var pending=localOffers.get(villager.getUUID());return pending==null||!pending.isDone()?null:pending.join();
        }
        try{requireRemote();var offers=RemoteOffers.read(manager,villager.getId(),villager.getUUID());if(offers==null)RemoteOffers.refreshMissing(manager,villager.getId());return offers;}catch(ReflectiveOperationException e){throw new IllegalStateException("Servux 村民报价读取失败",e);}
    }
}
