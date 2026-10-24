package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Actual food use: pause mid-bite, crowded task hotbar, offhand, and threshold 15. */
public final class FoodGameTest implements FabricClientGameTest {
    static void check(boolean value,String msg){if(!value)throw new AssertionError(msg);}
    static void schedule(Robot r){r.queue.add(()->true);r.food.reset();r.state="TEST FOOD";r.running=true;r.stepStart=r.ticks;}
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(s->{var l=s.overworld();for(int x=-9;x<=14;x++)for(int z=-9;z<=14;z++){l.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());for(int y=65;y<=70;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                for(var e:Map.of(new BlockPos(0,65,0),Blocks.CHEST,new BlockPos(2,65,0),Blocks.CRAFTING_TABLE,new BlockPos(4,65,0),Blocks.SMITHING_TABLE,new BlockPos(6,65,0),Blocks.ANVIL,new BlockPos(8,65,0),Blocks.CHEST).entrySet())l.setBlockAndUpdate(e.getKey(),e.getValue().defaultBlockState());
                var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.teleportTo(3.5,65,5.5);p.getInventory().setItem(0,new ItemStack(Items.BREAD,2));p.getInventory().setItem(1,new ItemStack(Items.BOOK));p.getInventory().setSelectedSlot(1);p.getFoodData().setFoodLevel(14);p.getFoodData().setSaturation(0);
            });context.waitTicks(40);
            context.runOnClient(mc->{mc.player.getInventory().setSelectedSlot(1);mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));var r=AutoEquipmentClient.robot;var cfg=r.cfg.root;cfg.addProperty("serverKey","singleplayer");cfg.addProperty("dimension",r.dimension());var b=new JsonObject();b.add("min",WorkConfig.pos(new BlockPos(-9,64,-9)));b.add("max",WorkConfig.pos(new BlockPos(14,70,14)));cfg.add("platformBounds",b);
                for(var e:Map.of("crafting",new BlockPos(2,65,0),"smithing",new BlockPos(4,65,0),"anvils",new BlockPos(6,65,0)).entrySet()){var a=new JsonArray();a.add(WorkConfig.pos(e.getValue()));cfg.getAsJsonObject("workstations").add(e.getKey(),a);}cfg.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(new BlockPos(8,65,0)));cfg.getAsJsonObject("notifications").addProperty("chatEnabled",false);mc.gui.setScreen(null);r.start();check(r.running,"Start failed");r.queue.clear();schedule(r);
            });
            context.waitFor(mc->{var f=AutoEquipmentClient.robot.food.active;return f!=null&&f.started;},100);
            TradingGameTest.chat(world,"pause");context.waitFor(mc->AutoEquipmentClient.robot.manualPaused,100);context.waitTicks(50);
            world.getServer().runOnServer(s->check(s.getPlayerList().getPlayers().getFirst().getInventory().getItem(0).getCount()==2,"Food consumed while paused"));
            TradingGameTest.chat(world,"continue");context.waitFor(mc->AutoEquipmentClient.robot.running,100);done(context);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.getInventory().getItem(0).getCount()==1,"Wrong count after resumed food");check(p.getInventory().getSelectedSlot()==1,"Selected hotbar not restored");
                p.getInventory().setItem(0,ItemStack.EMPTY);p.getInventory().setItem(10,new ItemStack(Items.GOLDEN_CARROT,2));for(int i=2;i<36;i++)if(i!=10)p.getInventory().setItem(i,new ItemStack(Items.DIRT));p.getInventory().setSelectedSlot(2);p.getFoodData().setFoodLevel(14);p.getFoodData().setSaturation(0);
            });context.waitTicks(5);context.runOnClient(mc->{mc.player.getInventory().setSelectedSlot(2);mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(2));var r=AutoEquipmentClient.robot;r.protectedSlots.add(10);schedule(r);});done(context);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.getInventory().getItem(10).is(Items.GOLDEN_CARROT)&&p.getInventory().getItem(10).getCount()==1,"Food slot not restored");for(int i=2;i<9;i++)check(p.getInventory().getItem(i).is(Items.DIRT)&&p.getInventory().getItem(i).getCount()==1,"Task hotbar item changed");for(int i=9;i<36;i++)if(i!=10)check(p.getInventory().getItem(i).is(Items.DIRT)&&p.getInventory().getItem(i).getCount()==1,"Full backpack item changed");check(p.getInventory().getSelectedSlot()==2,"Crowded hotbar selection not restored");p.getInventory().setItem(10,ItemStack.EMPTY);p.getInventory().setItem(40,new ItemStack(Items.GOLDEN_CARROT,2));p.getFoodData().setFoodLevel(15);p.getFoodData().setSaturation(0);});
            context.waitTicks(5);context.runOnClient(mc->schedule(AutoEquipmentClient.robot));done(context);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.getOffhandItem().getCount()==2,"Ate at hunger 15");p.getFoodData().setFoodLevel(14);p.getFoodData().setSaturation(0);});context.waitTicks(5);context.runOnClient(mc->schedule(AutoEquipmentClient.robot));done(context);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.getOffhandItem().getCount()==1&&p.getFoodData().getFoodLevel()>14,"Offhand food failed");for(int i=2;i<9;i++)check(p.getInventory().getItem(i).is(Items.DIRT),"Offhand eating used main hand item");});
            for(double[] pos:List.of(new double[]{4.5,66,3.5},new double[]{4.5,66,3.01},new double[]{4.01,66,3.01})){
                var target=new BlockPos(4,65,3);world.getServer().runOnServer(s->{s.overworld().setBlockAndUpdate(target,Blocks.SHULKER_BOX.defaultBlockState());s.getPlayerList().getPlayers().getFirst().teleportTo(pos[0],pos[1],pos[2]);});context.waitTicks(5);
                context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(r.new Open(target,net.minecraft.world.inventory.ShulkerBoxMenu.class));r.queue.add(()->{r.close();return true;});r.state="TEST SHULKER REOPEN";r.running=true;r.stepStart=r.ticks;});done(context);
                context.runOnClient(mc->{var b=mc.player.getBoundingBox();check(b.maxX<=4||b.minX>=5||b.maxZ<=3||b.minZ>=4,"Player body still overlaps shulker lid");});context.waitTicks(30);
            }
            context.takeScreenshot("v11-food-boundaries");
        }
    }
    static void done(ClientGameTestContext c){c.waitFor(mc->{var r=AutoEquipmentClient.robot;if(!r.running&&!r.state.equals("COMPLETED"))throw new AssertionError("Food stopped: "+r.state);return r.state.equals("COMPLETED");},1200);}
}
