package io.github.autoequipmentmaking.client;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import com.google.gson.*;

/** Isolated client/server test: fixtures supply ingredients, never final enchanted gear. */
public class ProductionGameTest implements FabricClientGameTest {
    protected boolean complex(){return false;}
    protected boolean shortageOnly(){return false;}
    protected List<Integer> bookLevels(String id,int level){return List.of(level);}
    protected int expectedBooks(){return 55;}
    protected int expectedLevels(){return 319;}
    protected void fixtures(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player){}
    protected void afterStart(ClientGameTestContext context,net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world){}
    protected void verifyExtra(net.minecraft.server.level.ServerLevel level,net.minecraft.server.level.ServerPlayer player){}
    protected void verifyConsumedSources(net.minecraft.server.level.ServerLevel level){
        var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));for(int batch=0;batch<kitCount();batch++)for(int i=0;i<7;i++)if(!raw.getItem(batch*10+i).isEmpty())throw new AssertionError("Unconsumed raw input");
        BlockPos[] sourceBoxes=bookBoxes();for(int i=0;i<expectedBooks()*kitCount();i++)if(!((Container)level.getBlockEntity(sourceBoxes[i/27])).getItem(i%27).isEmpty())throw new AssertionError("Unconsumed book");
    }
    int kitCount(){return complex()?2:1;}
    BlockPos[] bookBoxes(){return new BlockPos[]{new BlockPos(0,65,3),new BlockPos(2,complex()?66:65,3),new BlockPos(4,65,3),new BlockPos(0,65,1),new BlockPos(2,65,1)};}
    ItemStack[] materials(){return new ItemStack[]{new ItemStack(Items.DIAMOND,40),new ItemStack(Items.STICK,12),new ItemStack(Items.NETHERITE_INGOT,11),new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,11),new ItemStack(Items.ELYTRA),new ItemStack(Items.BOW),new ItemStack(Items.SHIELD)};}
    final java.util.concurrent.atomic.AtomicBoolean climbed=new java.util.concurrent.atomic.AtomicBoolean();
    final java.util.concurrent.atomic.AtomicReference<ItemStack> withheldBook=new java.util.concurrent.atomic.AtomicReference<>();
    @Override public void runTest(ClientGameTestContext context){
        // Production fixtures always represent the complete kit, independently of saved UI-test settings.
        context.runOnClient(mc->{var s=RobotSettings.INSTANCE;s.quantityMode.setOptionListValue(RobotSettings.QuantityMode.AUTO);
            s.outputBoxes.setStrings(List.of("minecraft:light_blue_shulker_box"));s.scanLength.setIntegerValue(21);s.scanWidth.setIntegerValue(21);s.scanHeight.setIntegerValue(5);
            for(var option:List.of(s.swiftSneak,s.soulSpeed,s.elytra,s.shield,s.trading,s.spareAnvils))option.setBooleanValue(true);
            var r=AutoEquipmentClient.robot;r.kit.clear();r.kit.addAll(r.kitTemplate);
        });
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(server->{var level=server.overworld();
                for(int x=-10;x<=15;x++)for(int z=-10;z<=15;z++){level.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());for(int y=65;y<=70;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                if(complex()){for(int x=-9;x<=14;x++)for(int z=4;z<=5;z++)level.setBlockAndUpdate(new BlockPos(x,65,z),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(new BlockPos(2,65,3),Blocks.STONE.defaultBlockState());}
                BlockPos[] boxes=bookBoxes();
                for(var p:boxes)level.setBlockAndUpdate(p,Blocks.SHULKER_BOX.defaultBlockState());
                var kit=AutoEquipmentClient.robot.kit;int slot=0;
                for(int batch=0;batch<kitCount();batch++)for(var t:kit)for(var e:t.ench().entrySet())for(int bookLevel:bookLevels(e.getKey(),e.getValue())){
                    var book=new ItemStack(Items.ENCHANTED_BOOK);var ench=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    ench.set(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(e.getKey())).orElseThrow(),bookLevel);book.set(DataComponents.STORED_ENCHANTMENTS,ench.toImmutable());
                    ((Container)level.getBlockEntity(boxes[slot/27])).setItem(slot%27,book);slot++;
                }
                level.setBlockAndUpdate(new BlockPos(0,65,0),Blocks.CHEST.defaultBlockState());var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));
                for(int batch=0;batch<kitCount();batch++){var inputs=materials();for(int i=0;i<inputs.length;i++)raw.setItem(batch*10+i,inputs[i]);}
                level.setBlockAndUpdate(new BlockPos(6,complex()?66:65,5),Blocks.DYED_SHULKER_BOX.lightBlue().defaultBlockState());
                if(complex()){
                    level.setBlockAndUpdate(new BlockPos(0,66,5),Blocks.DYED_SHULKER_BOX.lightBlue().defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(7,65,7),Blocks.DYED_SHULKER_BOX.lightBlue().defaultBlockState());
                    ((Container)level.getBlockEntity(new BlockPos(7,65,7))).setItem(0,new ItemStack(Items.DIAMOND,64));
                    level.setBlockAndUpdate(new BlockPos(9,65,7),Blocks.DYED_SHULKER_BOX.lightBlue().defaultBlockState());
                    for(var pair:Map.of(new BlockPos(-2,65,0),Blocks.DYED_SHULKER_BOX.red(),new BlockPos(-4,65,0),Blocks.BARREL).entrySet()){
                        level.setBlockAndUpdate(pair.getKey(),pair.getValue().defaultBlockState());
                        ((Container)level.getBlockEntity(pair.getKey())).setItem(0,new ItemStack(Items.DIAMOND,64));
                        ((Container)level.getBlockEntity(pair.getKey())).setItem(1,((Container)level.getBlockEntity(boxes[0])).getItem(0).copy());
                    }
                }
                if(shortageOnly()){raw.setItem(0,new ItemStack(Items.DIAMOND,39));raw.setItem(1,new ItemStack(Items.STICK,11));raw.setItem(2,new ItemStack(Items.NETHERITE_INGOT,10));raw.setItem(3,new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,10));for(int i=4;i<7;i++)raw.setItem(i,ItemStack.EMPTY);var source=(Container)level.getBlockEntity(boxes[0]);withheldBook.set(source.getItem(0).copy());source.setItem(0,ItemStack.EMPTY);}
                level.setBlockAndUpdate(new BlockPos(2,65,0),Blocks.CRAFTING_TABLE.defaultBlockState());level.setBlockAndUpdate(new BlockPos(4,65,0),Blocks.SMITHING_TABLE.defaultBlockState());level.setBlockAndUpdate(new BlockPos(6,65,0),Blocks.ANVIL.defaultBlockState());level.setBlockAndUpdate(new BlockPos(8,65,0),Blocks.CHEST.defaultBlockState());
                for(int z:new int[]{-2,-4,-6,-8})level.setBlockAndUpdate(new BlockPos(6,65,z),Blocks.ANVIL.defaultBlockState());
                if(complex()){raw.setItem(18,new ItemStack(Items.ANVIL,8));raw.setItem(8,new ItemStack(Items.COBBLESTONE,64));raw.setItem(9,new ItemStack(Items.BOOK,12));((Container)level.getBlockEntity(boxes[4])).setItem(2,new ItemStack(Items.DIRT,64));((Container)level.getBlockEntity(boxes[4])).setItem(3,new ItemStack(Items.ARROW,32));}
                var player=server.getPlayerList().getPlayers().getFirst();if(complex()){player.getInventory().setItem(4,new ItemStack(Items.DIRT,64));player.getInventory().setItem(10,new ItemStack(Items.COBBLESTONE,64));player.getInventory().setItem(15,new ItemStack(Items.BOOK,1));player.getInventory().setItem(20,new ItemStack(Items.DIAMOND,7));player.getInventory().setItem(25,new ItemStack(Items.ARROW,64));}player.setGameMode(GameType.SURVIVAL);player.giveExperienceLevels(shortageOnly()?0:1500);player.teleportTo(3.5,65,7.5);fixtures(level,player);
            });
            context.waitTicks(60);
            context.runOnClient(mc->{
                try{var generic=Class.forName("fi.dy.masa.minihud.config.Configs$Generic");Object enabled=generic.getField("ENTITY_DATA_SYNC").get(null);enabled.getClass().getMethod("setBooleanValue",boolean.class).invoke(enabled,true);}catch(Exception e){throw new IllegalStateException(e);}
                var robot=AutoEquipmentClient.robot;var config=robot.cfg.root;config.addProperty("serverKey","singleplayer");config.addProperty("dimension",robot.dimension());
                var bounds=new JsonObject();bounds.add("min",WorkConfig.pos(new BlockPos(-9,64,-9)));bounds.add("max",WorkConfig.pos(new BlockPos(14,70,14)));config.add("platformBounds",bounds);
                for(var entry:Map.of("crafting",new BlockPos(2,65,0),"smithing",new BlockPos(4,65,0),"anvils",new BlockPos(6,65,0)).entrySet()){var array=new JsonArray();array.add(WorkConfig.pos(entry.getValue()));config.getAsJsonObject("workstations").add(entry.getKey(),array);}
                for(int z:new int[]{-2,-4,-6,-8})config.getAsJsonObject("workstations").getAsJsonArray("anvils").add(WorkConfig.pos(new BlockPos(6,65,z)));
                config.getAsJsonObject("packing").add("placement",WorkConfig.pos(new BlockPos(6,complex()?66:65,5)));config.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(new BlockPos(8,65,0)));config.getAsJsonObject("notifications").addProperty("chatEnabled",shortageOnly());robot.cfg.save();mc.gui.setScreen(null);robot.start();
                if(!robot.running)throw new IllegalStateException("Robot start failed: "+robot.state);
            });
            afterStart(context,world);
            if(shortageOnly()){exerciseRestock(context,world);return;}
            context.waitFor(mc->{var robot=AutoEquipmentClient.robot;if(mc.player.getY()>=66)climbed.set(true);if(!robot.running&&!robot.state.equals("COMPLETED"))throw new IllegalStateException("Robot stopped: "+robot.state+"; inspect task.jsonl");return robot.state.equals("COMPLETED");},40000);
            world.getServer().runOnServer(server->{var level=server.overworld();var output=(Container)level.getBlockEntity(new BlockPos(8,65,0));if(output.isEmpty())throw new AssertionError("Output chest empty");
                int boxes=0;for(int i=0;i<output.getContainerSize();i++)if(!output.getItem(i).isEmpty()){
                    boxes++;if(!output.getItem(i).is(Items.DYED_SHULKER_BOX.lightBlue()))throw new AssertionError("Wrong output color");var contents=output.getItem(i).getOrDefault(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY).allItemsCopyStream().toList();
                    for(var target:AutoEquipmentClient.robot.kit){if(contents.size()<=target.slot())throw new AssertionError("Missing gear "+target.key());var gear=contents.get(target.slot());if(!Stacks.id(gear).equals(target.item())||gear.getCount()!=1||gear.getDamageValue()!=0||!Stacks.ench(gear,false).equals(target.ench()))throw new AssertionError("Invalid gear "+target.key());}
                    for(int j=14;j<contents.size();j++)if(!contents.get(j).isEmpty())throw new AssertionError("Unexpected box contents");
                }if(boxes!=kitCount())throw new AssertionError("Wrong batch count "+boxes);
                verifyConsumedSources(level);var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));
                if(complex()){if(raw.getItem(8).getCount()!=64||raw.getItem(9).getCount()!=12)throw new AssertionError("Container clutter changed");var inv=server.getPlayerList().getPlayers().getFirst().getInventory();int[] slots={4,10,15,20,25},counts={64,64,1,7,64};Item[] types={Items.DIRT,Items.COBBLESTONE,Items.BOOK,Items.DIAMOND,Items.ARROW};for(int i=0;i<slots.length;i++)if(!inv.getItem(slots[i]).is(types[i])||inv.getItem(slots[i]).getCount()!=counts[i])throw new AssertionError("Protected player inventory changed");}
                var player=server.getPlayerList().getPlayers().getFirst();if(player.experienceLevel!=1500-expectedLevels()*kitCount())throw new AssertionError("Unexpected level debit "+player.experienceLevel);verifyExtra(level,player);
            });
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;if(r.batchBoxes.size()!=kitCount()||r.deliveredKits!=kitCount())throw new AssertionError("Batch accounting mismatch");if(complex()&&!climbed.get())throw new AssertionError("One-block height was not traversed");});
            if(complex())world.getServer().runOnServer(server->{var l=server.overworld();
                for(var p:List.of(new BlockPos(-2,65,0),new BlockPos(-4,65,0),new BlockPos(7,65,7)))if(((Container)l.getBlockEntity(p)).getItem(0).getCount()!=64)throw new AssertionError("Excluded container consumed "+p);
                if(!l.getBlockState(new BlockPos(9,65,7)).is(Blocks.DYED_SHULKER_BOX.lightBlue())||!((Container)l.getBlockEntity(new BlockPos(9,65,7))).isEmpty())throw new AssertionError("Out-of-range box touched");
                for(var p:List.of(new BlockPos(6,66,5),new BlockPos(0,66,5)))if(!l.getBlockState(p).isAir())throw new AssertionError("Batch box not recovered");
            });
            context.takeScreenshot("newcomer-equipment-complete");
        }
    }
    void exerciseRestock(ClientGameTestContext context,net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world){
        context.waitFor(mc->AutoEquipmentClient.robot.awaitingGood,600);
        context.runOnClient(mc->{String reason=AutoEquipmentClient.robot.waitingReason;for(String token:new String[]{"钻石","木棍","下界合金锭","下界合金升级模板","附魔书"})if(!reason.contains(token))throw new AssertionError("Missing shortage diagnosis "+token+": "+reason);});
        world.getServer().runOnServer(server->{var raw=(Container)server.overworld().getBlockEntity(new BlockPos(0,65,0));ItemStack[] items=materials();for(int i=0;i<items.length;i++)raw.setItem(i,items[i]);server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> goodbye"),false);});
        context.waitTicks(30);context.runOnClient(mc->{if(!AutoEquipmentClient.robot.awaitingGood||AutoEquipmentClient.robot.restockAcks!=0)throw new AssertionError("Resumed without good");});
        sendGood(world);context.waitFor(mc->AutoEquipmentClient.robot.restockAcks==1&&AutoEquipmentClient.robot.awaitingGood,600);
        context.runOnClient(mc->{if(!AutoEquipmentClient.robot.waitingReason.contains("附魔书"))throw new AssertionError("Missing book not detected after good");});
        world.getServer().runOnServer(server->((Container)server.overworld().getBlockEntity(new BlockPos(0,65,3))).setItem(0,withheldBook.get()));
        sendGood(world);context.waitFor(mc->AutoEquipmentClient.robot.restockAcks==2&&AutoEquipmentClient.robot.awaitingGood,600);
        context.runOnClient(mc->{if(!AutoEquipmentClient.robot.waitingReason.contains("经验不足"))throw new AssertionError("XP deficit not detected");});
        world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().giveExperienceLevels(1500));context.waitTicks(20);
        context.runOnClient(mc->{if(!AutoEquipmentClient.robot.awaitingGood)throw new AssertionError("Resumed on XP change without good");});
        sendGood(world);context.waitFor(mc->AutoEquipmentClient.robot.restockAcks==3&&AutoEquipmentClient.robot.state.startsWith("PRODUCING"),1200);
        context.runOnClient(mc->{AutoEquipmentClient.robot.pause("缺货与 good 复查测试完成");AutoEquipmentClient.robot.cancel();});
        context.takeScreenshot("shortage-good-confirmed");
    }
    void sendGood(net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world){world.getServer().runOnServer(server->server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> good"),false));}

}
