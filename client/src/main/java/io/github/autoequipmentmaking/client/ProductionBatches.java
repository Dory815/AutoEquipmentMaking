package io.github.autoequipmentmaking.client;

import io.github.autoequipmentmaking.engine.planner.*;
import java.util.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** One crafting visit, one smithing visit, then capacity-bounded enchanting batches. */
final class ProductionBatches {
    final Robot r;
    ProductionBatches(Robot r){this.r=r;}
    void schedule(){
        var steps=new ArrayList<Robot.Step>();r.state="PRODUCING BASES";
        for(var e:Map.of("minecraft:diamond",40,"minecraft:stick",12,"minecraft:netherite_ingot",11,"minecraft:netherite_upgrade_smithing_template",11).entrySet())steps.add(r.new Fetch(r.base(e.getKey()),e.getValue()));
        steps.add(r.new Open(r.cfg.station("crafting"),CraftingMenu.class));
        for(var t:r.kit)if(t.item().startsWith("minecraft:netherite_")){
            String type=t.item().substring("minecraft:netherite_".length());var pattern=Robot.pattern(type);
            for(int y=0;y<3;y++)for(int x=0;x<3;x++){char c=pattern[y].charAt(x);if(c!=' ')steps.add(r.new Put(r.base(c=='D'?"minecraft:diamond":"minecraft:stick"),1+y*3+x,1));}
            steps.add(r.new TakeResult(0,r.base("minecraft:diamond_"+type),-1));
        }
        steps.add(()->{r.record("CRAFT ALL COMPLETE");r.close();return true;});
        steps.add(r.new Open(r.cfg.station("smithing"),SmithingMenu.class));
        for(var t:r.kit)if(t.item().startsWith("minecraft:netherite_")){
            String type=t.item().substring("minecraft:netherite_".length());
            steps.add(r.new Put(r.base("minecraft:netherite_upgrade_smithing_template"),0,1));
            steps.add(r.new Put(r.base("minecraft:diamond_"+type),1,1));steps.add(r.new Put(r.base("minecraft:netherite_ingot"),2,1));
            steps.add(r.new TakeResult(3,r.base(t.item()),-1));
        }
        steps.add(()->{r.record("SMITH ALL COMPLETE");r.close();return true;});
        for(var t:r.kit)if(!t.item().startsWith("minecraft:netherite_"))steps.add(r.new Fetch(r.base(t.item()),1));
        steps.add(r.new Open(r.packing(),ShulkerBoxMenu.class));
        for(var t:r.kit)steps.add(r.new Put(r.base(t.item()),t.slot(),1));
        steps.add(()->{for(var t:r.kit)r.require(r.base(t.item()).test(r.mc.player.containerMenu.getSlot(t.slot()).getItem()),"基础装备装箱不符: "+t.key());r.record("BASES PACKED count="+r.kit.size());r.close();return true;});
        steps.add(new EnchantBatch(0));r.prepend(steps);
    }
    final class EnchantBatch implements Robot.Step {
        final int offset;
        EnchantBatch(int offset){this.offset=offset;}
        public boolean tick(){
            if(offset==r.plans.size())return true;
            int free=0;for(int i=0;i<36;i++)if(!r.protectedSlots.contains(i)&&r.mc.player.getInventory().getItem(i).isEmpty())free++;
            var remaining=r.plans.subList(offset,r.plans.size());int count=0,reserve=2;
            var needs=new LinkedHashMap<AnvilPlanner.Book,Integer>();
            for(int candidate=1;candidate<=remaining.size();candidate++){
                for(var b:remaining.get(candidate-1).books())needs.merge(b,1,Integer::sum);
                int newBooks=0,nextReserve=2;
                for(var e:needs.entrySet()){var b=e.getKey();java.util.function.Predicate<ItemStack> matches=s->Stacks.book(s,b.enchantment(),b.level(),b.penalty());newBooks+=Math.max(0,e.getValue()-r.inventoryCount(matches));if(r.available(matches)<e.getValue())nextReserve=4;}
                if(candidate+newBooks+nextReserve>free)break;
                count=candidate;reserve=nextReserve;
            }
            r.require(count>0,"附魔批次没有足够背包空间：需装备、全部附魔书和事务预留槽");
            var batch=r.plans.subList(offset,offset+count);var steps=new ArrayList<Robot.Step>();String batchState="PRODUCING BATCH "+offset+"+"+count;r.state=batchState;
            r.record("ENCHANT BATCH offset="+offset+" items="+count+" free="+free+" reserve="+reserve);
            steps.add(r.new Open(r.packing(),ShulkerBoxMenu.class));
            for(var p:batch)steps.add(()->{var s=r.mc.player.containerMenu.getSlot(p.target().slot()).getItem().copy();r.require(r.base(p.target().item()).test(s),"待附魔装备改变");r.prepend(List.of(r.new Move(p.target().slot(),-1,1,s)));return true;});
            steps.add(()->{r.close();return true;});
            var quantities=new LinkedHashMap<AnvilPlanner.Book,Integer>();for(var p:batch)for(var b:p.books())quantities.merge(b,1,Integer::sum);
            quantities.forEach((b,n)->steps.add(r.new Fetch(s->Stacks.book(s,b.enchantment(),b.level(),b.penalty()),n)));
            steps.add(()->{r.state=batchState;return true;});
            for(var p:batch)steps.add(()->{r.scheduleEnchantment(p);return true;});
            steps.add(r.new Open(r.packing(),ShulkerBoxMenu.class));
            for(var p:batch){steps.add(r.new Put(s->r.matches(s,p.target()),p.target().slot(),1));steps.add(()->{r.require(r.matches(r.mc.player.containerMenu.getSlot(p.target().slot()).getItem(),p.target()),"装箱核验失败");r.completed++;r.record("PACKED "+p.target().key());return true;});}
            steps.add(()->{r.close();return true;});steps.add(new EnchantBatch(offset+count));r.prepend(steps);return true;
        }
    }
}
