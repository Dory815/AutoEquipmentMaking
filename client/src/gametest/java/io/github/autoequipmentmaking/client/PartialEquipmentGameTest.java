package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;

/** Real survival transactions on the old simple fixture; never supply final enchanted kits. */
public final class PartialEquipmentGameTest extends ProductionGameTest {
    static final BlockPos A=new BlockPos(-2,65,2),B=new BlockPos(-4,65,2),RED=new BlockPos(-2,65,4),BARREL=new BlockPos(-4,65,4),GRIND=new BlockPos(2,65,-3);
    final String scenario=Optional.ofNullable(System.getenv("AEM_PARTIAL_SCENARIO")).orElse("mixed");
    final Map<BlockPos,List<ItemStack>> expected=new LinkedHashMap<>();
    boolean allNetherite(){return scenario.equals("all-netherite");}
    boolean allDiamond(){return scenario.equals("all-enchanted-diamond");}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static ItemStack item(String id){return new ItemStack(Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.parse(id))));}
    static ItemStack enchanted(ServerLevel level,String id,Map<String,Integer> values){
        var result=item(id);var ench=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for(var e:values.entrySet())ench.set(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(e.getKey())).orElseThrow(),e.getValue());
        result.set(DataComponents.ENCHANTMENTS,ench.toImmutable());result.set(DataComponents.REPAIR_COST,7);return result;
    }
    @Override ItemStack[] materials(){
        var raw=super.materials();if(allNetherite())for(int i=0;i<4;i++)raw[i]=ItemStack.EMPTY;
        if(allDiamond()){raw[0]=ItemStack.EMPTY;raw[1]=ItemStack.EMPTY;}return raw;
    }
    @Override protected void fixtures(ServerLevel level,ServerPlayer player){
        for(var p:List.of(A,B))level.setBlockAndUpdate(p,p.equals(A)?Blocks.SHULKER_BOX.defaultBlockState():Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(RED,Blocks.DYED_SHULKER_BOX.red().defaultBlockState());level.setBlockAndUpdate(BARREL,Blocks.BARREL.defaultBlockState());
        ((Container)level.getBlockEntity(RED)).setItem(0,item("minecraft:netherite_boots"));
        ((Container)level.getBlockEntity(BARREL)).setItem(0,item("minecraft:netherite_axe"));
        player.getInventory().setItem(0,item("minecraft:netherite_boots"));player.getInventory().setItem(1,new ItemStack(Items.DIAMOND,7));
        var a=(Container)level.getBlockEntity(A);var b=(Container)level.getBlockEntity(B);var consumedA=new HashSet<Integer>();var consumedB=new HashSet<Integer>();
        if(allNetherite()||allDiamond()){
            int i=0;for(var target:AutoEquipmentClient.robot.kitTemplate)if(target.item().startsWith("minecraft:netherite_")){
                String id=allNetherite()?target.item():target.item().replace("netherite_","diamond_");
                a.setItem(i,allNetherite()?item(id):enchanted(level,id,Map.of("minecraft:unbreaking",1)));consumedA.add(i++);
            }
            if(allNetherite()){level.setBlockAndUpdate(new BlockPos(2,65,0),Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(new BlockPos(4,65,0),Blocks.AIR.defaultBlockState());}
            else {
                level.setBlockAndUpdate(GRIND,Blocks.GRINDSTONE.defaultBlockState());
                var menu=new net.minecraft.world.inventory.GrindstoneMenu(77,player.getInventory(),net.minecraft.world.inventory.ContainerLevelAccess.create(level,GRIND));
                var cursed=enchanted(level,"minecraft:diamond_boots",Map.of("minecraft:binding_curse",1,"minecraft:vanishing_curse",1,"minecraft:protection",1));
                menu.getSlot(0).set(cursed);
                check(Stacks.ench(menu.getSlot(2).getItem(),false).equals(Map.of("minecraft:binding_curse",1,"minecraft:vanishing_curse",1)),"26.2 grindstone did not retain both curses while removing ordinary enchantment");
                check(!BaseEquipmentPlan.grindable(cursed,"minecraft:diamond_boots"),"Cursed input entered half-equipment plan");
                menu.getSlot(0).set(ItemStack.EMPTY);
            }
        }else{
            String[] ready={"netherite_helmet","netherite_sword","netherite_hoe"};for(int i=0;i<ready.length;i++)a.setItem(i,item("minecraft:"+ready[i]));
            a.setItem(3,enchanted(level,"minecraft:diamond_chestplate",Map.of("minecraft:protection",1,"minecraft:unbreaking",1)));
            a.setItem(4,enchanted(level,"minecraft:diamond_sword",Map.of("minecraft:sharpness",1)));
            a.setItem(5,enchanted(level,"minecraft:diamond_pickaxe",Map.of("minecraft:efficiency",1)));
            consumedA.addAll(Set.of(0,1,2,3,4,5));
            b.setItem(0,item("minecraft:diamond_leggings"));b.setItem(1,item("minecraft:diamond_pickaxe"));b.setItem(2,item("minecraft:diamond_shovel"));consumedB.addAll(Set.of(0,1,2));
            b.setItem(3,item("minecraft:diamond_helmet"));b.setItem(4,enchanted(level,"minecraft:diamond_helmet",Map.of("minecraft:protection",1)));
            b.setItem(5,item("minecraft:diamond_chestplate"));b.setItem(6,item("minecraft:diamond_sword"));
            b.setItem(7,enchanted(level,"minecraft:diamond_boots",Map.of("minecraft:binding_curse",1,"minecraft:protection",1)));
            var damaged=enchanted(level,"minecraft:diamond_axe",Map.of("minecraft:efficiency",1));damaged.setDamageValue(1);b.setItem(8,damaged);
            var named=item("minecraft:diamond_axe");named.set(DataComponents.CUSTOM_NAME,Component.literal("PRIVATE NAMED AXE"));b.setItem(9,named);
            b.setItem(10,enchanted(level,"minecraft:netherite_chestplate",Map.of("minecraft:protection",1)));
            var damagedNetherite=item("minecraft:netherite_boots");damagedNetherite.setDamageValue(1);b.setItem(11,damagedNetherite);
            // The initial absence tests preflight: no source may be consumed before good.
        }
        for(var p:List.of(A,B,RED,BARREL)){
            var container=(Container)level.getBlockEntity(p);var contents=new ArrayList<ItemStack>();
            for(int i=0;i<container.getContainerSize();i++)contents.add((p.equals(A)&&consumedA.contains(i)||p.equals(B)&&consumedB.contains(i))?ItemStack.EMPTY:container.getItem(i).copy());
            expected.put(p,contents);
        }
    }
    @Override protected void afterStart(ClientGameTestContext context,TestSingleplayerContext world){
        if(allNetherite()||allDiamond())return;
        context.waitFor(mc->AutoEquipmentClient.robot.awaitingGood,1200);
        context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.waitingReason.contains("砂轮"),"Missing grinder was not diagnosed: "+r.waitingReason);check(!r.uncertain,"Missing grinder became uncertain");});
        world.getServer().runOnServer(server->{
            var l=server.overworld();var raw=(Container)l.getBlockEntity(new BlockPos(0,65,0));check(raw.getItem(0).getCount()==40&&raw.getItem(1).getCount()==12,"Materials consumed before grinder preflight");
            for(int i=0;i<6;i++)check(!((Container)l.getBlockEntity(A)).getItem(i).isEmpty(),"Partial gear consumed before grinder preflight");
            l.setBlockAndUpdate(GRIND,Blocks.GRINDSTONE.defaultBlockState());
        });
        context.waitTicks(20);context.runOnClient(mc->check(AutoEquipmentClient.robot.awaitingGood,"Grinder appearance bypassed good gate"));
        sendGood(world);context.waitFor(mc->AutoEquipmentClient.robot.running,200);
    }
    @Override protected void verifyConsumedSources(ServerLevel level){
        var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));
        int[] counts=allNetherite()?new int[]{0,0,0,0}:allDiamond()?new int[]{0,0,0,0}:new int[]{33,10,3,3};
        for(int i=0;i<4;i++)check(raw.getItem(i).getCount()==counts[i],"Wrong raw consumption slot="+i+" actual="+raw.getItem(i));
        for(int i=4;i<7;i++)check(raw.getItem(i).isEmpty(),"Non-netherite base was not consumed");
        for(int i=0;i<expectedBooks();i++)check(((Container)level.getBlockEntity(bookBoxes()[i/27])).getItem(i%27).isEmpty(),"Expected book not consumed");
        for(var e:expected.entrySet())for(int i=0;i<e.getValue().size();i++)check(ItemStack.matches(((Container)level.getBlockEntity(e.getKey())).getItem(i),e.getValue().get(i)),"Selected/unselected gear mismatch "+e.getKey()+" slot="+i);
    }
    @Override protected void verifyExtra(ServerLevel level,ServerPlayer player){
        check(ItemStack.matches(player.getInventory().getItem(0),item("minecraft:netherite_boots")),"Protected netherite boots consumed");check(player.getInventory().getItem(1).is(Items.DIAMOND)&&player.getInventory().getItem(1).getCount()==7,"Protected diamonds consumed");
        try{
            String task=AutoEquipmentClient.robot.task;
            var events=Files.readAllLines(AutoEquipmentClient.robot.journal).stream().map(line->JsonParser.parseString(line).getAsJsonObject()).filter(row->row.has("task")&&!row.get("task").isJsonNull()&&row.get("task").getAsString().equals(task)).map(row->row.get("event").getAsString()).toList();
            String plan=allNetherite()?"BASE PLAN netherite=11 grind=0 diamond=0 craft=0 diamonds=0 sticks=0 ingots=0 templates=0":allDiamond()?"BASE PLAN netherite=0 grind=11 diamond=0 craft=0 diamonds=0 sticks=0 ingots=11 templates=11":"BASE PLAN netherite=3 grind=3 diamond=3 craft=2 diamonds=7 sticks=2 ingots=8 templates=8";
            check(events.contains(plan),"Wrong priority allocation: "+events.stream().filter(e->e.startsWith("BASE PLAN")).toList());
            long grinds=events.stream().filter(e->e.startsWith("GRIND CONFIRMED ")).count();check(grinds==(allNetherite()?0:allDiamond()?11:3),"Wrong grind transactions "+grinds);
            if(allNetherite())check(events.stream().noneMatch(e->e.startsWith("OPEN CraftingMenu")||e.startsWith("OPEN SmithingMenu")||e.startsWith("OPEN GrindstoneMenu")),"Reused netherite opened unnecessary workstation");
            if(allDiamond())check(events.stream().noneMatch(e->e.startsWith("OPEN CraftingMenu")),"Enchanted diamond fallback crafted new bases");
            if(!allNetherite())check(events.stream().filter(e->e.startsWith("OPEN GrindstoneMenu")).count()==1,"Grinding revisited workstation");
            if(!allNetherite()&&!allDiamond())check(AutoEquipmentClient.robot.restockAcks==1,"Missing-grinder good gate not exercised");
            var result=new LinkedHashMap<String,Object>();result.put("scenario",scenario);result.put("task",task);result.put("basePlan",plan);result.put("grinds",grinds);result.put("items",14);result.put("kits",1);result.put("anvilLevelCost",319);result.put("privateSlotsUnchanged",true);result.put("lowerPriorityAndExcludedSourcesUnchanged",true);result.put("formalServerConnected",false);
            if(allDiamond())result.put("actual26_2GrindstoneMenuRetainsBothCurses",true);
            Files.writeString(Path.of("partial-equipment-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
