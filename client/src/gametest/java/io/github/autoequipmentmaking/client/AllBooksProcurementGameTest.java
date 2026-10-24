package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.phys.AABB;

/** No source enchanted books: all purchases must precede diamond production. */
public final class AllBooksProcurementGameTest extends ProductionGameTest {
    final Map<String,Integer> required=new TreeMap<>();
    final Map<String,ItemStack> products=new TreeMap<>();
    int bookCount;
    static String key(ItemStack stack){return stack.is(Items.ENCHANTED_BOOK)?Stacks.ench(stack,true).toString():Stacks.id(stack);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override protected void fixtures(ServerLevel level,ServerPlayer player){
        for(var pos:bookBoxes()){
            var source=(Container)level.getBlockEntity(pos);
            for(int slot=0;slot<source.getContainerSize();slot++){
                var book=source.getItem(slot);
                if(!book.isEmpty()){
                    check(book.is(Items.ENCHANTED_BOOK),"Unexpected source fixture");
                    required.merge(key(book),book.getCount(),Integer::sum);
                    products.put(key(book),book.copy());bookCount+=book.getCount();
                }
                source.setItem(slot,ItemStack.EMPTY);
            }
            check(source.isEmpty(),"Enchanted book source not cleared");
        }
        check(bookCount==55,"Unexpected kit book quantity: "+bookCount);
        for(var item:List.of(Items.BOW,Items.SHIELD)){
            var stack=new ItemStack(item);required.put(key(stack),1);products.put(key(stack),stack);
        }
        var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));
        raw.setItem(5,new ItemStack(Items.BOOK,bookCount));
        raw.setItem(6,new ItemStack(Items.EMERALD_BLOCK,64));
        raw.setItem(7,new ItemStack(Items.ANVIL,16));
        var locations=List.of(new double[]{10.5,65,3.5},new double[]{10.5,65,-5.5},new double[]{-5.5,65,-5.5});
        var entries=new ArrayList<>(products.values());
        for(int merchant=0;merchant<3;merchant++){
            var v=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);var pos=locations.get(merchant);
            v.setPos(pos[0],pos[1],pos[2]);v.setNoAi(true);v.setInvulnerable(true);
            v.setVillagerData(v.getVillagerData().withProfession(level.registryAccess(),VillagerProfession.LIBRARIAN).withLevel(5));
            var offers=new MerchantOffers();
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD),new ItemStack(Items.ARROW,8),1000,0,0));
            for(int i=merchant;i<entries.size();i+=3){
                var out=entries.get(i);
                int price=out.is(Items.ENCHANTED_BOOK)?10:out.is(Items.BOW)?4:5;
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,price),out.is(Items.ENCHANTED_BOOK)?Optional.of(new ItemCost(Items.BOOK)):Optional.empty(),out.copy(),1000,0,0));
            }
            v.setOffers(offers);level.addFreshEntity(v);
        }
        player.getInventory().setItem(0,new ItemStack(Items.EMERALD,7));
        player.getInventory().setItem(1,new ItemStack(Items.BOOK,3));
        player.getFoodData().setFoodLevel(20);player.getFoodData().setSaturation(20);
        for(var pos:bookBoxes())check(((Container)level.getBlockEntity(pos)).isEmpty(),"Book fixture leaked into raw stock");
        for(int i=0;i<36;i++)check(!player.getInventory().getItem(i).is(Items.ENCHANTED_BOOK),"Initial player has an enchanted book");
    }
    @Override protected void verifyConsumedSources(ServerLevel level){
        var raw=(Container)level.getBlockEntity(new BlockPos(0,65,0));
        for(int i=0;i<6;i++)check(raw.getItem(i).isEmpty(),"Unconsumed raw material / plain books: "+i);
        check(raw.getItem(6).is(Items.EMERALD_BLOCK)&&raw.getItem(6).getCount()==1,"Expected 63 blocks consumed for 559 emeralds");
        for(var pos:bookBoxes()){
            var source=(Container)level.getBlockEntity(pos);
            for(int i=0;i<source.getContainerSize();i++)check(!source.getItem(i).is(Items.ENCHANTED_BOOK),"Purchased book left in source");
        }
    }
    @Override protected void verifyExtra(ServerLevel level,ServerPlayer player){
        check(player.getInventory().getItem(0).is(Items.EMERALD)&&player.getInventory().getItem(0).getCount()==7,"Private emeralds changed");
        check(player.getInventory().getItem(1).is(Items.BOOK)&&player.getInventory().getItem(1).getCount()==3,"Private books changed");
        var bought=new TreeMap<String,Integer>();int emeralds=0,books=0;
        var villagers=level.getEntitiesOfClass(Villager.class,new AABB(-9,64,-9,14,70,14));
        check(villagers.size()==3,"Wrong fixture merchant count");
        for(var v:villagers)for(var offer:v.getOffers()){
            if(offer.getUses()==0)continue;
            check(!offer.getResult().is(Items.ARROW),"Unrelated trade purchased");
            check(!offer.isOutOfStock(),"Fixture entered cooldown");
            bought.merge(key(offer.getResult()),offer.getUses(),Integer::sum);
            emeralds+=offer.getUses()*offer.getBaseCostA().count();
            if(offer.getCostB().is(Items.BOOK))books+=offer.getUses()*offer.getCostB().getCount();
        }
        check(bought.equals(required),"Wrong purchase quantities: "+bought+" expected "+required);
        check(emeralds==bookCount*10+9&&books==bookCount,"Payment totals differ: "+emeralds+" / "+books);
        try{
            var r=AutoEquipmentClient.robot;
            var rows=Files.readAllLines(r.journal).stream().map(l->JsonParser.parseString(l).getAsJsonObject()).filter(o->o.get("task").getAsString().equals(r.task)).toList();
            var events=rows.stream().map(o->o.get("event").getAsString()).toList();
            int ready=events.indexOf("PRETRADE COMPLETE all kit deficits filled before diamond crafting");
            int craft=events.indexOf("CRAFT ALL COMPLETE"),smith=events.indexOf("SMITH ALL COMPLETE");
            check(ready>=0&&craft>ready&&smith>craft,"Wrong purchase / craft / smith ordering");
            check(events.stream().filter(e->e.startsWith("TRADE BUY CONFIRMED")).count()==bookCount+2,"Wrong transaction count");
            check(events.stream().filter(e->e.startsWith("TRADE SELECT INTENT")).count()<bookCount,"Every purchase still reselects and refills the offer");
            check(events.stream().anyMatch(e->e.startsWith("TRADE SESSION COMPLETE purchases=")&&Integer.parseInt(e.split("purchases=")[1])>=3),"No repeated purchases shared a funded merchant menu");
            for(var row:rows)if(row.get("event").getAsString().startsWith("TRADE BUY INTENT")){
                var privateCounts=new HashMap<Integer,Integer>();for(var held:row.getAsJsonArray("inventory")){var item=held.getAsJsonObject();privateCounts.put(item.get("slot").getAsInt(),item.getAsJsonObject("stack").get("count").getAsInt());}
                check(privateCounts.getOrDefault(0,0)==7&&privateCounts.getOrDefault(1,0)==3,"Purchase began before private funds were restored");
            }
            check(events.subList(ready,events.size()).stream().noneMatch(e->e.startsWith("TRADE BUY INTENT")||e.equals("EMERALD BLOCK UNPACKED")),"Purchasing continued after diamond production began");
            for(var row:rows.subList(0,ready))for(var held:row.getAsJsonArray("inventory")){
                var id=held.getAsJsonObject().getAsJsonObject("stack").get("id").getAsString();
                check(!id.equals("minecraft:diamond")&&!id.equals("minecraft:stick")&&!id.startsWith("minecraft:diamond_")&&!id.startsWith("minecraft:netherite_"),"Production material taken before full procurement: "+id);
            }
            check(events.stream().anyMatch(e->e.startsWith("PRETRADE STORED")),"No capacity spill exercised");
            check(events.stream().anyMatch(e->e.startsWith("TRADE SCAN villagers=3 ")&&e.contains("observed=3 opened=0")),"Discovery opened merchant menus instead of observing server snapshots");
            check(events.stream().noneMatch(e->e.startsWith("TRADE COOLDOWN CHECK")||e.startsWith("RESTOCK ACK")||e.startsWith("PAUSED")),"Unexpected cooldown, restock or pause");
            var plans=new ArrayList<Integer>();for(int i=0;i<ready;i++)if(events.get(i).startsWith("PRETRADE PLAN "))plans.add(i);
            check(plans.size()>=2&&plans.size()<bookCount/2,"Procurement was not batched: "+plans.size());
            int total=0;
            for(int batch=0;batch<plans.size();batch++){
                int from=plans.get(batch),to=batch+1<plans.size()?plans.get(batch+1):ready;
                var section=events.subList(from,to);int first=-1,last=-1;
                for(int i=0;i<section.size();i++){if(section.get(i).startsWith("TRADE BUY INTENT")&&first<0)first=i;if(section.get(i).startsWith("TRADE BUY CONFIRMED"))last=i;}
                check(first>=0&&last>=first,"Empty procurement batch");
                int planned=Integer.parseInt(section.getFirst().split("products=")[1].split(" ")[0]);
                long actual=section.stream().filter(e->e.startsWith("TRADE BUY CONFIRMED")).count();
                check(actual==planned,"Purchase batch incomplete: "+actual+" / "+planned);total+=planned;
                check(batch==plans.size()-1||planned>2,"Residual slots caused a tiny non-final procurement batch: "+planned);
                check(section.subList(first,last+1).stream().noneMatch(e->e.startsWith("OPEN AbstractContainerMenu")||e.startsWith("OPEN ChestMenu")||e.startsWith("OPEN ShulkerBoxMenu")||e.startsWith("OPEN CraftingMenu")||e.equals("EMERALD BLOCK UNPACKED")),"Returned for currency/books between purchases in batch "+batch);
            }
            check(total==bookCount+2,"Wrong total batch products");
            check(events.subList(ready,craft).stream().filter(e->e.startsWith("OPEN CraftingMenu")).count()==1,"Diamond crafting revisited workbench");
            check(events.subList(craft,smith).stream().filter(e->e.startsWith("OPEN SmithingMenu")).count()==1,"Smithing revisited workstation");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
