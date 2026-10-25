package io.github.autoequipmentmaking.client;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Uses the actual grindstone menu and the existing confirmed result transaction. */
final class Grinding {
    static BlockPos station(Robot r){
        var configured=r.cfg.root.getAsJsonObject("workstations").getAsJsonArray("grindstones");
        if(configured!=null)for(var value:configured){var pos=WorkConfig.read(value);if(r.inside(pos)&&r.mc.level.getBlockState(pos).is(Blocks.GRINDSTONE))return pos;}
        BlockPos best=null;double distance=Double.POSITIVE_INFINITY;
        for(var pos:BlockPos.betweenClosed(r.cfg.min(),r.cfg.max())){
            r.require(r.mc.level.hasChunkAt(pos),"砂轮区域未加载，工作站未确认");
            if(r.mc.level.getBlockState(pos).is(Blocks.GRINDSTONE)&&pos.distSqr(r.mc.player.blockPosition())<distance){best=pos.immutable();distance=pos.distSqr(r.mc.player.blockPosition());}
        }
        return best;
    }
    static List<Robot.Step> steps(Robot r,BaseEquipmentPlan plan){
        return steps(r,plan.entries);
    }
    static List<Robot.Step> steps(Robot r,List<BaseEquipmentPlan.Entry> entries){
        var steps=new ArrayList<Robot.Step>();long count=entries.stream().filter(e->e.route()==BaseEquipmentPlan.Route.GRIND_DIAMOND).count();if(count==0)return steps;
        steps.add(()->{var pos=station(r);if(pos==null)throw new Robot.Waiting("砂轮缺货：请在工坊范围内放置砂轮，发送 good 后继续");r.prepend(List.of(r.new Open(pos,GrindstoneMenu.class)));return true;});
        for(var entry:entries)if(entry.route()==BaseEquipmentPlan.Route.GRIND_DIAMOND){
            ItemStack expected=entry.source().copy();
            steps.add(r.new Put(s->ItemStack.isSameItemSameComponents(s,expected),0,1));
            steps.add(r.new TakeResult(2,r.base(entry.diamond()),-1){
                @Override void captured(ItemStack result){
                    r.require(r.mc.player.containerMenu instanceof GrindstoneMenu,"砂轮菜单改变，禁止重放结果点击");
                    r.require(r.mc.player.containerMenu.getSlot(0).getItem().isEmpty()&&r.mc.player.containerMenu.getSlot(1).getItem().isEmpty(),"砂轮输入未确认消耗");
                    r.record("GRIND CONFIRMED "+entry.target().key()+" repairCost="+Stacks.penalty(result));
                }
            });
        }
        steps.add(()->{r.record("GRIND ALL COMPLETE count="+count);r.close();return true;});
        return steps;
    }
}
