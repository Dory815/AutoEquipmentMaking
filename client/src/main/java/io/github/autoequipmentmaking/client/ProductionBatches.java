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
        if(r.bulkMode){scheduleBulk();return;}
        var steps=new ArrayList<Robot.Step>();r.state="PRODUCING BASES";
        var plan=Objects.requireNonNull(r.basePlan,"Base equipment plan missing");
        for(var e:plan.materials.entrySet())steps.add(r.new Fetch(r.base(e.getKey()),e.getValue()));
        // Fetch originals before grinding so a cleaned output cannot satisfy a
        // separate plain-diamond reservation for another target of the same type.
        for(var input:plan.inputs())steps.add(r.new Fetch(s->ItemStack.isSameItemSameComponents(s,input.identity()),input.amount()));
        steps.addAll(Grinding.steps(r,plan));
        if(plan.count(BaseEquipmentPlan.Route.CRAFT)>0)steps.add(r.new Open(r.cfg.station("crafting"),CraftingMenu.class));
        for(var entry:plan.entries)if(entry.route()==BaseEquipmentPlan.Route.CRAFT){
            String type=entry.type();var pattern=Robot.pattern(type);
            for(int y=0;y<3;y++)for(int x=0;x<3;x++){char c=pattern[y].charAt(x);if(c!=' ')steps.add(r.new Put(r.base(c=='D'?"minecraft:diamond":"minecraft:stick"),1+y*3+x,1));}
            steps.add(r.new TakeResult(0,r.base("minecraft:diamond_"+type),-1));
        }
        steps.add(()->{r.record("CRAFT ALL COMPLETE");r.close();return true;});
        if(plan.entries.stream().anyMatch(e->e.route()!=BaseEquipmentPlan.Route.NETHERITE))steps.add(r.new Open(r.cfg.station("smithing"),SmithingMenu.class));
        for(var entry:plan.entries)if(entry.route()!=BaseEquipmentPlan.Route.NETHERITE){
            String type=entry.type();
            steps.add(r.new Put(r.base("minecraft:netherite_upgrade_smithing_template"),0,1));
            steps.add(r.new Put(r.base("minecraft:diamond_"+type),1,1));steps.add(r.new Put(r.base("minecraft:netherite_ingot"),2,1));
            steps.add(r.new TakeResult(3,r.base(entry.target().item()),-1));
        }
        steps.add(()->{r.record("SMITH ALL COMPLETE");r.close();return true;});
        for(var t:r.kit)if(!t.item().startsWith("minecraft:netherite_"))steps.add(r.new Fetch(r.base(t.item()),1));
        steps.add(r.new Open(r.packing(),ShulkerBoxMenu.class));
        for(var t:r.kit)steps.add(r.new Put(r.base(t.item()),t.slot(),1));
        steps.add(()->{for(var t:r.kit)r.require(r.base(t.item()).test(r.mc.player.containerMenu.getSlot(t.slot()).getItem()),"基础装备装箱不符: "+t.key());r.record("BASES PACKED count="+r.kit.size());r.close();return true;});
        steps.add(new EnchantBatch(0));r.prepend(steps);
    }
    /** Six bases plus materials and transaction reserves fit even the minimum backpack. */
    void scheduleBulk(){r.prepend(List.of(new BaseBatch(0)));}
    final class BaseBatch implements Robot.Step {
        final int offset;BaseBatch(int offset){this.offset=offset;}
        public boolean tick(){
            if(offset==r.kit.size()){r.record("BASES PACKED count="+r.kit.size());r.prepend(List.of(new EnchantBatch(0)));return true;}
            int free=0;for(int i=0;i<36;i++)if(!r.protectedSlots.contains(i)&&r.mc.player.getInventory().getItem(i).isEmpty())free++;
            int count=Math.min(Math.min(6,free-6),r.kit.size()-offset);r.require(count>0,"批量底材制作没有足够背包空间");
            var targets=r.kit.subList(offset,offset+count);var entries=r.basePlan.entries.stream().filter(e->targets.contains(e.target())).toList();
            var steps=new ArrayList<Robot.Step>();var amounts=new LinkedHashMap<String,Integer>();r.state="PRODUCING BASES";
            for(var e:entries){
                if(e.route()==BaseEquipmentPlan.Route.CRAFT)for(String row:Robot.pattern(e.type()))for(char c:row.toCharArray())if(c!=' ')amounts.merge(c=='D'?"minecraft:diamond":"minecraft:stick",1,Integer::sum);
                if(e.target().item().startsWith("minecraft:netherite_")&&e.route()!=BaseEquipmentPlan.Route.NETHERITE){amounts.merge("minecraft:netherite_ingot",1,Integer::sum);amounts.merge("minecraft:netherite_upgrade_smithing_template",1,Integer::sum);}
            }
            amounts.forEach((id,n)->steps.add(r.new Fetch(r.base(id),n)));
            var inputs=new ArrayList<BaseEquipmentPlan.Input>();
            for(var e:entries)if(!e.source().isEmpty()){
                int index=-1;for(int i=0;i<inputs.size();i++)if(ItemStack.isSameItemSameComponents(inputs.get(i).identity(),e.source())){index=i;break;}
                if(index<0)inputs.add(new BaseEquipmentPlan.Input(e.source(),1));else{var old=inputs.get(index);inputs.set(index,new BaseEquipmentPlan.Input(old.identity(),old.amount()+1));}
            }
            for(var input:inputs)steps.add(r.new Fetch(s->ItemStack.isSameItemSameComponents(s,input.identity()),input.amount()));
            steps.addAll(Grinding.steps(r,entries));
            if(entries.stream().anyMatch(e->e.route()==BaseEquipmentPlan.Route.CRAFT))steps.add(r.new Open(r.cfg.station("crafting"),CraftingMenu.class));
            for(var entry:entries)if(entry.route()==BaseEquipmentPlan.Route.CRAFT){var pattern=Robot.pattern(entry.type());
                for(int y=0;y<3;y++)for(int x=0;x<3;x++){char c=pattern[y].charAt(x);if(c!=' ')steps.add(r.new Put(r.base(c=='D'?"minecraft:diamond":"minecraft:stick"),1+y*3+x,1));}
                steps.add(r.new TakeResult(0,r.base(entry.diamond()),-1));
            }
            var upgrades=entries.stream().filter(e->e.target().item().startsWith("minecraft:netherite_")&&e.route()!=BaseEquipmentPlan.Route.NETHERITE).toList();
            if(!upgrades.isEmpty())steps.add(r.new Open(r.cfg.station("smithing"),SmithingMenu.class));
            for(var e:upgrades){steps.add(r.new Put(r.base("minecraft:netherite_upgrade_smithing_template"),0,1));steps.add(r.new Put(r.base(e.diamond()),1,1));steps.add(r.new Put(r.base("minecraft:netherite_ingot"),2,1));steps.add(r.new TakeResult(3,r.base(e.target().item()),-1));}
            var direct=new LinkedHashMap<String,Integer>();for(var target:targets)if(entries.stream().noneMatch(e->e.target().equals(target)))direct.merge(target.item(),1,Integer::sum);
            direct.forEach((id,n)->steps.add(r.new Fetch(r.base(id),n)));
            steps.add(r.new Open(r.packing(),ShulkerBoxMenu.class));
            for(var target:targets){steps.add(r.new Put(r.base(target.item()),target.slot(),1));steps.add(()->{r.require(r.base(target.item()).test(r.mc.player.containerMenu.getSlot(target.slot()).getItem()),"批量底材装箱不符");return true;});}
            steps.add(()->{r.record("BULK BASE BATCH offset="+offset+" items="+count);r.close();return true;});steps.add(new BaseBatch(offset+count));r.prepend(steps);return true;
        }
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
