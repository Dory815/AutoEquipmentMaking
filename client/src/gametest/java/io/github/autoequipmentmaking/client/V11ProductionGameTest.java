package io.github.autoequipmentmaking.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;

/** Actual production using purchased equipment/book, block currency, protected currency and food. */
public class V11ProductionGameTest extends ProductionGameTest {
    @Override protected void fixtures(ServerLevel level,ServerPlayer player){
        ItemStack power=null,unbreaking=null;int removed=0;for(var pos:bookBoxes()){var c=(Container)level.getBlockEntity(pos);for(int i=0;i<c.getContainerSize();i++)if(Stacks.ench(c.getItem(i),true).containsKey("minecraft:power")){power=c.getItem(i).copy();c.setItem(i,ItemStack.EMPTY);}else if(removed<3&&Stacks.ench(c.getItem(i),true).equals(Map.of("minecraft:unbreaking",3))){unbreaking=c.getItem(i).copy();c.setItem(i,ItemStack.EMPTY);removed++;}}
        if(power==null)throw new AssertionError("Power book fixture missing");
        if(removed!=3)throw new AssertionError("Three Unbreaking books fixture missing");
        var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));raw.setItem(5,new ItemStack(Items.BOOK,4));raw.setItem(6,new ItemStack(Items.EMERALD_BLOCK,5));
        var v=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);v.setPos(10.5,65,3.5);v.setNoAi(true);v.setInvulnerable(true);v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(),VillagerProfession.FLETCHER).withLevel(5));
        var offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemCost(Items.EMERALD),new ItemStack(Items.ARROW,8),100,0,0));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,4),new ItemStack(Items.BOW),100,0,0));offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,5),new ItemStack(Items.SHIELD),100,0,0));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,2),Optional.of(new ItemCost(Items.BOOK)),power,100,0,0));offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,10),Optional.of(new ItemCost(Items.BOOK)),unbreaking,100,0,0));v.setOffers(offers);level.addFreshEntity(v);
        player.getInventory().setItem(0,new ItemStack(Items.EMERALD,7));player.getInventory().setItem(1,new ItemStack(Items.BOOK,3));player.getInventory().setItem(10,new ItemStack(Items.GOLDEN_CARROT,2));player.getFoodData().setFoodLevel(14);player.getFoodData().setSaturation(0);
    }
    @Override protected void afterStart(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runOnServer(server->server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> pause"),false));
        context.waitFor(mc->AutoEquipmentClient.robot.manualPaused,100);
        long[] before={0};context.runOnClient(mc->{var r=AutoEquipmentClient.robot;before[0]=r.ticks;if(r.running)throw new AssertionError("Chat pause did not stop");});context.waitTicks(40);
        context.runOnClient(mc->{if(AutoEquipmentClient.robot.ticks!=before[0])throw new AssertionError("Queue advanced while paused");});
        world.getServer().runOnServer(server->server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> continue"),false));context.waitFor(mc->AutoEquipmentClient.robot.running,100);
    }
    @Override protected void verifyExtra(ServerLevel level,ServerPlayer player){
        var inv=player.getInventory();if(!inv.getItem(0).is(Items.EMERALD)||inv.getItem(0).getCount()!=7||!inv.getItem(1).is(Items.BOOK)||inv.getItem(1).getCount()!=3)throw new AssertionError("Protected currency consumed");
        if(!inv.getItem(10).is(Items.GOLDEN_CARROT)||inv.getItem(10).getCount()!=1)throw new AssertionError("Food must consume exactly one and restore slot");
        var v=level.getEntitiesOfClass(Villager.class,new net.minecraft.world.phys.AABB(-9,64,-9,14,70,14)).getFirst();for(int i=1;i<=3;i++)if(v.getOffers().get(i).getUses()!=1)throw new AssertionError("Wrong trade count "+i);if(v.getOffers().get(0).getUses()!=0)throw new AssertionError("Unrelated trade bought");
        if(v.getOffers().get(4).getUses()!=3)throw new AssertionError("Unbreaking III was not purchased exactly three times");
        try{var lines=Files.readAllLines(AutoEquipmentClient.robot.journal);var events=lines.stream().map(l->com.google.gson.JsonParser.parseString(l).getAsJsonObject()).filter(o->o.get("task").getAsString().equals(AutoEquipmentClient.robot.task)).map(o->o.get("event").getAsString()).toList();
            int craft=events.indexOf("CRAFT ALL COMPLETE"),smith=events.indexOf("SMITH ALL COMPLETE"),bases=events.indexOf("BASES PACKED count=14");if(!(craft>=0&&smith>craft&&bases>smith))throw new AssertionError("Wrong production phases");
            int ready=events.indexOf("PRETRADE COMPLETE all kit deficits filled before diamond crafting");if(ready<0||ready>=craft)throw new AssertionError("Purchases were not completed before crafting");
            if(events.subList(ready,craft).stream().filter(e->e.startsWith("OPEN CraftingMenu")).count()!=1||events.subList(craft,smith).stream().filter(e->e.startsWith("OPEN SmithingMenu")).count()!=1)throw new AssertionError("Base production revisited workstation");
            if(events.subList(ready,events.size()).stream().anyMatch(e->e.startsWith("TRADE BUY INTENT")||e.equals("EMERALD BLOCK UNPACKED")))throw new AssertionError("Known shortages were purchased after crafting began");
            if(events.subList(0,bases).stream().anyMatch(e->e.startsWith("OPEN AnvilMenu")))throw new AssertionError("Enchanted before base packing");
            if(events.stream().noneMatch(e->e.startsWith("ENCHANT BATCH")&&Integer.parseInt(e.split("items=")[1].split(" ")[0])>1))throw new AssertionError("No multi-equipment batch");
            if(events.stream().filter(e->e.equals("EMERALD BLOCK UNPACKED")).count()!=5||events.stream().filter(e->e.startsWith("TRADE BUY CONFIRMED")).count()!=6)throw new AssertionError("Trade/block accounting mismatch");
            if(!events.contains("PRETRADE PLAN products=6 emeralds=41 books=4"))throw new AssertionError("Initial batch did not include the entire known shortage");
            if(events.stream().noneMatch(e->e.startsWith("TRADE SCAN villagers=1 ")&&e.contains("observed=1 opened=0")))throw new AssertionError("Quote discovery opened a merchant menu instead of observing server data");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}

