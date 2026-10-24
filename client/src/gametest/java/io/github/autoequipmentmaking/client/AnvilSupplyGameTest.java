package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;

/** Real menus and placement packets: full/damaged spares, busy hotbar, stockouts and good retries. */
public final class AnvilSupplyGameTest implements FabricClientGameTest {
    static final BlockPos RAW=new BlockPos(0,65,0),BOOKS=new BlockPos(0,67,3),PAD=new BlockPos(6,65,0);
    static final List<Item> FILLERS=List.of(Items.DIAMOND,Items.STICK,Items.IRON_INGOT,Items.REDSTONE,Items.FEATHER,Items.STRING);
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(server->{
                var level=server.overworld();for(int x=-9;x<=14;x++)for(int z=-9;z<=14;z++){
                    level.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());for(int y=65;y<=70;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(RAW,Blocks.CHEST.defaultBlockState());var raw=(Container)level.getBlockEntity(RAW);
                raw.setItem(0,new ItemStack(Items.ANVIL));raw.setItem(1,new ItemStack(Items.CHIPPED_ANVIL));raw.setItem(2,new ItemStack(Items.DAMAGED_ANVIL));
                for(int i=0;i<FILLERS.size();i++)raw.setItem(4+i,new ItemStack(FILLERS.get(i)));
                level.setBlockAndUpdate(BOOKS,Blocks.SHULKER_BOX.defaultBlockState().setValue(ShulkerBoxBlock.FACING,Direction.DOWN));var books=(Container)level.getBlockEntity(BOOKS);
                for(int i=1;i<=14;i++){
                    var book=new ItemStack(Items.ENCHANTED_BOOK);var ench=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    ench.set(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse("minecraft:unbreaking")).orElseThrow(),i<=12?3:2);
                    book.set(DataComponents.STORED_ENCHANTMENTS,ench.toImmutable());books.setItem(i,book);
                }
                for(var p:List.of(new BlockPos(-2,65,0),new BlockPos(-4,65,0))){level.setBlockAndUpdate(p,p.getX()==-2?Blocks.DYED_SHULKER_BOX.red().defaultBlockState():Blocks.BARREL.defaultBlockState());((Container)level.getBlockEntity(p)).setItem(0,new ItemStack(Items.ANVIL,9));}
                level.setBlockAndUpdate(new BlockPos(2,65,0),Blocks.CRAFTING_TABLE.defaultBlockState());level.setBlockAndUpdate(new BlockPos(4,65,0),Blocks.SMITHING_TABLE.defaultBlockState());level.setBlockAndUpdate(new BlockPos(8,65,0),Blocks.CHEST.defaultBlockState());
                var player=server.getPlayerList().getPlayers().getFirst();player.setGameMode(GameType.SURVIVAL);player.giveExperienceLevels(100);player.getInventory().setItem(0,new ItemStack(Items.ANVIL));player.teleportTo(3.5,65,5.5);
            });context.waitTicks(40);
            context.runOnClient(mc->{
                var r=AutoEquipmentClient.robot;var cfg=r.cfg.root;cfg.addProperty("serverKey","singleplayer");cfg.addProperty("dimension",r.dimension());
                var bounds=new JsonObject();bounds.add("min",WorkConfig.pos(new BlockPos(-9,64,-9)));bounds.add("max",WorkConfig.pos(new BlockPos(14,70,14)));cfg.add("platformBounds",bounds);
                for(var e:Map.of("crafting",new BlockPos(2,65,0),"smithing",new BlockPos(4,65,0),"anvils",PAD).entrySet()){var a=new JsonArray();a.add(WorkConfig.pos(e.getValue()));cfg.getAsJsonObject("workstations").add(e.getKey(),a);}
                cfg.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(new BlockPos(8,65,0)));cfg.getAsJsonObject("notifications").addProperty("chatEnabled",true);mc.gui.setScreen(null);r.start();check(r.running,"Robot did not start");r.queue.clear();
                r.queue.add(r.new Scan());r.queue.add(()->{check(r.bookCount("minecraft:unbreaking",3)==12,"Elevated, down-facing shulker was omitted");return true;});
                // Fill all unprotected hotbar slots before fetching an anvil: placement must move a task item safely.
                r.queue.add(r.new Fetch(s->Stacks.book(s,"minecraft:unbreaking",2,0),2));
                for(Item item:FILLERS){String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();r.queue.add(r.new Fetch(r.base(id),1));}
                r.queue.add(new AnvilSupply(r));r.queue.add(r.new Put(s->Stacks.book(s,"minecraft:unbreaking",2,0),0,1));r.queue.add(r.new Put(s->Stacks.book(s,"minecraft:unbreaking",2,0),1,1));
                r.queue.add(r.new TakeResult(2,s->Stacks.book(s,"minecraft:unbreaking",3,1),3));r.queue.add(()->{r.close();return true;});
            });done(context);
            world.getServer().runOnServer(server->{var level=server.overworld();check(level.getBlockState(PAD).is(Blocks.ANVIL),"Fresh spare was not placed");check(((Container)level.getBlockEntity(RAW)).getItem(0).isEmpty(),"Fresh spare was not consumed");check(server.getPlayerList().getPlayers().getFirst().experienceLevel==97,"Actual anvil operation had wrong debit");level.setBlockAndUpdate(PAD,Blocks.AIR.defaultBlockState());});
            context.waitTicks(5);
            for(int stage=1;stage<=2;stage++){
                context.runOnClient(mc->schedule(AutoEquipmentClient.robot));done(context);int slot=stage;
                world.getServer().runOnServer(server->{var level=server.overworld();check(level.getBlockState(PAD).is(slot==1?Blocks.CHIPPED_ANVIL:Blocks.DAMAGED_ANVIL),"Wrong spare variant");check(((Container)level.getBlockEntity(RAW)).getItem(slot).isEmpty(),"Spare variant not consumed");level.setBlockAndUpdate(PAD,Blocks.AIR.defaultBlockState());});context.waitTicks(5);
            }
            context.runOnClient(mc->schedule(AutoEquipmentClient.robot));context.waitFor(mc->AutoEquipmentClient.robot.awaitingGood,600);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.waitingReason.contains("铁砧缺货"),"Missing anvil was not treated as a stockout");check(!r.uncertain,"Known anvil stockout required manual reconciliation");});
            good(world);context.waitFor(mc->AutoEquipmentClient.robot.restockAcks==1&&AutoEquipmentClient.robot.awaitingGood,600);
            world.getServer().runOnServer(server->{((Container)server.overworld().getBlockEntity(RAW)).setItem(0,new ItemStack(Items.ANVIL));server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> goodbye"),false);});
            context.waitTicks(30);context.runOnClient(mc->check(AutoEquipmentClient.robot.awaitingGood&&AutoEquipmentClient.robot.restockAcks==1,"Resumed before standalone good"));
            good(world);context.waitFor(mc->AutoEquipmentClient.robot.restockAcks==2,600);done(context);
            world.getServer().runOnServer(server->{
                var level=server.overworld();check(level.getBlockState(PAD).is(Blocks.ANVIL),"Restocked anvil not placed after good");check(((Container)level.getBlockEntity(RAW)).getItem(0).isEmpty(),"Restock source not consumed");
                var inv=server.getPlayerList().getPlayers().getFirst().getInventory();check(inv.getItem(0).is(Items.ANVIL)&&inv.getItem(0).getCount()==1,"Protected personal anvil was consumed");
                for(Item item:FILLERS){int count=0;for(int i=0;i<36;i++)if(inv.getItem(i).is(item))count+=inv.getItem(i).getCount();check(count==1,"Task item lost during hotbar preparation");}
                for(var p:List.of(new BlockPos(-2,65,0),new BlockPos(-4,65,0)))check(((Container)level.getBlockEntity(p)).getItem(0).getCount()==9,"Excluded source consumed");
                var books=(Container)level.getBlockEntity(BOOKS);for(int i=1;i<=12;i++)check(books.getItem(i).getCount()==1,"Unrelated level-III book consumed");
                level.setBlockAndUpdate(PAD.above(),Blocks.ANVIL.defaultBlockState());level.setBlockAndUpdate(PAD.above(2),Blocks.ANVIL.defaultBlockState());
            });
            context.waitTicks(5);
            context.runOnClient(mc->{
                var r=AutoEquipmentClient.robot;var a=new JsonArray();a.add(WorkConfig.pos(PAD.above(2)));r.cfg.root.getAsJsonObject("workstations").add("anvils",a);AnvilSupply.reset(r);
                check(PAD.equals(AnvilSupply.available(r)),"An unstable upper anvil was preferred over the stable bottom anvil");
                r.state="TEST_READ_ONLY_OPEN_RETRY";r.running=false;var open=r.new Open(PAD.above(2),AnvilMenu.class);r.queue.add(open);open.tick();check(open.sent&&open.pos.equals(PAD),"Anvil opener did not select the stable bottom");
            });
            // Simulate a lost/closed first menu response, without permitting any transfer into the menu.
            context.waitFor(mc->mc.player.containerMenu instanceof AnvilMenu,600);
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().closeContainer());
            context.waitFor(mc->mc.player.containerMenu==mc.player.inventoryMenu,600);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(()->{r.close();return true;});r.running=true;r.stepStart=r.ticks;});done(context);
            context.runOnClient(mc->{try{var r=AutoEquipmentClient.robot;check(java.nio.file.Files.readString(r.journal).contains("ANVIL OPEN RETRY: no menu, inventory and XP unchanged"),"Read-only retry was not exercised");check(mc.player.experienceLevel==97,"Menu retry consumed XP");}catch(java.io.IOException e){throw new AssertionError(e);}});
            BlockPos elevated=new BlockPos(10,68,7);
            world.getServer().runOnServer(server->{server.overworld().setBlockAndUpdate(elevated,Blocks.SHULKER_BOX.defaultBlockState());server.getPlayerList().getPlayers().getFirst().teleportTo(3.5,65,7.5);});context.waitTicks(5);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.queue.add(r.new Open(elevated,net.minecraft.world.inventory.ShulkerBoxMenu.class));r.queue.add(()->{r.close();return true;});r.state="TEST_ELEVATED_NAVIGATION";r.running=true;r.stepStart=r.ticks;});done(context);
        }
    }
    static void schedule(Robot r){r.queue.add(new AnvilSupply(r));r.queue.add(()->{r.close();return true;});r.state="TEST_ANVIL_SUPPLY";r.running=true;r.stepStart=r.ticks;}
    static void done(ClientGameTestContext context){context.waitFor(mc->{var r=AutoEquipmentClient.robot;if(!r.running&&!r.state.equals("COMPLETED"))throw new AssertionError("Anvil supply stopped: "+r.state+" "+r.waitingReason);return r.state.equals("COMPLETED");},2400);}
    static void good(TestSingleplayerContext world){world.getServer().runOnServer(server->server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> good"),false));}
}
