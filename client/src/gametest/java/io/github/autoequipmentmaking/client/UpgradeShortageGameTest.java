package io.github.autoequipmentmaking.client;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Rejects I+II, requires a real chat acknowledgement, then accepts II+I+I. */
public final class UpgradeShortageGameTest extends ProductionGameTest {
    @Override protected boolean shortageOnly(){return true;}
    @Override protected List<Integer> bookLevels(String id,int level){
        return io.github.autoequipmentmaking.engine.planner.UpgradeBooks.supported(id)?List.of(2,1):List.of(level);
    }
    @Override void exerciseRestock(ClientGameTestContext context,TestSingleplayerContext world){
        context.waitFor(mc->AutoEquipmentClient.robot.awaitingGood,600);
        world.getServer().runOnServer(server->{
            var level=server.overworld();var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));
            ItemStack[] inputs=materials();for(int i=0;i<inputs.length;i++)raw.setItem(i,inputs[i]);
            ((Container)level.getBlockEntity(bookBoxes()[0])).setItem(0,withheldBook.get());
            server.getPlayerList().getPlayers().getFirst().giveExperienceLevels(1500);
        });
        sendGood(world);
        context.waitFor(mc->AutoEquipmentClient.robot.restockAcks==1&&AutoEquipmentClient.robot.awaitingGood,600);
        context.runOnClient(mc->{
            String reason=AutoEquipmentClient.robot.waitingReason;
            for(String token:List.of("灵魂疾行","迅捷潜行","I=1","II=1","III=0","可合成III=0"))if(!reason.contains(token))throw new AssertionError("Wrong lower-book deficit: "+reason);
        });
        world.getServer().runOnServer(server->{
            var level=server.overworld();var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));int slot=10;
            for(String id:List.of("minecraft:soul_speed","minecraft:swift_sneak")){
                var book=new ItemStack(Items.ENCHANTED_BOOK);var ench=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                ench.set(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(id)).orElseThrow(),1);
                book.set(DataComponents.STORED_ENCHANTMENTS,ench.toImmutable());raw.setItem(slot++,book);
            }
            server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("<Player0> goodbye"),false);
        });
        context.waitTicks(30);
        context.runOnClient(mc->{if(!AutoEquipmentClient.robot.awaitingGood||AutoEquipmentClient.robot.restockAcks!=1)throw new AssertionError("Resumed without standalone good");});
        sendGood(world);
        context.waitFor(mc->AutoEquipmentClient.robot.restockAcks==2&&AutoEquipmentClient.robot.state.startsWith("PRODUCING"),1200);
        context.runOnClient(mc->{
            var r=AutoEquipmentClient.robot;
            for(String id:List.of("minecraft:soul_speed","minecraft:swift_sneak")){
                var books=r.plans.stream().flatMap(p->p.books().stream()).filter(b->b.enchantment().equals(id)).map(b->b.level()).sorted().toList();
                if(!books.equals(List.of(1,1,2)))throw new AssertionError("Wrong upgrade selection "+books);
            }
            r.cancel();
        });
    }
}
