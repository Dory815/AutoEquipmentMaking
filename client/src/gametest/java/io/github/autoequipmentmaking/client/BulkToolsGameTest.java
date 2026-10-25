package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;

/** Survival acceptance in a disposable clone; only supplies and raw bases are fixtures. */
public final class BulkToolsGameTest implements FabricClientGameTest {
    static final BlockPos MIN=TradingHallNavigationGameTest.MIN,MAX=TradingHallNavigationGameTest.MAX,OUTPUT=TradingHallNavigationGameTest.OUTPUT;
    static final BlockPos BOX=new BlockPos(1069,57,20),SECOND=new BlockPos(1058,57,21);
    final String scenario=System.getenv().getOrDefault("AEM_BULK_SCENARIO","netherite");
    final Map<String,Object> result=new LinkedHashMap<>();
    final List<UUID> vendors=new ArrayList<>();
    final Map<UUID,MerchantOffers> observedOffers=new LinkedHashMap<>();
    final Map<BlockPos,List<ItemStack>> preserved=new LinkedHashMap<>();
    long journalStart;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    boolean trade(){return scenario.equals("trade");}
    boolean newcomer(){return scenario.equals("newcomer");}
    int boxes(){return scenario.equals("elytra")?2:1;}
    RobotSettings.BulkItem item(){return scenario.equals("diamond")?RobotSettings.BulkItem.DIAMOND_PICKAXE:scenario.equals("elytra")?RobotSettings.BulkItem.ELYTRA:RobotSettings.BulkItem.NETHERITE_PICKAXE;}
    static ItemStack book(ServerLevel l,String id,int level){var b=new ItemStack(Items.ENCHANTED_BOOK);var e=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);e.set(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(id)).orElseThrow(),level);b.set(DataComponents.STORED_ENCHANTMENTS,e.toImmutable());return b;}
    @Override public void runTest(ClientGameTestContext context){
        Path path=Path.of(Objects.requireNonNull(System.getenv("AEM_HALL_WORLD"))).toAbsolutePath();check(path.toString().contains("clientGameTest")&&!path.toString().contains("test-baselines"),"Refuse baseline");
        result.put("scenario",scenario);result.put("complete",false);
        try(var world=new TestWorldSaveImpl(context,path).open()){
            world.getServer().runOnServer(server->{
                var l=server.getLevel(Level.END);for(int x=65;x<=69;x++)for(int z=0;z<=2;z++)l.setChunkForced(x,z,true);
                var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.DIAMOND,7));p.getInventory().setItem(1,new ItemStack(Items.BOOK,3));p.experienceLevel=0;p.giveExperienceLevels(10000);p.getFoodData().setFoodLevel(20);p.teleportTo(l,1068.5,57,21.5,Set.of(),0,0,true);
                setup(l);
            });
            context.waitFor(mc->mc.level!=null&&mc.level.dimension()==Level.END,400);context.waitTicks(160);
            if(!trade())verifySettingsGui(context);
            context.runOnClient(mc->{
                try{var g=Class.forName("fi.dy.masa.minihud.config.Configs$Generic");Object sync=g.getField("ENTITY_DATA_SYNC").get(null);sync.getClass().getMethod("setBooleanValue",boolean.class).invoke(sync,true);}catch(Exception e){throw new IllegalStateException(e);}
                var r=AutoEquipmentClient.robot;var c=r.cfg.root;c.addProperty("serverKey","singleplayer");c.addProperty("dimension",r.dimension());var b=new JsonObject();b.add("min",WorkConfig.pos(MIN));b.add("max",WorkConfig.pos(MAX));c.add("platformBounds",b);
                var ws=c.getAsJsonObject("workstations");for(var e:Map.of("crafting",new BlockPos(1072,57,24),"smithing",new BlockPos(1071,57,24),"grindstones",new BlockPos(1079,56,29)).entrySet()){var a=new JsonArray();a.add(WorkConfig.pos(e.getValue()));ws.add(e.getKey(),a);}
                var anvils=new JsonArray();for(int y=57;y<=62;y++)anvils.add(WorkConfig.pos(new BlockPos(1067,y,24)));ws.add("anvils",anvils);c.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(OUTPUT));c.getAsJsonObject("notifications").addProperty("chatEnabled",trade());c.getAsJsonObject("trading").addProperty("enabled",true);
                var s=RobotSettings.INSTANCE;s.workMode.setOptionListValue(newcomer()||trade()?RobotSettings.WorkMode.NEWCOMER:RobotSettings.WorkMode.BULK);s.bulkItem.setOptionListValue(item());s.bulkQuantity.setIntegerValue(boxes());s.bulkOutputBoxes.setStrings(List.of("minecraft:yellow_shulker_box"));s.outputBoxes.setStrings(List.of("minecraft:light_blue_shulker_box"));s.quantityMode.setOptionListValue(RobotSettings.QuantityMode.MANUAL);s.quantity.setIntegerValue(1);s.useOldEquipment.setBooleanValue(!newcomer());s.scanLength.setIntegerValue(51);s.scanWidth.setIntegerValue(51);s.scanHeight.setIntegerValue(15);
                for(var option:List.of(s.swiftSneak,s.soulSpeed,s.elytra,s.shield,s.trading,s.spareAnvils))option.setBooleanValue(true);
                s.save();s.bulkQuantity.setIntegerValue(9);s.load();check(s.bulkQuantity.getIntegerValue()==boxes(),"New settings reload failed");
                mc.gui.setScreen(null);try{journalStart=Files.exists(r.journal)?Files.size(r.journal):0;}catch(Exception e){throw new IllegalStateException(e);}r.start();check(r.running,"Start failed");
                if(newcomer()){
                    // Exercise both choices against snapshots of the actual preserved fixture gear.
                    for(var entry:preserved.entrySet())for(int i=0;i<entry.getValue().size();i++)if(!entry.getValue().get(i).isEmpty())r.stock.add(new Robot.Source(entry.getKey(),i,entry.getValue().get(i).copy()));
                    r.allowOldEquipment=true;var reuse=new BaseEquipmentPlan(r);check(reuse.count(BaseEquipmentPlan.Route.NETHERITE)==11&&reuse.materials.isEmpty(),"Enabled reuse did not reserve existing newcomer gear");
                    r.allowOldEquipment=false;var raw=new BaseEquipmentPlan(r);check(raw.count(BaseEquipmentPlan.Route.CRAFT)==11&&raw.materials.get("minecraft:diamond")==40,"Disabled reuse still chose old newcomer gear");
                    r.stock.clear();result.put("reuseToggleBothPlanningStates",true);
                }
                if(trade()){r.queue.clear();r.queue.add(r.new Scan());r.queue.add(()->{observedOffers.forEach(r.trading::remember);r.trading.scanned=true;return true;});r.queue.add(r.trading.new Currency("minecraft:emerald",6));r.queue.add(r.trading.new Currency("minecraft:book",6));r.queue.add(r.trading.new Acquire(v->Stacks.book(v,"minecraft:unbreaking",3,0),3));}
            });
            if(trade())runTrades(context,world);
            else {
                context.waitFor(mc->{var r=AutoEquipmentClient.robot;check(!r.awaitingGood,"Unexpected good: "+r.waitingReason);check(r.running||r.state.equals("COMPLETED"),"Stopped: "+r.state);return r.state.equals("COMPLETED");},180000);
                world.getServer().runOnServer(server->{var l=server.getLevel(Level.END);var output=(Container)l.getBlockEntity(OUTPUT);int n=0;for(int i=0;i<output.getContainerSize();i++){var box=output.getItem(i);if(box.isEmpty())continue;check(AutoEquipmentClient.robot.validBox(box),"Invalid delivered box");n++;}check(n==boxes(),"Wrong boxes "+n);check(AutoEquipmentClient.robot.completed==(newcomer()?14:27*boxes()),"Wrong item count");
                    for(var e:preserved.entrySet())for(int i=0;i<e.getValue().size();i++)check(ItemStack.matches(((Container)l.getBlockEntity(e.getKey())).getItem(i),e.getValue().get(i)),"Unselected gear changed");
                    result.put("deliveredBoxes",n);result.put("items",AutoEquipmentClient.robot.completed);result.put("levelsSpent",10000-server.getPlayerList().getPlayers().getFirst().experienceLevel);
                });
            }
            world.getServer().runOnServer(server->{var inv=server.getPlayerList().getPlayers().getFirst().getInventory();check(inv.getItem(0).is(Items.DIAMOND)&&inv.getItem(0).getCount()==7&&inv.getItem(1).is(Items.BOOK)&&inv.getItem(1).getCount()==3,"Private inventory changed");});
            context.runOnClient(mc->{result.put("task",AutoEquipmentClient.robot.task);result.put("events",events());});
            result.put("complete",true);result.put("formalServerConnected",false);context.takeScreenshot("v13-"+scenario+"-complete");
        }finally{try{Files.writeString(Path.of("v13-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new IllegalStateException(e);}}
    }
    void verifySettingsGui(ClientGameTestContext context){
        if(scenario.equals("diamond"))context.runOnClient(mc->{
            byte[] before=null;try{
                var current=RobotSettings.INSTANCE;current.save();before=Files.readAllBytes(RobotSettings.FILE);var legacy=JsonParser.parseString(new String(before,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(String key:List.of("workMode","useOldEquipment","bulkOutputBoxes","bulkQuantity","bulkItem"))legacy.remove(key);
                Files.writeString(RobotSettings.FILE,WorkConfig.GSON.toJson(legacy));var constructor=RobotSettings.class.getDeclaredConstructor();constructor.setAccessible(true);var fresh=constructor.newInstance();fresh.load();
                check(fresh.workMode.getOptionListValue()==RobotSettings.WorkMode.NEWCOMER&&fresh.useOldEquipment.getBooleanValue(),"Legacy settings changed default mode or reuse");
                check(fresh.bulkQuantity.getIntegerValue()==1&&fresh.bulkOutputBoxes.getStrings().equals(List.of("minecraft:yellow_shulker_box")),"Legacy bulk defaults");
                for(var option:fresh.all)if(legacy.has(option.getName()))check(option.getAsJsonElement().equals(legacy.get(option.getName())),"Legacy setting changed: "+option.getName());
                result.put("legacySettingsPreserved",true);
            }catch(Exception e){throw new IllegalStateException(e);}finally{if(before!=null)try{Files.write(RobotSettings.FILE,before);}catch(Exception e){throw new IllegalStateException(e);}}
        });
        context.runOnClient(mc->{var s=RobotSettings.INSTANCE;int previous=s.bulkQuantity.getIntegerValue();s.bulkQuantity.setIntegerValue(0);check(s.bulkQuantity.getIntegerValue()==1,"Lower box limit");s.bulkQuantity.setIntegerValue(11);check(s.bulkQuantity.getIntegerValue()==10,"Upper box limit");s.bulkQuantity.setIntegerValue(previous);});
        context.runOnClient(mc->RobotSettings.INSTANCE.openGui());context.waitTicks(2);
        for(int tab=0;tab<3;tab++){
            int selected=tab;context.runOnClient(mc->{
                var screen=mc.gui.screen();screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(20+selected*110,30,new net.minecraft.client.input.MouseButtonInfo(0,0)),false);
                try{var method=screen.getClass().getMethod("getConfigs");method.setAccessible(true);var configs=(List<?>)method.invoke(screen);var names=configs.stream().map(v->((fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper)v).getConfig().getName()).toList();check(names.contains(selected==0?"workMode":selected==1?"useOldEquipment":"bulkItem"),"Wrong settings tab "+selected+" "+names);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            });context.waitTicks(2);context.takeScreenshot("v13-settings-"+tab);
        }
        context.runOnClient(mc->mc.gui.setScreen(null));result.put("settingsTabsClicked",3);
    }
    List<String> events(){var r=AutoEquipmentClient.robot;try(var in=Files.newInputStream(r.journal)){in.skipNBytes(journalStart);return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().map(s->JsonParser.parseString(s).getAsJsonObject()).filter(o->o.has("task")&&!o.get("task").isJsonNull()&&o.get("task").getAsString().equals(r.task)).map(o->o.get("event").getAsString()).toList();}catch(Exception e){throw new IllegalStateException(e);}}
    void setup(ServerLevel l){
        // The clone alone supplies empty source chests. Keep their locations away from the hall paths.
        var sources=new ArrayList<Container>();for(int x=1055;x<=1093;x+=2){var pos=new BlockPos(x,57,34);l.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());l.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());var c=(Container)l.getBlockEntity(pos);c.clearContent();sources.add(c);}
        // Remove old observed raw stock only in the disposable clone, to force each selected path.
        for(BlockPos pos:BlockPos.betweenClosed(MIN,MAX))if((l.getBlockState(pos).is(Blocks.SHULKER_BOX)||l.getBlockState(pos).is(Blocks.CHEST))&&!sources.contains(l.getBlockEntity(pos))&&l.getBlockEntity(pos) instanceof Container c)c.clearContent();
        if(!trade())l.setBlockAndUpdate(BOX,(newcomer()?Blocks.DYED_SHULKER_BOX.lightBlue():Blocks.DYED_SHULKER_BOX.yellow()).defaultBlockState());
        if(boxes()==2)l.setBlockAndUpdate(SECOND,Blocks.DYED_SHULKER_BOX.yellow().defaultBlockState());
        var supplies=new ArrayList<ItemStack>();
        if(trade()){
            for(int i=0;i<2;i++){var v=EntityTypes.VILLAGER.create(l,EntitySpawnReason.COMMAND);v.setPos(i==0?1069.5:1058.5,57,i==0?20.5:21.5);v.setNoAi(true);v.setInvulnerable(true);v.setVillagerData(v.getVillagerData().withProfession(l.registryAccess(),VillagerProfession.LIBRARIAN).withLevel(5));var offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemCost(Items.EMERALD,1),Optional.of(new ItemCost(Items.BOOK)),book(l,"minecraft:unbreaking",3),i==0?1:2,0,0));v.setOffers(offers);l.addFreshEntity(v);vendors.add(v.getUUID());observedOffers.put(v.getUUID(),offers.copy());}
            supplies.add(new ItemStack(Items.EMERALD,6));supplies.add(new ItemStack(Items.BOOK,6));
        }else if(newcomer()){
            supplies.add(new ItemStack(Items.DIAMOND,40));supplies.add(new ItemStack(Items.STICK,12));supplies.add(new ItemStack(Items.NETHERITE_INGOT,11));supplies.add(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,11));supplies.add(new ItemStack(Items.ELYTRA));supplies.add(new ItemStack(Items.BOW));supplies.add(new ItemStack(Items.SHIELD));
            for(var t:AutoEquipmentClient.robot.kitTemplate)for(var e:t.ench().entrySet())supplies.add(book(l,e.getKey(),e.getValue()));
            var pos=new BlockPos(1055,57,32);l.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());var c=(Container)l.getBlockEntity(pos);c.clearContent();int n=0;for(var t:AutoEquipmentClient.robot.kitTemplate)if(t.item().startsWith("minecraft:netherite_"))c.setItem(n++,PartialEquipmentGameTest.item(t.item()));var copy=new ArrayList<ItemStack>();for(int i=0;i<27;i++)copy.add(c.getItem(i).copy());preserved.put(pos,copy);
        }else{
            int n=27*boxes();Map<String,Integer> ench=item()==RobotSettings.BulkItem.ELYTRA?Map.of("minecraft:unbreaking",3,"minecraft:mending",1):Map.of("minecraft:efficiency",5,"minecraft:silk_touch",1,"minecraft:unbreaking",3,"minecraft:mending",1);
            for(int i=0;i<n;i++){for(var e:ench.entrySet())supplies.add(book(l,e.getKey(),e.getValue()));if(item()==RobotSettings.BulkItem.ELYTRA)supplies.add(new ItemStack(Items.ELYTRA));}
            if(item()==RobotSettings.BulkItem.NETHERITE_PICKAXE)for(int i=0;i<18;i++)supplies.add(i<6?new ItemStack(Items.NETHERITE_PICKAXE):i<12?PartialEquipmentGameTest.enchanted(l,"minecraft:diamond_pickaxe",Map.of("minecraft:unbreaking",1)):new ItemStack(Items.DIAMOND_PICKAXE));
            if(item()!=RobotSettings.BulkItem.ELYTRA){supplies.add(new ItemStack(Items.DIAMOND,64));supplies.add(new ItemStack(Items.DIAMOND,17));supplies.add(new ItemStack(Items.STICK,54));if(item()==RobotSettings.BulkItem.NETHERITE_PICKAXE){supplies.add(new ItemStack(Items.NETHERITE_INGOT,27));supplies.add(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,27));}}
        }
        supplies.add(new ItemStack(Items.ANVIL,64));supplies.add(new ItemStack(Items.GOLDEN_CARROT,64));
        for(var stack:supplies){boolean inserted=false;for(var c:sources){for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty()){c.setItem(i,stack);inserted=true;break;}if(inserted)break;}check(inserted,"Supply space");}
    }
    void runTrades(ClientGameTestContext context,TestSingleplayerContext world){
        context.waitFor(mc->{var r=AutoEquipmentClient.robot;check(r.running||r.state.equals("COMPLETED"),"Trade stopped");return r.state.equals("COMPLETED");},20000);
        context.runOnClient(mc->{check(events().stream().noneMatch(e->e.startsWith("TRADE COOLDOWN CHECK")),"Waited while another vendor had stock");var r=AutoEquipmentClient.robot;r.queue.add(r.trading.new Acquire(v->Stacks.book(v,"minecraft:unbreaking",3,0),4));r.running=true;r.state="TRADING";r.stepStart=r.ticks;});
        context.waitFor(mc->events().stream().anyMatch(e->e.startsWith("TRADE COOLDOWN CHECK 1")),5000);
        long start=System.nanoTime();context.waitTicks(80);context.runOnClient(mc->check(events().stream().filter(e->e.startsWith("TRADE COOLDOWN CHECK")).count()==1,"Cooldown polled before 20s"));
        world.getServer().runOnServer(server->{var l=server.getLevel(Level.END);check(((Villager)l.getEntity(vendors.get(0))).getOffers().getFirst().getUses()==1,"First vendor not exhausted");check(((Villager)l.getEntity(vendors.get(1))).getOffers().getFirst().getUses()==2,"Second vendor not used");((Villager)l.getEntity(vendors.get(0))).getOffers().getFirst().resetUses();});
        context.waitFor(mc->{var r=AutoEquipmentClient.robot;check(r.running||r.state.equals("COMPLETED"),"Cooldown stopped");return r.state.equals("COMPLETED");},100000);
        check(System.nanoTime()-start>=19_000_000_000L,"Real-time cooldown too short");
        context.runOnClient(mc->{check(events().stream().filter(e->e.startsWith("NOTICE 村民进入补货冷却")).count()==1,"Cooldown announcement missing or repeated");check(events().stream().filter(e->e.startsWith("NOTICE 村民补货完成")).count()==1,"Recovery announcement");});result.put("purchases",4);result.put("vendorSwitchWithoutWait",true);result.put("allExhaustedWaitMsAtLeast",19000);
    }
}
