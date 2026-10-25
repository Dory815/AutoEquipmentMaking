package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.minecraft.core.*;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.*;

/** Replays the user's three failures against original minecart villagers in a baseline clone. */
public final class VillagerNavigationGameTest implements FabricClientGameTest {
    final Map<String,Object> result=new LinkedHashMap<>();
    final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> trapdoors=new HashMap<>();
    final List<Map<String,Object>> cases=new ArrayList<>();
    UUID villager;int uses;
    boolean before=Boolean.parseBoolean(System.getenv().getOrDefault("AEM_MERCHANT_BEFORE","false"));
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    @Override public void runTest(ClientGameTestContext context){
        Path save=Path.of(Objects.requireNonNull(System.getenv("AEM_HALL_WORLD"))).toAbsolutePath();check(save.toString().contains("clientGameTest")&&!save.toString().contains("test-baselines"),"Refuse baseline");
        var targets=List.of(new BlockPos(1083,56,18),new BlockPos(1074,56,12),new BlockPos(1062,56,9));
        var starts=List.of(new Vec3(1068.4994878029083,57,22.99969185871762),new Vec3(1068.300000011921,57,23.895649468482418),new Vec3(1069.4239513427385,57.875,23.299915325797702));
        result.put("expectedBeforeFailure",before);result.put("complete",false);result.put("cases",cases);
        try(var world=new TestWorldSaveImpl(context,save).open()){
            world.getServer().runOnServer(server->{var l=server.getLevel(Level.END);for(int x=65;x<=69;x++)for(int z=0;z<=2;z++)l.setChunkForced(x,z,true);var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getFoodData().setFoodLevel(20);p.teleportTo(l,1068.5,57,21.5,Set.of(),0,0,true);for(var b:BlockPos.betweenClosed(TradingHallNavigationGameTest.MIN,TradingHallNavigationGameTest.MAX))if(l.getBlockState(b).getBlock() instanceof TrapDoorBlock)trapdoors.put(b.immutable(),l.getBlockState(b));});
            context.waitFor(mc->mc.level!=null&&mc.level.dimension()==Level.END,400);context.waitTicks(100);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;var c=r.cfg.root;c.addProperty("serverKey","singleplayer");c.addProperty("dimension",r.dimension());var b=new JsonObject();b.add("min",WorkConfig.pos(new BlockPos(1054,56,8)));b.add("max",WorkConfig.pos(new BlockPos(1095,62,36)));c.add("platformBounds",b);c.getAsJsonObject("notifications").addProperty("chatEnabled",false);mc.gui.setScreen(null);});
            for(int i=0;i<(before?1:targets.size());i++){
                var target=targets.get(i);var start=starts.get(i);var details=new LinkedHashMap<String,Object>();cases.add(details);details.put("target",target.toShortString());details.put("start",start.toString());
                world.getServer().runOnServer(server->{var l=server.getLevel(Level.END);var matches=l.getEntitiesOfClass(Villager.class,new AABB(target).inflate(1),v->v.blockPosition().equals(target));check(matches.size()==1,"Expected one original villager at "+target+", got "+matches.size());var v=matches.getFirst();villager=v.getUUID();uses=v.getOffers().stream().mapToInt(o->o.getUses()).sum();details.put("uuid",villager.toString());details.put("eye",v.getEyePosition().toString());details.put("passenger",v.isPassenger());check(v.isPassenger(),"Original minecart relation missing");server.getPlayerList().getPlayers().getFirst().teleportTo(l,start.x,start.y,start.z,Set.of(),0,0,true);});
                context.waitTicks(5);
                context.runOnClient(mc->{
                    var r=AutoEquipmentClient.robot;var v=(Villager)mc.level.getEntity(villager);var nav=new PlayerNavigation(r,v);var counts=new TreeMap<String,Integer>();var visible=new ArrayList<String>();var nearby=new TreeMap<String,String>();details.put("bbox",v.getBoundingBox().toString());
                    for(int x=(target.getX()-4)*2;x<=(target.getX()+4)*2;x++)for(int z=(target.getZ()-4)*2;z<=(target.getZ()+4)*2;z++)for(var p:nav.terrain.column(x,z)){
                        if(!nav.terrain.clear(p)||!nav.terrain.supported(p))continue;counts.merge("clearStanding",1,Integer::sum);
                        if(nav.reached(p,3.2)){counts.merge("reached",1,Integer::sum);if(visible.size()<12)visible.add(p.toString());}if(nav.goalVisible(p))counts.merge("robustGoal",1,Integer::sum);
                        var eye=p.add(0,mc.player.getEyeHeight(),0);var aim=v.getBoundingBox().getCenter();if(eye.distanceTo(aim)<3.2){var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(eye,aim,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));counts.merge("bodyRay:"+(hit.getType()==HitResult.Type.MISS?"MISS":mc.level.getBlockState(hit.getBlockPos()).toString()+" at "+hit.getBlockPos().toShortString()),1,Integer::sum);}
                    }
                    for(var p:BlockPos.betweenClosed(target.offset(-1,0,-1),target.offset(1,3,1)))nearby.put(p.toShortString(),mc.level.getBlockState(p).toString());details.put("poseCounts",counts);details.put("visiblePoses",visible);details.put("nearbyBlocks",nearby);
                });
                context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.queue.isEmpty(),"Previous probe queue retained");r.task=UUID.randomUUID().toString();r.server=r.server();r.dimension=r.dimension();r.state="MERCHANT NAV PROBE";r.running=true;r.uncertain=false;r.stepStart=r.ticks;r.queue.add(r.trading.new OpenMerchant(villager));r.queue.add(()->{check(mc.player.containerMenu instanceof MerchantMenu,"Merchant navigation did not open actual menu");r.record("MERCHANT PROBE OPENED "+villager);return true;});});
                context.waitFor(mc->!AutoEquipmentClient.robot.running,15000);
                context.runOnClient(mc->{var r=AutoEquipmentClient.robot;details.put("state",r.state);details.put("arrival",mc.player.position().toString());if(before){check(r.state.equals("RECOVERY_REQUIRED"),"Expected original failure");details.put("failureReproduced",true);r.queue.clear();}else{check(r.state.equals("COMPLETED"),"Original villager probe failed: "+target+" state="+r.state);details.put("actualMerchantMenuOpened",mc.player.containerMenu instanceof MerchantMenu);check(mc.player.containerMenu instanceof MerchantMenu,"Menu missing");}r.close();r.release();});
                world.getServer().runOnServer(server->{var v=(Villager)server.getLevel(Level.END).getEntity(villager);check(v.isPassenger(),"Passenger relation changed");check(v.getOffers().stream().mapToInt(o->o.getUses()).sum()==uses,"Read-only merchant probe changed trade uses");});
            }
            world.getServer().runOnServer(server->{var l=server.getLevel(Level.END);for(var e:trapdoors.entrySet())check(l.getBlockState(e.getKey())==e.getValue(),"Trapdoor changed");});result.put("trapdoorsUnchanged",trapdoors.size());result.put("complete",true);context.takeScreenshot("merchant-navigation-"+(before?"before":"after"));
        }finally{try{Files.writeString(Path.of("merchant-navigation-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new IllegalStateException(e);}}
    }
}
