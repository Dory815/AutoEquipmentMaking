package io.github.autoequipmentmaking.client;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;

/** Reserve each observed base once, in the user's strict reuse priority order. */
final class BaseEquipmentPlan {
    enum Route { NETHERITE, GRIND_DIAMOND, DIAMOND, CRAFT }
    record Entry(Robot.Target target,Route route,ItemStack source) {
        String type(){return target.item().substring(target.item().indexOf('_')+1);}
        String diamond(){return "minecraft:diamond_"+type();}
    }
    record Input(ItemStack identity,int amount) {}
    final List<Entry> entries;
    final Map<String,Integer> materials;
    BaseEquipmentPlan(Robot r){
        var pool=new ArrayList<ItemStack>();
        for(var source:r.stock)pool.add(source.stack().copy());
        for(int i=0;i<36;i++)if(!r.protectedSlots.contains(i))pool.add(r.mc.player.getInventory().getItem(i).copy());
        var chosen=new ArrayList<Entry>();var budget=new LinkedHashMap<String,Integer>();
        if(!r.allowOldEquipment)pool.clear();
        for(var target:r.kit)if(target.item().startsWith("minecraft:netherite_")||target.item().startsWith("minecraft:diamond_")){
            String type=target.item().substring(target.item().indexOf('_')+1),diamond="minecraft:diamond_"+type;
            boolean netherite=target.item().startsWith("minecraft:netherite_");
            ItemStack selected=netherite?take(pool,s->Stacks.base(s,target.item())):ItemStack.EMPTY;Route route=Route.NETHERITE;
            if(selected.isEmpty()){route=Route.GRIND_DIAMOND;selected=take(pool,s->grindable(s,diamond));}
            if(selected.isEmpty()){route=Route.DIAMOND;selected=take(pool,s->Stacks.base(s,diamond));}
            if(selected.isEmpty()){
                route=Route.CRAFT;
                for(String row:Robot.pattern(type))for(char c:row.toCharArray())if(c!=' ')budget.merge(c=='D'?"minecraft:diamond":"minecraft:stick",1,Integer::sum);
            }
            if(netherite&&route!=Route.NETHERITE){budget.merge("minecraft:netherite_ingot",1,Integer::sum);budget.merge("minecraft:netherite_upgrade_smithing_template",1,Integer::sum);}
            chosen.add(new Entry(target,route,selected));
        }
        entries=List.copyOf(chosen);materials=Collections.unmodifiableMap(budget);
    }
    static boolean grindable(ItemStack stack,String id){
        return !stack.isEmpty()&&Stacks.id(stack).equals(id)&&stack.getDamageValue()==0&&!stack.has(DataComponents.CUSTOM_NAME)
            &&!Stacks.ench(stack,false).isEmpty()&&stack.getEnchantments().entrySet().stream().noneMatch(e->e.getKey().is(EnchantmentTags.CURSE));
    }
    static ItemStack take(List<ItemStack> pool,java.util.function.Predicate<ItemStack> predicate){
        for(var stack:pool)if(predicate.test(stack)){var selected=stack.copyWithCount(1);stack.shrink(1);return selected;}
        return ItemStack.EMPTY;
    }
    boolean needsGrinding(){return entries.stream().anyMatch(e->e.route()==Route.GRIND_DIAMOND);}
    List<Input> inputs(){
        var inputs=new ArrayList<Input>();
        for(var entry:entries)if(!entry.source().isEmpty()){
            int index=-1;for(int i=0;i<inputs.size();i++)if(ItemStack.isSameItemSameComponents(inputs.get(i).identity(),entry.source())){index=i;break;}
            if(index<0)inputs.add(new Input(entry.source().copy(),1));else{var old=inputs.get(index);inputs.set(index,new Input(old.identity(),old.amount()+1));}
        }
        return List.copyOf(inputs);
    }
    String summary(){
        return "BASE PLAN netherite="+count(Route.NETHERITE)+" grind="+count(Route.GRIND_DIAMOND)+" diamond="+count(Route.DIAMOND)+" craft="+count(Route.CRAFT)
            +" diamonds="+materials.getOrDefault("minecraft:diamond",0)+" sticks="+materials.getOrDefault("minecraft:stick",0)
            +" ingots="+materials.getOrDefault("minecraft:netherite_ingot",0)+" templates="+materials.getOrDefault("minecraft:netherite_upgrade_smithing_template",0);
    }
    long count(Route route){return entries.stream().filter(e->e.route()==route).count();}
}
