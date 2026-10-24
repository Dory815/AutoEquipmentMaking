package io.github.autoequipmentmaking.client;
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

/** Packing-only fixture. This test does not count as a production run. */
public final class PackingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().create()){
            world.getServer().runOnServer(server->{var level=server.overworld();
                for(int x=-6;x<=8;x++)for(int z=-6;z<=8;z++){level.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());for(int y=65;y<70;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                for(int x=-6;x<=8;x++)for(int z=2;z<=3;z++)level.setBlockAndUpdate(new BlockPos(x,65,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(0,66,3),Blocks.DYED_SHULKER_BOX.lightBlue().defaultBlockState());level.setBlockAndUpdate(new BlockPos(4,65,0),Blocks.CHEST.defaultBlockState());
                var box=(Container)level.getBlockEntity(new BlockPos(0,66,3));
                for(var target:AutoEquipmentClient.robot.kit){var stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(target.item())));var ench=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);for(var e:target.ench().entrySet())ench.set(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(e.getKey())).orElseThrow(),e.getValue());stack.set(DataComponents.ENCHANTMENTS,ench.toImmutable());box.setItem(target.slot(),stack);}
                var player=server.getPlayerList().getPlayers().getFirst();player.setGameMode(GameType.SURVIVAL);player.teleportTo(.5,65,.5);
            });context.waitTicks(40);
            context.runOnClient(mc->{var r=AutoEquipmentClient.robot;var c=r.cfg.root;var b=new JsonObject();b.add("min",WorkConfig.pos(new BlockPos(-6,64,-6)));b.add("max",WorkConfig.pos(new BlockPos(8,70,8)));c.add("platformBounds",b);c.getAsJsonObject("packing").add("placement",WorkConfig.pos(new BlockPos(0,66,3)));c.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(new BlockPos(4,65,0)));c.getAsJsonObject("notifications").addProperty("chatEnabled",false);r.activePacking=null;r.batchBoxes.clear();r.deliveredKits=0;r.protectedSlots.clear();r.queue.clear();r.task="PACKING_FIXTURE_"+UUID.randomUUID();r.server="singleplayer";r.dimension=r.dimension();r.completed=14;r.uncertain=false;r.mining=false;r.running=true;r.queue.add(()->{r.scheduleDelivery();return true;});mc.gui.setScreen(null);});
            try{context.waitFor(mc->{var r=AutoEquipmentClient.robot;if(!r.running&&!r.state.equals("COMPLETED"))throw new AssertionError("Packing stopped: "+r.state);return r.state.equals("COMPLETED");},1800);}catch(Throwable failure){
                world.getServer().runOnServer(server->{var l=server.overworld();System.out.println("PACKING DIAGNOSTIC block="+l.getBlockState(new BlockPos(0,66,3))+" player="+server.getPlayerList().getPlayers().getFirst().position());for(var entity:l.getAllEntities())if(entity instanceof net.minecraft.world.entity.item.ItemEntity item)System.out.println("PACKING DROP "+item.position()+" "+item.getItem());});throw failure;
            }
            context.takeScreenshot("packing-confirmed");
        }
    }
}
