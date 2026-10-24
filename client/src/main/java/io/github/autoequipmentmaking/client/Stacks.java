package io.github.autoequipmentmaking.client;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.enchantment.ItemEnchantments;

final class Stacks {
    static String id(ItemStack s){return BuiltInRegistries.ITEM.getKey(s.getItem()).toString();}
    static Map<String,Integer> ench(ItemStack s,boolean book){
        var result=new TreeMap<String,Integer>();
        var v=s.getOrDefault(book?DataComponents.STORED_ENCHANTMENTS:DataComponents.ENCHANTMENTS,ItemEnchantments.EMPTY);
        for(var e:v.entrySet()) result.put(e.getKey().unwrapKey().orElseThrow().identifier().toString(),e.getIntValue());
        return result;
    }
    static boolean base(ItemStack s,String id){return !s.isEmpty()&&id(s).equals(id)&&s.getDamageValue()==0&&ench(s,false).isEmpty()&&!s.has(DataComponents.CUSTOM_NAME)&&penalty(s)==0;}
    static int penalty(ItemStack s){return s.getOrDefault(DataComponents.REPAIR_COST,0);}
    static boolean book(ItemStack s,String id,int level,int penalty){return s.is(Items.ENCHANTED_BOOK)&&ench(s,true).equals(Map.of(id,level))&&penalty(s)==penalty;}
    static int multiplier(String id,net.minecraft.client.Minecraft mc){
        var registry=mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        var value=registry.getValue(net.minecraft.resources.Identifier.parse(id));
        if(value==null)throw new IllegalStateException("未知附魔: "+id);
        return Math.max(1,value.getAnvilCost()/2);
    }
}
