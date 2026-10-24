package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Real menus after authoritative source changes; protects private materials and avoids double takes. */
public final class SourceRelocationGameTest implements FabricClientGameTest {
    static final BlockPos A=new BlockPos(0,65,0),B=new BlockPos(0,65,2),D=new BlockPos(4,65,3),DECOY=new BlockPos(5,65,4);
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(server->{var l=server.overworld();for(int x=-9;x<=14;x++)for(int z=-9;z<=14;z++){l.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());for(int y=65;y<=70;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                for(var pos:List.of(A,B,D,new BlockPos(8,65,0)))l.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
                l.setBlockAndUpdate(DECOY,Blocks.DYED_SHULKER_BOX.red().defaultBlockState());((Container)l.getBlockEntity(DECOY)).setItem(0,new ItemStack(Items.DIAMOND,64));
                l.setBlockAndUpdate(new BlockPos(2,65,0),Blocks.CRAFTING_TABLE.defaultBlockState());l.setBlockAndUpdate(new BlockPos(4,65,0),Blocks.SMITHING_TABLE.defaultBlockState());l.setBlockAndUpdate(new BlockPos(6,65,0),Blocks.ANVIL.defaultBlockState());
                var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));p.getFoodData().setFoodLevel(20);p.teleportTo(3.5,65,5.5);
            });context.waitTicks(40);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;var cfg=r.cfg.root;cfg.addProperty("serverKey","singleplayer");cfg.addProperty("dimension",r.dimension());var bounds=new JsonObject();bounds.add("min",WorkConfig.pos(new BlockPos(-9,64,-9)));bounds.add("max",WorkConfig.pos(new BlockPos(14,70,14)));cfg.add("platformBounds",bounds);
                for(var e:Map.of("crafting",new BlockPos(2,65,0),"smithing",new BlockPos(4,65,0),"anvils",new BlockPos(6,65,0)).entrySet()){var list=new JsonArray();list.add(WorkConfig.pos(e.getValue()));cfg.getAsJsonObject("workstations").add(e.getKey(),list);}cfg.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(new BlockPos(8,65,0)));cfg.getAsJsonObject("trading").addProperty("enabled",false);mc.gui.setScreen(null);r.start();check(r.running,"Start failed");r.queue.clear();r.running=false;
            });
            for(int phase=0;phase<3;phase++){
                final int scenario=phase;
                world.getServer().runOnServer(server->{var l=server.overworld();for(var pos:List.of(A,B,D)){if(!l.getBlockState(pos).is(Blocks.CHEST))l.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());((Container)l.getBlockEntity(pos)).clearContent();}
                    ((Container)l.getBlockEntity(A)).setItem(0,new ItemStack(Items.DIAMOND,scenario==1?10:40));if(scenario==1)((Container)l.getBlockEntity(B)).setItem(0,new ItemStack(Items.DIAMOND,30));var p=server.getPlayerList().getPlayers().getFirst();for(int i=1;i<36;i++)p.getInventory().setItem(i,ItemStack.EMPTY);
                });context.waitTicks(20);
                context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(r.new Scan());r.queue.add(r.new Fetch(r.base("minecraft:diamond"),40));r.state="RELOCATION TEST "+scenario;r.running=true;r.stepStart=r.ticks;});
                context.waitFor(mc->{var r=AutoEquipmentClient.robot;check(!r.uncertain,"Unexpected uncertain take");if(r.queue.peek() instanceof SourceFetch step&&step.move==null&&r.inventoryCount(r.base("minecraft:diamond"))==(scenario==1?10:0)){r.manualPause("单人测试移动原料盒");return true;}return false;},2400);
                world.getServer().runOnServer(server->{var l=server.overworld();BlockPos old=scenario==1?B:A;var from=(Container)l.getBlockEntity(old);var stack=from.getItem(0).copy();from.setItem(0,ItemStack.EMPTY);((Container)l.getBlockEntity(D)).setItem(0,stack);if(scenario<2)l.setBlockAndUpdate(old,Blocks.AIR.defaultBlockState());else from.setItem(0,new ItemStack(Items.STONE,40));});
                context.waitTicks(20);context.runOnClient(mc->AutoEquipmentClient.robot.resume());
                context.waitFor(mc->{var r=AutoEquipmentClient.robot;check(!r.uncertain&&!r.awaitingGood,"Relocation should recover without good");return r.state.equals("COMPLETED");},5000);
                context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.inventoryCount(r.base("minecraft:diamond"))==40,"Relocation duplicated or lost task materials");r.record("SOURCE RELOCATION VERIFIED scenario="+scenario+" taskDiamonds=40 privateDiamonds=7");});
                world.getServer().runOnServer(server->{var l=server.overworld();var p=server.getPlayerList().getPlayers().getFirst();check(p.getInventory().getItem(0).getCount()==7,"Private materials changed");check(((Container)l.getBlockEntity(D)).isEmpty(),"Relocated source not exhausted exactly");check(((Container)l.getBlockEntity(DECOY)).getItem(0).getCount()==64,"Colored source was consumed");if(scenario==2)check(((Container)l.getBlockEntity(A)).getItem(0).getCount()==40,"Unrelated replacement items changed");});
            }
            // An unchanged but blocked chest gets one automatic rescan, then the good gate.
            world.getServer().runOnServer(server->{var l=server.overworld();((Container)l.getBlockEntity(A)).clearContent();((Container)l.getBlockEntity(A)).setItem(0,new ItemStack(Items.DIAMOND,40));l.setBlockAndUpdate(A.above(),Blocks.STONE.defaultBlockState());var p=server.getPlayerList().getPlayers().getFirst();for(int i=1;i<36;i++)p.getInventory().setItem(i,ItemStack.EMPTY);});context.waitTicks(20);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(r.new Scan());r.queue.add(r.new Fetch(r.base("minecraft:diamond"),40));r.state="BLOCKED SOURCE TEST";r.running=true;r.stepStart=r.ticks;});
            context.waitFor(mc->{var r=AutoEquipmentClient.robot;check(!r.uncertain,"Read-only open failure became an uncertain take");return r.awaitingGood;},5000);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.waitingReason.contains("重新扫描后仍无法打开"),"Blocked chest failed to stop repeated retries");check(r.inventoryCount(r.base("minecraft:diamond"))==0,"Blocked chest produced items");});
            world.getServer().runOnServer(server->{server.overworld().setBlockAndUpdate(A.above(),Blocks.AIR.defaultBlockState());server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> good"),false);});
            context.waitFor(mc->{var r=AutoEquipmentClient.robot;check(!r.uncertain,"good recovery replayed an uncertain take");return r.state.equals("COMPLETED");},5000);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.restockAcks==1&&r.inventoryCount(r.base("minecraft:diamond"))==40,"Blocked source recovery lost or duplicated items");r.record("SOURCE BLOCKED VERIFIED one automatic retry then good; taskDiamonds=40 privateDiamonds=7");r.record("COMPLETED");});
        }
    }
}
