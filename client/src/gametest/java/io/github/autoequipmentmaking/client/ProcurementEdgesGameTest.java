package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.util.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Real menu transactions with price changes before commit and two storage good cycles. */
public final class ProcurementEdgesGameTest implements FabricClientGameTest {
    static final BlockPos RAW=new BlockPos(0,65,0),EXTRA=new BlockPos(0,65,3);
    static final Predicate<ItemStack> BOOK=s->Stacks.book(s,"minecraft:mending",1,0);
    UUID villager;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    void price(TestSingleplayerContext world,int cost){
        world.getServer().runOnServer(server->{var v=(Villager)server.overworld().getEntity(villager);var offers=v.getOffers();offers.set(0,new MerchantOffer(new ItemCost(Items.EMERALD,cost),Optional.of(new ItemCost(Items.BOOK)),offers.getFirst().getResult().copy(),1000,0,0));
            for(var p:server.getPlayerList().getPlayers())if(p.containerMenu instanceof MerchantMenu)p.sendMerchantOffers(p.containerMenu.containerId,offers,5,0,false,false);
        });
    }
    void enqueue(ClientGameTestContext c,int required){c.runOnClient(mc->{var r=AutoEquipmentClient.robot;var procurement=new Procurement(r);procurement.needs.clear();procurement.needs.add(new Procurement.Need(BOOK,required,"经验修补测试书"));r.queue.add(r.new Scan());r.queue.add(r.trading.new Discover());r.queue.add(procurement);r.running=true;r.stepStart=r.ticks;});}
    void changeBeforeCommit(ClientGameTestContext c,TestSingleplayerContext world,int cost){
        c.waitFor(mc->{var r=AutoEquipmentClient.robot;check(r.running,"Stopped before price injection: "+r.waitingReason);if(r.queue.peek() instanceof Trading.TradeResult t&&t.click==null){r.manualPause("test price update before output click");return true;}return false;},10000);
        price(world,cost);c.waitFor(mc->mc.player.containerMenu instanceof MerchantMenu m&&m.getOffers().getFirst().getCostA().getCount()==cost,100);
        TradingGameTest.chat(world,"continue");c.waitFor(mc->!AutoEquipmentClient.robot.manualPaused,100);
    }
    @Override public void runTest(ClientGameTestContext c){
        try(var world=c.worldBuilder().create()){
            world.getServer().runOnServer(server->{var l=server.overworld();for(int x=-9;x<=14;x++)for(int z=-9;z<=14;z++){l.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());for(int y=65;y<=70;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                l.setBlockAndUpdate(RAW,Blocks.CHEST.defaultBlockState());l.setBlockAndUpdate(new BlockPos(2,65,0),Blocks.CRAFTING_TABLE.defaultBlockState());l.setBlockAndUpdate(new BlockPos(4,65,0),Blocks.SMITHING_TABLE.defaultBlockState());l.setBlockAndUpdate(new BlockPos(6,65,0),Blocks.ANVIL.defaultBlockState());l.setBlockAndUpdate(new BlockPos(8,65,0),Blocks.CHEST.defaultBlockState());
                var raw=(Container)l.getBlockEntity(RAW);raw.setItem(0,new ItemStack(Items.EMERALD,64));raw.setItem(1,new ItemStack(Items.BOOK,10));
                var book=new ItemStack(Items.ENCHANTED_BOOK);var enchantments=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);enchantments.set(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse("minecraft:mending")).orElseThrow(),1);book.set(DataComponents.STORED_ENCHANTMENTS,enchantments.toImmutable());
                var v=EntityTypes.VILLAGER.create(l,EntitySpawnReason.COMMAND);v.setPos(9.5,65,3.5);v.setNoAi(true);v.setInvulnerable(true);v.setVillagerData(v.getVillagerData().withProfession(l.registryAccess(),VillagerProfession.LIBRARIAN).withLevel(5));var offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,2),Optional.of(new ItemCost(Items.BOOK)),book,1000,0,0));v.setOffers(offers);l.addFreshEntity(v);villager=v.getUUID();
                var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().setItem(0,new ItemStack(Items.EMERALD,7));p.getInventory().setItem(1,new ItemStack(Items.BOOK,3));p.teleportTo(3.5,65,5.5);
            });c.waitTicks(40);
            c.runOnClient(mc->{var r=AutoEquipmentClient.robot;var cfg=r.cfg.root;cfg.addProperty("serverKey","singleplayer");cfg.addProperty("dimension",r.dimension());var bounds=new JsonObject();bounds.add("min",WorkConfig.pos(new BlockPos(-9,64,-9)));bounds.add("max",WorkConfig.pos(new BlockPos(14,70,14)));cfg.add("platformBounds",bounds);
                for(var e:Map.of("crafting",new BlockPos(2,65,0),"smithing",new BlockPos(4,65,0),"anvils",new BlockPos(6,65,0)).entrySet()){var list=new JsonArray();list.add(WorkConfig.pos(e.getValue()));cfg.getAsJsonObject("workstations").add(e.getKey(),list);}cfg.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(new BlockPos(8,65,0)));cfg.getAsJsonObject("trading").addProperty("enabled",true);cfg.getAsJsonObject("trading").addProperty("maxEmeraldsPerTrade",64);cfg.getAsJsonObject("trading").addProperty("maxEmeraldsPerKit",1000);cfg.getAsJsonObject("notifications").addProperty("chatEnabled",true);mc.gui.setScreen(null);r.start();check(r.running,"Robot did not start");r.queue.clear();r.plans.clear();});
            enqueue(c,3);changeBeforeCommit(c,world,5);TradingGameTest.done(c);
            c.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.trading.spent==15&&r.inventoryCount(BOOK)==3,"Price rise duplicated purchases or used stale cost");check(TradingGameTest.count("TRADE REQUOTE")==1,"Price rise was not reconciled");});
            enqueue(c,5);changeBeforeCommit(c,world,3);TradingGameTest.done(c);
            c.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.trading.spent==21&&r.inventoryCount(BOOK)==5,"Price drop failed accounting");r.cfg.root.getAsJsonObject("trading").addProperty("maxEmeraldsPerKit",31);});
            enqueue(c,6);changeBeforeCommit(c,world,20);
            c.waitFor(mc->AutoEquipmentClient.robot.awaitingGood,10000);
            c.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(!r.uncertain&&r.trading.spent==21&&r.inventoryCount(BOOK)==5,"Over-budget price committed");check(mc.player.containerMenu==mc.player.inventoryMenu&&mc.player.containerMenu.getCarried().isEmpty(),"Budget wait kept payment menu open");});
            price(world,1);TradingGameTest.chat(world,"continue");c.waitTicks(20);c.runOnClient(mc->check(AutoEquipmentClient.robot.awaitingGood,"continue bypassed budget good gate"));TradingGameTest.chat(world,"good");c.waitFor(mc->AutoEquipmentClient.robot.restockAcks==1,100);TradingGameTest.done(c);
            c.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.trading.spent==22&&r.inventoryCount(BOOK)==6&&TradingGameTest.count("TRADE BUY CONFIRMED")==6,"Budget recovery duplicated or lost purchases");});
            world.getServer().runOnServer(server->{var raw=(Container)server.overworld().getBlockEntity(RAW);for(int i=0;i<raw.getContainerSize();i++)raw.setItem(i,new ItemStack(Items.DIRT,64));});c.waitTicks(10);
            c.runOnClient(mc->{var r=AutoEquipmentClient.robot;var p=new Procurement(r);p.needs.clear();p.needs.add(new Procurement.Need(BOOK,6,"经验修补测试书"));r.queue.add(new ProcurementStorage(p,p.free()+6,false));r.running=true;r.stepStart=r.ticks;});
            c.waitFor(mc->AutoEquipmentClient.robot.awaitingGood,10000);c.runOnClient(mc->check(TradingGameTest.count("PRETRADE STORED")==0,"Full raw chest overwritten"));
            world.getServer().runOnServer(server->((Container)server.overworld().getBlockEntity(RAW)).setItem(0,ItemStack.EMPTY));TradingGameTest.chat(world,"continue");c.waitTicks(20);c.runOnClient(mc->check(AutoEquipmentClient.robot.awaitingGood,"Storage continue bypassed good"));TradingGameTest.chat(world,"good");
            c.waitFor(mc->AutoEquipmentClient.robot.awaitingGood&&AutoEquipmentClient.robot.restockAcks==2,10000);c.runOnClient(mc->check(TradingGameTest.count("PRETRADE STORED")==1,"Partial storage duplicated write"));
            world.getServer().runOnServer(server->server.overworld().setBlockAndUpdate(EXTRA,Blocks.SHULKER_BOX.defaultBlockState()));c.waitTicks(10);TradingGameTest.chat(world,"good");c.waitFor(mc->AutoEquipmentClient.robot.restockAcks==3,100);TradingGameTest.done(c);
            c.runOnClient(mc->{check(TradingGameTest.count("PRETRADE STORED")==6,"Newly placed storage not scanned or duplicate writes");check(!AutoEquipmentClient.robot.uncertain,"Storage recovery uncertain");});
            world.getServer().runOnServer(server->{var l=server.overworld();var raw=(Container)l.getBlockEntity(RAW);var extra=(Container)l.getBlockEntity(EXTRA);int count=0;for(var storage:List.of(raw,extra))for(int i=0;i<storage.getContainerSize();i++)if(BOOK.test(storage.getItem(i)))count+=storage.getItem(i).getCount();check(count==6,"Stored book conservation failed");for(int i=1;i<27;i++)check(raw.getItem(i).is(Items.DIRT)&&raw.getItem(i).getCount()==64,"Foreign container stock overwritten");var inv=server.getPlayerList().getPlayers().getFirst().getInventory();check(inv.getItem(0).getCount()==7&&inv.getItem(1).getCount()==3,"Protected currency changed");});
        }
    }
}
