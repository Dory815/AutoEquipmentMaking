package io.github.autoequipmentmaking.client;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.block.Blocks;

/** Exercises pinned MiniHUD/MaLiLib receivers and NBT adapters, not a remote network connection. */
public final class ServuxProtocolGameTest implements FabricClientGameTest {
    UUID villager;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void rejects(Runnable action,String message){boolean rejected=false;try{action.run();}catch(IllegalStateException expected){rejected=true;}check(rejected,message);}
    static CompoundTag handshake(int protocol,String version){var n=new CompoundTag();n.putInt("version",protocol);n.putString("servux",version);return n;}
    @Override public void runTest(ClientGameTestContext context){
        var pos=new BlockPos(0,65,3);
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(server->{var l=server.overworld();l.setBlockAndUpdate(pos,Blocks.SHULKER_BOX.defaultBlockState());var v=EntityTypes.VILLAGER.create(l,EntitySpawnReason.COMMAND);v.setPos(1.5,65,3.5);v.setNoAi(true);l.addFreshEntity(v);villager=v.getUUID();});context.waitTicks(40);
            context.runOnClient(mc->{try{
                var a=new Object();var b=new Object();
                check(ServuxSession.accept(a,handshake(1,"0.11.2")),"Supported handshake rejected");ServuxSession.requireReady(a);
                rejects(()->ServuxSession.requireReady(b),"Old connection handshake reused");
                check(!ServuxSession.accept(a,handshake(2,"0.11.2")),"Wrong wire protocol accepted");rejects(()->ServuxSession.requireReady(a),"Wrong protocol marked ready");
                check(!ServuxSession.accept(a,handshake(1,"0.11.20")),"Version prefix accepted");
                check(ServuxSession.accept(b,handshake(1,"servux-0.11.2")),"Compatible fresh handshake rejected");ServuxSession.requireReady(b);
                var type=Class.forName("fi.dy.masa.minihud.data.EntityDataManager");var manager=type.getMethod("getInstance").invoke(null);var tracker=type.getMethod("getRequestTracker").invoke(manager);
                var v=(Villager)mc.level.getEntity(villager);check(v!=null,"Villager not loaded");int id=v.getId();
                var cache=type.getMethod("getCache").invoke(manager);
                // MaLiLib returns an empty compound on a cache miss, not null.
                cache.getClass().getMethod("removeFromCache",int.class).invoke(cache,id);
                tracker.getClass().getMethod("setPendingLocalEntityRequest",int.class,boolean.class).invoke(tracker,id,false);
                check(RemoteOffers.read(manager,id,villager)==null,"Missing entity cache entry must wait, not fail UUID validation");
                tracker.getClass().getMethod("setPendingLocalEntityRequest",int.class,boolean.class).invoke(tracker,id,true);
                check(RemoteOffers.read(manager,id,villager)==null,"Pending entity accepted");
                var tag=new CompoundTag();tag.put("UUID",UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE,villager).getOrThrow());tag.putString("id","minecraft:villager");
                var offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,10),Optional.of(new ItemCost(Items.BOOK)),new ItemStack(Items.BOW),100,0,0));
                tag.put("Offers",MerchantOffers.CODEC.encodeStart(mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE),offers).getOrThrow());
                var receiver=type.getMethod("handleEntityData",int.class,CompoundTag.class);receiver.invoke(manager,id,tag);
                var read=RemoteOffers.read(manager,id,villager);check(read!=null&&read.size()==1&&read.getFirst().getCostA().getCount()==10,"Real receiver lost offer or cost");
                long expiry=(long)cache.getClass().getMethod("getTimeout").invoke(cache);
                cache.getClass().getMethod("tickCache",long.class).invoke(cache,System.currentTimeMillis()+expiry+1);
                check(RemoteOffers.read(manager,id,villager)==null,"Expired entity response must wait, not fail UUID validation");
                receiver.invoke(manager,id,tag);
                var wrong=tag.copy();wrong.put("UUID",UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE,UUID.randomUUID()).getOrThrow());receiver.invoke(manager,id,wrong);
                boolean rejected=false;try{RemoteOffers.read(manager,id,villager);}catch(IllegalStateException expected){rejected=true;}check(rejected,"Mismatched cached UUID accepted");
                var empty=tag.copy();empty.remove("Offers");receiver.invoke(manager,id,empty);check(RemoteOffers.read(manager,id,villager).isEmpty(),"Uninitialized offers did not allow menu fallback");
                var malformed=tag.copy();malformed.putString("Offers","invalid");receiver.invoke(manager,id,malformed);rejected=false;try{RemoteOffers.read(manager,id,villager);}catch(IllegalStateException expected){rejected=true;}check(rejected,"Malformed offers accepted");
                var missingUuid=tag.copy();missingUuid.remove("UUID");receiver.invoke(manager,id,missingUuid);rejected=false;try{RemoteOffers.read(manager,id,villager);}catch(IllegalStateException expected){rejected=true;}check(rejected,"Present response with missing UUID accepted");
                var box=new CompoundTag();box.putString("id","minecraft:shulker_box");box.putInt("x",pos.getX());box.putInt("y",pos.getY());box.putInt("z",pos.getZ());
                tracker.getClass().getMethod("setPendingLocalBlockEntityRequest",BlockPos.class,boolean.class).invoke(tracker,pos,true);
                check(RemoteInventory.read(manager,pos)==null,"Pending block accepted");type.getMethod("handleBlockEntityData",BlockPos.class,CompoundTag.class).invoke(manager,pos,box);
                check(RemoteInventory.read(manager,pos).isEmpty(),"Empty receiver response not recognized");
                cache.getClass().getMethod("tickCache",long.class).invoke(cache,System.currentTimeMillis()+expiry+1);
                check(RemoteInventory.read(manager,pos)==null,"Expired block response must not become an empty container");
                box.putInt("x",99);rejects(()->RemoteInventory.validateIdentity(box,pos),"Wrong block coordinates accepted");box.putInt("x",0);box.putString("id","minecraft:chest");rejects(()->RemoteInventory.validateIdentity(box,pos),"Wrong block type accepted");
                var invalid=new CompoundTag();invalid.putString("Items","invalid");rejects(()->RemoteInventory.decode(invalid),"Malformed Items accepted as empty");
                var observer=new ServuxObserver();observer.connect();var field=ServuxObserver.class.getDeclaredField("world");field.setAccessible(true);field.set(observer,null);rejects(observer::requireWorld,"Old world observer accepted");
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
        }
    }
}
