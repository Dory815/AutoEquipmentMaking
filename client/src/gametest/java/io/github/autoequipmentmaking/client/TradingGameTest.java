package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Real-time cooldown, real menus, independent shortage/food/control assertions. */
public final class TradingGameTest implements FabricClientGameTest {
    static final BlockPos RAW=new BlockPos(0,65,0);UUID villager;
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);}
    static List<String> events(){try{var r=AutoEquipmentClient.robot;return Files.readAllLines(r.journal).stream().map(l->JsonParser.parseString(l).getAsJsonObject()).filter(o->o.get("task").getAsString().equals(r.task)).map(o->o.get("event").getAsString()).toList();}catch(Exception e){throw new IllegalStateException(e);}}
    static long count(String prefix){return events().stream().filter(e->e.startsWith(prefix)).count();}
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(server->{var l=server.overworld();for(int x=-9;x<=14;x++)for(int z=-9;z<=14;z++){l.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());for(int y=65;y<=70;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                l.setBlockAndUpdate(RAW,Blocks.CHEST.defaultBlockState());l.setBlockAndUpdate(new BlockPos(2,65,0),Blocks.CRAFTING_TABLE.defaultBlockState());l.setBlockAndUpdate(new BlockPos(4,65,0),Blocks.SMITHING_TABLE.defaultBlockState());l.setBlockAndUpdate(new BlockPos(6,65,0),Blocks.ANVIL.defaultBlockState());l.setBlockAndUpdate(new BlockPos(8,65,0),Blocks.CHEST.defaultBlockState());
                var book=new ItemStack(Items.ENCHANTED_BOOK);var ench=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);ench.set(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse("minecraft:mending")).orElseThrow(),1);book.set(DataComponents.STORED_ENCHANTMENTS,ench.toImmutable());
                var prior=book.copy();prior.set(DataComponents.REPAIR_COST,3);((Container)l.getBlockEntity(RAW)).setItem(2,prior);
                var soul=new ItemStack(Items.ENCHANTED_BOOK);var soulEnch=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);soulEnch.set(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse("minecraft:soul_speed")).orElseThrow(),2);soul.set(DataComponents.STORED_ENCHANTMENTS,soulEnch.toImmutable());
                var v=EntityTypes.VILLAGER.create(l,EntitySpawnReason.COMMAND);v.setPos(9.5,65,3.5);v.setNoAi(true);v.setInvulnerable(true);v.setVillagerData(v.getVillagerData().withProfession(l.registryAccess(),VillagerProfession.LIBRARIAN).withLevel(5));var offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,2),Optional.of(new ItemCost(Items.BOOK)),book,1,0,0));offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,64),Optional.of(new ItemCost(Items.BOOK)),soul,100,0,0));v.setOffers(offers);l.addFreshEntity(v);villager=v.getUUID();
                var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().setItem(0,new ItemStack(Items.EMERALD,7));p.getInventory().setItem(1,new ItemStack(Items.BOOK,3));p.getFoodData().setFoodLevel(14);p.getFoodData().setSaturation(0);p.teleportTo(3.5,65,5.5);
            });context.waitTicks(40);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;var cfg=r.cfg.root;cfg.addProperty("serverKey","singleplayer");cfg.addProperty("dimension",r.dimension());var bounds=new JsonObject();bounds.add("min",WorkConfig.pos(new BlockPos(-9,64,-9)));bounds.add("max",WorkConfig.pos(new BlockPos(14,70,14)));cfg.add("platformBounds",bounds);
                for(var e:Map.of("crafting",new BlockPos(2,65,0),"smithing",new BlockPos(4,65,0),"anvils",new BlockPos(6,65,0)).entrySet()){var a=new JsonArray();a.add(WorkConfig.pos(e.getValue()));cfg.getAsJsonObject("workstations").add(e.getKey(),a);}cfg.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(new BlockPos(8,65,0)));cfg.getAsJsonObject("trading").addProperty("enabled",true);cfg.getAsJsonObject("notifications").addProperty("chatEnabled",true);mc.gui.setScreen(null);r.start();check(r.running,"Robot did not start");r.queue.clear();r.queue.add(r.new Scan());r.queue.add(r.trading.new Discover());r.queue.add(()->{check(r.trading.planningBooks(Map.of("minecraft:mending@1",1)).isEmpty(),"Market displaced sufficient physical books");var lower=r.trading.planningBooks(Map.of("minecraft:soul_speed@3",1));check(lower.size()==2&&lower.stream().allMatch(b->Stacks.book(b,"minecraft:soul_speed",2,0)),"Lower trade books cannot supply a level III plan");return true;});r.queue.add(r.trading.new Acquire(s->Stacks.book(s,"minecraft:mending",1,0),1));
            });
            context.waitFor(mc->AutoEquipmentClient.robot.awaitingGood,2400);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.waitingReason.contains("绿宝石缺货"),"Wrong currency diagnosis");check(!r.uncertain,"Known stockout marked uncertain");check(count("FOOD MISSING CONTINUE")==1,"Missing food stopped or spammed");});
            chat(world,"continue");context.waitTicks(20);context.runOnClient(mc->check(AutoEquipmentClient.robot.awaitingGood&&!AutoEquipmentClient.robot.running,"continue bypassed good"));
            world.getServer().runOnServer(server->((Container)server.overworld().getBlockEntity(RAW)).setItem(0,new ItemStack(Items.EMERALD_BLOCK)));
            chat(world,"goodbye");context.waitTicks(20);context.runOnClient(mc->check(AutoEquipmentClient.robot.awaitingGood,"goodbye resumed stockout"));chat(world,"good");
            context.waitFor(mc->AutoEquipmentClient.robot.awaitingGood&&AutoEquipmentClient.robot.restockAcks==1,2400);context.runOnClient(mc->check(AutoEquipmentClient.robot.waitingReason.contains("普通书缺货"),"Missing book ignored"));
            chat(world,"pause");context.waitFor(mc->AutoEquipmentClient.robot.manualPaused,100);
            world.getServer().runOnServer(server->((Container)server.overworld().getBlockEntity(RAW)).setItem(1,new ItemStack(Items.BOOK,2)));chat(world,"good");context.waitFor(mc->AutoEquipmentClient.robot.restockAcks==2,100);context.runOnClient(mc->check(!AutoEquipmentClient.robot.running&&AutoEquipmentClient.robot.manualPaused&&!AutoEquipmentClient.robot.awaitingGood,"good cleared manual pause"));chat(world,"continue");context.waitFor(mc->AutoEquipmentClient.robot.running,100);done(context);
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(p.getInventory().getItem(0).getCount()==7&&p.getInventory().getItem(1).getCount()==3,"Personal currency spent");var v=(Villager)server.overworld().getEntity(villager);check(v.getOffers().getFirst().getUses()==1,"Initial purchase not confirmed");});
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(r.trading.new Acquire(s->Stacks.book(s,"minecraft:mending",1,0),2));r.state="TRADING";r.running=true;r.stepStart=r.ticks;});
            int[] polls={0};context.waitFor(mc->{check(AutoEquipmentClient.robot.running,"Cooldown stopped task");return ++polls[0]%20==0&&count("TRADE COOLDOWN CHECK ")>=3;},100000);
            context.runOnClient(mc->{check(count("NOTICE 村民进入补货冷却：")==1,"Cooldown warning was not exactly once");check(count("NOTICE 村民补货完成")==0,"Premature restock notification");});
            world.getServer().runOnServer(server->((Villager)server.overworld().getEntity(villager)).getOffers().getFirst().resetUses());done(context);
            context.runOnClient(mc->{check(count("NOTICE 村民补货完成")==1,"Restock recovery not announced");check(count("TRADE BUY CONFIRMED")==2,"Wrong total purchases");check(count("FOOD MISSING CONTINUE")==1,"No-food warning repeated");});
            world.getServer().runOnServer(server->{var p=server.getPlayerList().getPlayers().getFirst();check(p.getInventory().getItem(0).getCount()==7&&p.getInventory().getItem(1).getCount()==3,"Protected currency changed after repeated trades");});
            world.getServer().runOnServer(server->{var raw=(Container)server.overworld().getBlockEntity(RAW);raw.setItem(0,new ItemStack(Items.EMERALD_BLOCK,14));raw.setItem(1,new ItemStack(Items.BOOK,2));});
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(r.trading.new Acquire(s->Stacks.book(s,"minecraft:soul_speed",2,0),2));r.state="TRADING";r.running=true;r.stepStart=r.ticks;});done(context);
            world.getServer().runOnServer(server->{var v=(Villager)server.overworld().getEntity(villager);check(v.getOffers().get(1).getUses()==2,"Custom low-level book trades failed");check(((Container)server.overworld().getBlockEntity(RAW)).getItem(0).isEmpty()&&((Container)server.overworld().getBlockEntity(RAW)).getItem(1).isEmpty(),"High-price block funding not consumed exactly");check(Stacks.book(((Container)server.overworld().getBlockEntity(RAW)).getItem(2),"minecraft:mending",1,3),"Prior-penalty source book changed");var inv=server.getPlayerList().getPlayers().getFirst().getInventory();check(inv.getItem(0).is(Items.EMERALD)&&inv.getItem(0).getCount()==7&&inv.getItem(1).is(Items.BOOK)&&inv.getItem(1).getCount()==3,"High-price trade consumed protected currency");int remaining=0;for(int i=2;i<36;i++)if(inv.getItem(i).is(Items.EMERALD))remaining+=inv.getItem(i).getCount();check(remaining==3,"Emerald block/trade accounting mismatch: "+remaining);});
            // Fund three purchases together. Excess payment stays in the menu until the last purchase.
            long[] selections={0},sessions={0};
            context.runOnClient(mc->{selections[0]=count("TRADE SELECT INTENT");sessions[0]=count("TRADE SESSION COMPLETE");});
            world.getServer().runOnServer(server->{
                var l=server.overworld();var v=(Villager)l.getEntity(villager);var result=v.getOffers().getFirst().getResult().copy();
                v.getOffers().set(0,new MerchantOffer(new ItemCost(Items.EMERALD,2),Optional.of(new ItemCost(Items.BOOK)),result,100,0,0));
                var raw=(Container)l.getBlockEntity(RAW);raw.setItem(0,new ItemStack(Items.EMERALD_BLOCK));raw.setItem(1,new ItemStack(Items.BOOK,3));
            });
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(r.new Scan());r.queue.add(r.trading.new Currency("minecraft:emerald",6));r.queue.add(r.trading.new Currency("minecraft:book",3));r.queue.add(r.trading.new Acquire(s->Stacks.book(s,"minecraft:mending",1,0),5));r.state="TRADING";r.running=true;r.stepStart=r.ticks;});done(context);
            context.runOnClient(mc->{check(count("TRADE SELECT INTENT")==selections[0]+1,"Repeated selection during a funded three-book purchase");check(count("TRADE SESSION COMPLETE")==sessions[0]+1,"Repeated merchant sessions during funded purchases");check(count("TRADE BUY CONFIRMED")==7,"Bulk purchase exceeded or missed its target");});
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst();var inv=p.getInventory();
                check(((Villager)server.overworld().getEntity(villager)).getOffers().getFirst().getUses()==3,"Wrong bulk offer uses");
                check(inv.getItem(0).getCount()==7&&inv.getItem(1).getCount()==3,"Bulk leftovers merged into private currency");
                int remaining=0;for(int i=2;i<36;i++)if(inv.getItem(i).is(Items.EMERALD))remaining+=inv.getItem(i).getCount();check(remaining==6,"Bulk leftover payment was lost or overspent");
                check(p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty(),"Bulk purchase left a menu or cursor open");
            });
            // A queued payment must never act on inventory slots if the merchant menu was closed.
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(r.trading.new OpenMerchant(villager));r.state="TRADING";r.running=true;r.stepStart=r.ticks;});done(context);
            context.runOnClient(mc->{
                var r=AutoEquipmentClient.robot;var guardSteps=List.<Robot.Step>of(r.trading.new ReturnPayment(0),r.trading.new FillPayment(0,new ItemStack(Items.EMERALD)),r.trading.new PaymentMove(r.menuSlot(0),0,1,new ItemStack(Items.EMERALD)));
                long clicks=count("CLICK INTENT");r.close();
                for(var step:guardSteps){boolean rejected=false;try{step.tick();}catch(IllegalStateException expected){rejected=expected.getMessage().contains("菜单身份改变");}check(rejected,"Payment step accepted a closed merchant menu");}
                check(count("CLICK INTENT")==clicks,"Rejected payment still sent a click");
            });
            context.takeScreenshot("v11-trade-cooldown-recovered");
        }
    }
    static void chat(TestSingleplayerContext w,String word){w.getServer().runOnServer(s->s.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> "+word),false));}
    static void done(ClientGameTestContext c){c.waitFor(mc->{var r=AutoEquipmentClient.robot;if(!r.running&&!r.state.equals("COMPLETED"))throw new AssertionError("Trade task stopped: "+r.state+" "+r.waitingReason);return r.state.equals("COMPLETED");},100000);}
}
