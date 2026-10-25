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
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Runs only a disposable byte-for-byte clone of the user-arranged 201 baseline. */
public final class TradingHallNavigationGameTest implements FabricClientGameTest {
    static final BlockPos MIN=new BlockPos(1054,56,8),MAX=new BlockPos(1095,68,36),RAW=new BlockPos(1088,63,16),OUTPUT=new BlockPos(1066,57,23);
    final Map<String,Object> result=new LinkedHashMap<>();
    final List<BlockPos> boxes=new ArrayList<>();
    final Map<BlockPos,net.minecraft.world.level.block.state.BlockState> trapdoors=new HashMap<>();
    long seed=Long.parseLong(System.getenv().getOrDefault("AEM_HALL_SEED","20120261025"));
    int sprintHoldTicks,sprintToggleTicks;
    String mode=System.getenv().getOrDefault("AEM_HALL_MODE","production");
    static void check(boolean v,String reason){if(!v)throw new AssertionError(reason);}
    @Override public void runTest(ClientGameTestContext context){
        Path save=Path.of(Objects.requireNonNull(System.getenv("AEM_HALL_WORLD"))).toAbsolutePath();
        check(save.toString().contains("clientGameTest")&&!save.toString().contains("test-baselines"),"Only disposable test save may open");
        result.put("seed",seed);result.put("mode",mode);result.put("save",save.toString());result.put("complete",false);
        try(var world=new TestWorldSaveImpl(context,save).open()){
            world.getServer().runOnServer(server->{
                var l=server.getLevel(Level.END);for(int x=65;x<=69;x++)for(int z=0;z<=2;z++)l.setChunkForced(x,z,true);
                var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.experienceLevel=0;p.giveExperienceLevels(2500);p.getFoodData().setFoodLevel(20);
                if(mode.equals("fluid"))p.teleportTo(l,1070.699999988079,57,22.489724741761112,Set.of(),0,0,true);
                else if(mode.equals("sight"))p.teleportTo(l,1066.487500011921,57,18.50480385977341,Set.of(),0,0,true);
                else if(mode.equals("edge"))p.teleportTo(l,1082.50274304426,57,12.299011255566064,Set.of(),0,0,true);
                else p.teleportTo(l,1068.5,57,21.5,Set.of(),0,0,true);
                for(BlockPos b:BlockPos.betweenClosed(MIN,MAX))if(l.getBlockState(b).getBlock() instanceof TrapDoorBlock)trapdoors.put(b.immutable(),l.getBlockState(b));
                if(mode.equals("production"))setup(l);
            });
            context.waitFor(mc->mc.level!=null&&mc.level.dimension()==Level.END,400);context.waitTicks(160);
            context.runOnClient(mc->{
                try{var generic=Class.forName("fi.dy.masa.minihud.config.Configs$Generic");Object sync=generic.getField("ENTITY_DATA_SYNC").get(null);sync.getClass().getMethod("setBooleanValue",boolean.class).invoke(sync,true);}catch(Exception e){throw new IllegalStateException(e);}
                var r=AutoEquipmentClient.robot;var c=r.cfg.root;c.addProperty("serverKey","singleplayer");c.addProperty("dimension",r.dimension());
                var bounds=new JsonObject();bounds.add("min",WorkConfig.pos(MIN));bounds.add("max",WorkConfig.pos(MAX));c.add("platformBounds",bounds);
                var ws=c.getAsJsonObject("workstations");
                for(var e:Map.of("crafting",new BlockPos(1072,57,24),"smithing",new BlockPos(1071,57,24),"grindstones",new BlockPos(1079,56,29)).entrySet()){var a=new JsonArray();a.add(WorkConfig.pos(e.getValue()));ws.add(e.getKey(),a);}
                var anvils=new JsonArray();for(int y=57;y<=62;y++)anvils.add(WorkConfig.pos(new BlockPos(1067,y,24)));ws.add("anvils",anvils);
                c.getAsJsonObject("packing").add("outputContainer",WorkConfig.pos(OUTPUT));c.getAsJsonObject("notifications").addProperty("chatEnabled",false);r.cfg.save();
                var s=RobotSettings.INSTANCE;s.workMode.setOptionListValue(RobotSettings.WorkMode.NEWCOMER);s.useOldEquipment.setBooleanValue(true);s.quantityMode.setOptionListValue(RobotSettings.QuantityMode.AUTO);s.outputBoxes.setStrings(List.of("minecraft:light_blue_shulker_box"));s.scanLength.setIntegerValue(51);s.scanWidth.setIntegerValue(51);s.scanHeight.setIntegerValue(15);
                for(var option:List.of(s.swiftSneak,s.soulSpeed,s.elytra,s.shield,s.trading,s.spareAnvils))option.setBooleanValue(true);
                r.kit.clear();r.kit.addAll(r.kitTemplate);mc.gui.setScreen(null);mc.options.toggleSprint().set(false);
                result.put("initialEffects",mc.player.getActiveEffects().toString());
            });
            if(!mode.equals("production")){
                context.runOnClient(mc->{var r=AutoEquipmentClient.robot;result.put("probeStart",mc.player.position().toString());r.server=r.server();r.dimension=r.dimension();r.running=true;r.state="NAV PROBE";r.task=UUID.randomUUID().toString();r.queue.clear();r.queue.add(new PlayerNavigation(r,mode.equals("edge")?new BlockPos(1072,57,24):mode.equals("sight")?new BlockPos(1064,57,20):mode.equals("fluid")?new BlockPos(1088,57,27):RAW,false));});
                context.waitFor(mc->{var r=AutoEquipmentClient.robot;check(r.running||r.state.equals("COMPLETED"),"Navigation probe stopped: "+r.state);return r.queue.isEmpty();},8000);
                context.runOnClient(mc->{result.put("rawArrival",mc.player.position().toString());AutoEquipmentClient.robot.running=false;AutoEquipmentClient.robot.release();});
            }else{
                context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.start();check(r.running,"Start failed: "+r.state);});
                context.waitFor(mc->{var r=AutoEquipmentClient.robot;if(r.deliveredKits>=1)mc.options.toggleSprint().set(true);if(r.ownsNavigation()&&mc.player.isSprinting()){if(mc.options.toggleSprint().get())sprintToggleTicks++;else sprintHoldTicks++;}check(!r.awaitingGood,"Unexpected restock gate: "+r.waitingReason);check(r.running||r.state.equals("COMPLETED"),"Production stopped: "+r.state);return r.state.equals("COMPLETED");},120000);
                world.getServer().runOnServer(server->verify(server.getLevel(Level.END),server.getPlayerList().getPlayers().getFirst()));
                context.runOnClient(mc->{var r=AutoEquipmentClient.robot;check(r.deliveredKits==3&&r.batchBoxes.size()==3,"Wrong batch count");check(sprintHoldTicks>50&&sprintToggleTicks>50,"Automatic sprint not exercised in both option modes");check(!mc.options.keySprint.isDown()&&!mc.player.isSprinting(),"Sprint leaked after task completion");result.put("sprintHoldTicks",sprintHoldTicks);result.put("sprintToggleTicks",sprintToggleTicks);result.put("deliveredKits",r.deliveredKits);result.put("task",r.task);});
            }
            world.getServer().runOnServer(server->{var l=server.getLevel(Level.END);for(var e:trapdoors.entrySet())check(l.getBlockState(e.getKey())==e.getValue(),"Trapdoor changed: "+e.getKey());});
            result.put("trapdoorsUnchanged",trapdoors.size());result.put("complete",true);context.takeScreenshot("201-navigation-"+mode+"-complete");
        }finally{try{Files.writeString(Path.of("201-navigation-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new IllegalStateException(e);}}
    }
    void setup(ServerLevel l){
        var random=new Random(seed);var candidates=new ArrayList<BlockPos>();
        for(BlockPos b:BlockPos.betweenClosed(new BlockPos(1057,57,9),new BlockPos(1092,57,35))){
            if(!l.getBlockState(b).isAir()||!l.getBlockState(b.above()).isAir()||!l.getBlockState(b.below()).isCollisionShapeFullBlock(l,b.below()))continue;
            boolean room=true;for(Direction d:Direction.Plane.HORIZONTAL)if(!l.getBlockState(b.relative(d)).isAir())room=false;
            if(room)candidates.add(b.immutable());
        }
        Collections.shuffle(candidates,random);
        for(BlockPos p:candidates){if(boxes.stream().anyMatch(q->q.distSqr(p)<100))continue;boxes.add(p);if(boxes.size()==3)break;}
        check(boxes.size()==3,"Three separated random output locations required");
        for(BlockPos p:boxes)l.setBlockAndUpdate(p,Blocks.DYED_SHULKER_BOX.lightBlue().defaultBlockState());
        result.put("packingPositions",boxes.stream().map(BlockPos::toShortString).toList());
        // Supplies only: fill shortages in existing uncolored book boxes, retain terrain and all original items.
        var sources=new ArrayList<Container>();
        for(BlockPos p:BlockPos.betweenClosed(MIN,MAX))if(l.getBlockState(p).is(Blocks.SHULKER_BOX)&&SourceFetch.openingClear(l,p)&&l.getBlockEntity(p) instanceof Container c)sources.add(c);
        var needs=new TreeMap<String,Integer>();for(var t:AutoEquipmentClient.robot.kitTemplate)for(var e:t.ench().entrySet())needs.merge(e.getKey()+"@"+e.getValue(),3,Integer::sum);
        var additions=new ArrayList<String>();
        for(var e:needs.entrySet()){
            var parts=e.getKey().split("@");int have=0;for(var c:sources)for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(s.is(Items.ENCHANTED_BOOK)&&Stacks.ench(s,true).equals(Map.of(parts[0],Integer.parseInt(parts[1]))))have+=s.getCount();}
            for(int n=have;n<e.getValue();n++){var book=new ItemStack(Items.ENCHANTED_BOOK);var ench=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);ench.set(l.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Identifier.parse(parts[0])).orElseThrow(),Integer.parseInt(parts[1]));book.set(DataComponents.STORED_ENCHANTMENTS,ench.toImmutable());insert(sources,book);additions.add(e.getKey());}
        }
        for(int n=0;n<3;n++)insert(sources,new ItemStack(Items.ELYTRA));
        result.put("bookTopUps",additions);result.put("elytraTopUps",3);result.put("rawBox",RAW.toShortString());
    }
    void insert(List<Container> sources,ItemStack s){for(var c:sources)for(int i=0;i<c.getContainerSize();i++)if(c.getItem(i).isEmpty()){c.setItem(i,s);c.setChanged();return;}throw new AssertionError("No existing supply slot for fixture top-up");}
    void verify(ServerLevel l,net.minecraft.server.level.ServerPlayer p){
        var c=(Container)l.getBlockEntity(OUTPUT);int count=0;
        for(int i=0;i<c.getContainerSize();i++){
            var box=c.getItem(i);if(!box.is(Items.DYED_SHULKER_BOX.lightBlue()))continue;
            count++;var contents=box.getOrDefault(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY).allItemsCopyStream().toList();
            for(var t:AutoEquipmentClient.robot.kitTemplate){check(contents.size()>t.slot(),"Missing output gear");var gear=contents.get(t.slot());check(Stacks.id(gear).equals(t.item())&&gear.getCount()==1&&gear.getDamageValue()==0&&Stacks.ench(gear,false).equals(t.ench()),"Wrong gear: "+t.key());}
            for(int j=14;j<contents.size();j++)check(contents.get(j).isEmpty(),"Output clutter");
        }
        check(count==3,"Expected three output kits, got "+count);for(var b:boxes)check(l.getBlockState(b).isAir(),"Packing box not recovered");
        result.put("verifiedEquipment",42);result.put("remainingLevels",p.experienceLevel);
    }
}
