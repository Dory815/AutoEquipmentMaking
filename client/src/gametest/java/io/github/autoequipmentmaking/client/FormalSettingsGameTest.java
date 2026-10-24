package io.github.autoequipmentmaking.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.List;
import java.util.HashMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Exercises the actual MaLiLib screen, settings persistence and task snapshot in an integrated world. */
public final class FormalSettingsGameTest implements FabricClientGameTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            var prior=new HashMap<String,JsonElement>();
            world.getServer().runOnServer(server->{var level=server.overworld();
                for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){level.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());for(int y=65;y<=70;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                level.setBlockAndUpdate(new BlockPos(0,65,0),Blocks.CHEST.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(2,65,0),Blocks.DYED_SHULKER_BOX.lightBlue().defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(4,65,0),Blocks.DYED_SHULKER_BOX.lightBlue().defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(6,65,0),Blocks.DYED_SHULKER_BOX.red().defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(8,65,0),Blocks.CRAFTING_TABLE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(9,65,0),Blocks.SMITHING_TABLE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(10,65,0),Blocks.ANVIL.defaultBlockState());
                var player=server.getPlayerList().getPlayers().getFirst();player.setGameMode(GameType.SURVIVAL);player.teleportTo(0.5,65,5.5);
            });context.waitTicks(40);
            context.runOnClient(mc->{RobotSettings.INSTANCE.openGui();check(mc.gui.screen()!=null,"MaLiLib settings screen did not open");});
            context.waitTicks(5);context.takeScreenshot("formal-settings-ui");
            context.runOnClient(mc->{var settings=RobotSettings.INSTANCE;
                for(var option:settings.all)prior.put(option.getName(),option.getAsJsonElement().deepCopy());
                mc.gui.setScreen(null);
                check(settings.fontScale.getMinDoubleValue()==0.1&&settings.fontScale.getMaxDoubleValue()==3.0,"Font scale range incorrect");
                settings.fontScale.setDoubleValue(0);check(settings.fontScale.getDoubleValue()==0.1,"Font scale did not clamp low value");
                settings.fontScale.setDoubleValue(4);check(settings.fontScale.getDoubleValue()==3.0,"Font scale did not clamp high value");
                settings.fontScale.setDoubleValue(1.8);settings.openGui();
            });
            context.waitTicks(5);context.takeScreenshot("formal-settings-font-1_8");
            context.runOnClient(mc->{var settings=RobotSettings.INSTANCE;mc.gui.setScreen(null);settings.fontScale.setDoubleValue(3.0);settings.openGui();});
            context.waitTicks(5);context.takeScreenshot("formal-settings-font-3_0");
            context.runOnClient(mc->{var settings=RobotSettings.INSTANCE;mc.gui.setScreen(null);settings.fontScale.setDoubleValue(0.1);settings.openGui();});
            context.waitTicks(5);context.takeScreenshot("formal-settings-font-0_1");
            context.runOnClient(mc->{var settings=RobotSettings.INSTANCE;mc.gui.setScreen(null);settings.fontScale.setDoubleValue(1.8);
                check(settings.quantity.getMinIntegerValue()==1&&settings.quantity.getMaxIntegerValue()==10,"Manual slider range incorrect");
                check(settings.scanLength.getMinIntegerValue()==3&&settings.scanLength.getMaxIntegerValue()==51,"Length slider range incorrect");
                check(settings.scanWidth.getMinIntegerValue()==3&&settings.scanWidth.getMaxIntegerValue()==51,"Width slider range incorrect");
                check(settings.scanHeight.getMinIntegerValue()==1&&settings.scanHeight.getMaxIntegerValue()==21,"Height slider range incorrect");
                settings.start.setHotkeyStringValue("F7");settings.quantityMode.setOptionListValue(RobotSettings.QuantityMode.MANUAL);settings.quantity.setIntegerValue(0);check(settings.quantity.getIntegerValue()>=1,"Manual quantity accepted zero");settings.quantity.setIntegerValue(1);
                settings.scanLength.setIntegerValue(21);settings.scanWidth.setIntegerValue(21);settings.scanHeight.setIntegerValue(5);
                settings.outputBoxes.setStrings(List.of("minecraft:light_blue_shulker_box","minecraft:red_shulker_box"));
                settings.swiftSneak.setBooleanValue(false);settings.soulSpeed.setBooleanValue(false);settings.elytra.setBooleanValue(false);settings.shield.setBooleanValue(false);settings.trading.setBooleanValue(false);settings.spareAnvils.setBooleanValue(false);settings.save();
                settings.quantity.setIntegerValue(2);settings.start.setHotkeyStringValue("F8");settings.load();check(settings.quantity.getIntegerValue()==1,"Settings did not reload");check(settings.start.getHotkeyStringValue().equals("F7"),"Hotkey did not reload");check(Files.isRegularFile(RobotSettings.FILE),"Settings file absent");
                settings.outputBoxes.setStrings(List.of("minecraft:stone"));boolean rejected=false;try{settings.outputBlocks();}catch(IllegalStateException expected){rejected=true;}check(rejected,"Non-shulker output accepted");settings.outputBoxes.setStrings(List.of("minecraft:light_blue_shulker_box","minecraft:red_shulker_box"));
                var r=AutoEquipmentClient.robot;var root=r.cfg.root;root.addProperty("serverKey","singleplayer");root.addProperty("dimension",r.dimension());
                var bounds=new JsonObject();bounds.add("min",WorkConfig.pos(new BlockPos(-12,64,-12)));bounds.add("max",WorkConfig.pos(new BlockPos(12,70,12)));root.add("platformBounds",bounds);
                for(var e:java.util.Map.of("crafting",new BlockPos(8,65,0),"smithing",new BlockPos(9,65,0),"anvils",new BlockPos(10,65,0)).entrySet()){var list=new JsonArray();list.add(WorkConfig.pos(e.getValue()));root.getAsJsonObject("workstations").add(e.getKey(),list);}
                root.getAsJsonObject("packing").remove("outputContainer");root.getAsJsonObject("notifications").addProperty("chatEnabled",false);
                r.requestStart();check(r.awaitingDelivery,"Start did not request a delivery chest");r.playerChat("Player0","skip");check(!r.awaitingDelivery,"Skip did not bind delivery chest");check(r.cfg.output().equals(new BlockPos(0,65,0)),"Skip picked the wrong chest");
                check(r.running,"Start failed after skip");check(r.requestedKits==1,"Manual quantity not snapshotted");check(r.kit.size()==12,"Optional equipment not removed");check(r.kit.stream().noneMatch(t->t.ench().containsKey("minecraft:swift_sneak")||t.ench().containsKey("minecraft:soul_speed")),"Optional enchants not removed");
                check(!r.allowTrading&&!r.allowSpareAnvils,"Supply toggles not snapshotted");check(r.taskBoxBlocks.contains(Blocks.DYED_SHULKER_BOX.red()),"Configured output color missing");
            });
            context.waitFor(mc->AutoEquipmentClient.robot.activePacking!=null||AutoEquipmentClient.robot.awaitingGood,400);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.batchBoxes.size()==1,"Manual quantity selected wrong number of boxes");check(r.taskBoxBlocks.contains(mc.level.getBlockState(r.activePacking).getBlock()),"Task selected wrong box type");r.cancel();});
            context.runOnClient(mc->{var settings=RobotSettings.INSTANCE;settings.quantityMode.setOptionListValue(RobotSettings.QuantityMode.AUTO);settings.swiftSneak.setBooleanValue(true);settings.soulSpeed.setBooleanValue(true);settings.elytra.setBooleanValue(true);settings.shield.setBooleanValue(true);settings.trading.setBooleanValue(true);settings.spareAnvils.setBooleanValue(true);
                var r=AutoEquipmentClient.robot;r.requestStart();
                var wrong=new BlockPos(0,65,4);mc.hitResult=new BlockHitResult(Vec3.atCenterOf(wrong),Direction.UP,wrong,false);r.bindDelivery();check(r.awaitingDelivery,"Invalid delivery block accepted");
                var chest=new BlockPos(0,65,0);mc.hitResult=new BlockHitResult(Vec3.atCenterOf(chest),Direction.UP,chest,false);r.bindDelivery();check(!r.awaitingDelivery&&r.running,"Delivery binding shortcut failed");check(r.requestedKits==-1,"Automatic quantity not selected");check(r.kit.size()==14,"Default equipment list not restored");check(r.allowTrading&&r.allowSpareAnvils,"Supply toggles did not update for next task");
            });
            context.waitFor(mc->AutoEquipmentClient.robot.activePacking!=null||AutoEquipmentClient.robot.awaitingGood,400);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.batchBoxes.size()==3,"Automatic scan did not count three configured empty shulker boxes");r.cancel();});
            context.runOnClient(mc->{var settings=RobotSettings.INSTANCE;for(var option:settings.all)option.setValueFromJsonElement(prior.get(option.getName()));settings.save();});
        }
    }
}
