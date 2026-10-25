package io.github.autoequipmentmaking.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.Vec3;

/** Real client inputs and integrated-server physics; no teleport after a route starts. */
public final class NavigationGameTest implements FabricClientGameTest {
    private final JsonArray results=new JsonArray();
    private PlayerNavigation active;
    private int ticks,pauses,sprintTicks;
    private double travelled,maxTurn;
    private Vec3 last;
    private float yaw;
    @Override public void runTest(ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            reset(context,world);
            world.getServer().runOnServer(server->{
                server.overworld().setBlockAndUpdate(new BlockPos(0,65,0),Blocks.CARPET.white().defaultBlockState());
                server.getPlayerList().getPlayers().getFirst().teleportTo(.5,65.0625,.5);
            });
            context.waitTicks(10);run(context,world,"carpet-start",new BlockPos(3,65,0),true,false);

            reset(context,world);
            world.getServer().runOnServer(server->{
                server.overworld().setBlockAndUpdate(new BlockPos(0,65,0),Blocks.OAK_TRAPDOOR.defaultBlockState());
                server.getPlayerList().getPlayers().getFirst().teleportTo(.5,65.1875,.5);
            });
            context.waitTicks(10);run(context,world,"bottom-trapdoor-start",new BlockPos(3,65,0),true,false);

            reset(context,world);
            world.getServer().runOnServer(server->{
                server.overworld().setBlockAndUpdate(new BlockPos(2,64,0),Blocks.AIR.defaultBlockState());
                server.getPlayerList().getPlayers().getFirst().teleportTo(2.15,65,.5);
            });
            context.waitTicks(10);run(context,world,"supported-edge-start",new BlockPos(-3,65,0),true,false);

            for(Direction direction:List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST)){
                reset(context,world);
                world.getServer().runOnServer(server->{
                    var level=server.overworld();boolean alongX=direction.getAxis()==Direction.Axis.Z;
                    var panel=Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.FACING,direction);
                    for(int i=5;i<=10;i++)for(int y=65;y<=66;y++)level.setBlockAndUpdate(alongX?new BlockPos(i,y,1):new BlockPos(1,y,i),panel);
                    double side=direction==Direction.NORTH||direction==Direction.WEST?1.699999988079:1.300000011921;
                    // Same relative position as the real blocked corner, after sprint drift.
                    server.getPlayerList().getPlayers().getFirst().teleportTo(alongX?4.699999988079:side,65,alongX?side:4.699999988079);
                });
                context.waitTicks(10);
                run(context,world,"open-trapdoor-offset-"+direction.getName(),direction.getAxis()==Direction.Axis.Z?new BlockPos(8,65,1):new BlockPos(1,65,8),true,false);
                check(active.plans==1&&active.centeringTicks>0,"Trapdoor turn must recover without repeating the same plan");
            }

            reset(context,world);
            world.getServer().runOnServer(server->{
                var level=server.overworld();
                for(int x=-10;x<=16;x++)for(int y=65;y<=69;y++)for(int z:List.of(-1,1))level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
                for(int y=65;y<=69;y++)level.setBlockAndUpdate(new BlockPos(4,y,0),Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.FACING,Direction.EAST));
            });
            context.waitTicks(10);reject(context,"open-trapdoor-blocking-corridor",new BlockPos(8,65,0));

            reset(context,world);
            run(context,world,"flat-diagonal",new BlockPos(10,65,10),true,false);
            check(active.jumps==0&&ticks<60&&sprintTicks>35,"Flat movement not continuous/diagonal");
            check(travelled<17&&pauses<12&&maxTurn<=20.01,"Flat route jerk or detour");

            reset(context,world);context.runOnClient(mc->mc.options.toggleSprint().set(true));
            run(context,world,"toggle-sprint",new BlockPos(10,65,10),true,false);
            check(ticks<60&&sprintTicks>35,"Toggle sprint flickered");
            context.runOnClient(mc->check(mc.options.toggleSprint().get(),"Toggle sprint setting changed"));

            reset(context,world);
            world.getServer().runOnServer(server->{
                for(int z=-3;z<=3;z++)for(int y=65;y<=68;y++)server.overworld().setBlockAndUpdate(new BlockPos(2,y,z),Blocks.STONE.defaultBlockState());
                server.overworld().setBlockAndUpdate(new BlockPos(3,65,0),Blocks.CHEST.defaultBlockState());
            });
            context.waitTicks(10);
            run(context,world,"occluded-chest",new BlockPos(3,65,0),false,true);
            context.runOnClient(mc->check(mc.player.containerMenu instanceof ChestMenu,"Chest did not open"));
            check(travelled>3&&active.jumps==0,"Wall ignored or collision caused blind jumping");

            reset(context,world);
            world.getServer().runOnServer(server->{
                var level=server.overworld();
                for(var p:List.of(new BlockPos(0,65,3),new BlockPos(2,65,3),new BlockPos(4,65,3),new BlockPos(0,65,1),new BlockPos(2,65,1)))level.setBlockAndUpdate(p,Blocks.SHULKER_BOX.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(0,65,0),Blocks.CHEST.defaultBlockState());
                server.getPlayerList().getPlayers().getFirst().teleportTo(4.123047727,65,6.952265459);
            });
            context.waitTicks(10);run(context,world,"off-center-corridor",new BlockPos(0,65,0),false,true);
            check(active.plans==1,"Smoothed route incorrectly checked from cell center");

            reset(context,world);platform(world,1);context.waitTicks(10);
            run(context,world,"one-block",new BlockPos(8,66,0),true,false);
            check(active.jumps>0,"No real one-block jump");

            reset(context,world);
            world.getServer().runOnServer(server->{for(int x=4;x<=17;x++)for(int z=-11;z<=17;z++)server.overworld().setBlockAndUpdate(new BlockPos(x,65,z),Blocks.STONE_SLAB.defaultBlockState());});
            context.waitTicks(10);run(context,world,"half-slab-step",new BlockPos(8,66,0),true,false);
            check(active.jumps==0,"Half slab triggered unnecessary jump");
            run(context,world,"half-slab-descent",new BlockPos(0,65,0),true,false);

            reset(context,world);
            world.getServer().runOnServer(server->{for(int y=65;y<=68;y++)for(var p:List.of(new BlockPos(1,y,0),new BlockPos(0,y,1)))server.overworld().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());});
            context.waitTicks(10);run(context,world,"diagonal-corner",new BlockPos(2,65,2),true,false);
            check(travelled>4,"Diagonal route cut through a blocked corner");

            reset(context,world);platform(world,2);context.waitTicks(10);
            reject(context,"two-block-no-boost",new BlockPos(8,67,0));
            boost(world,0,600);context.waitTicks(10);
            reject(context,"two-block-boost-I",new BlockPos(8,67,0));

            reset(context,world);platform(world,2);
            world.getServer().runOnServer(server->{
                var level=server.overworld();BlockPos beacon=new BlockPos(-5,64,-5);
                for(int depth=1;depth<=4;depth++)for(int x=-depth;x<=depth;x++)for(int z=-depth;z<=depth;z++)level.setBlockAndUpdate(beacon.offset(x,-depth,z),Blocks.IRON_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(beacon,Blocks.BEACON.defaultBlockState());
                var entity=(BeaconBlockEntity)level.getBlockEntity(beacon);var player=server.getPlayerList().getPlayers().getFirst();
                var menu=(BeaconMenu)entity.createMenu(0,player.getInventory(),player);
                menu.setData(BeaconBlockEntity.DATA_PRIMARY,BeaconMenu.encodeEffect(MobEffects.JUMP_BOOST));
                menu.setData(BeaconBlockEntity.DATA_SECONDARY,BeaconMenu.encodeEffect(MobEffects.JUMP_BOOST));
            });
            context.waitFor(mc->mc.player.getEffect(MobEffects.JUMP_BOOST)!=null&&mc.player.getEffect(MobEffects.JUMP_BOOST).getAmplifier()==1,300);
            run(context,world,"two-block-real-beacon-II",new BlockPos(8,67,0),true,false);
            check(active.jumps>0,"No real two-block jump");context.takeScreenshot("navigation-beacon-two-block");

            reset(context,world);platform(world,2);boost(world,1,600);
            world.getServer().runOnServer(server->{for(int x=0;x<=5;x++)for(int z=-10;z<=16;z++)server.overworld().setBlockAndUpdate(new BlockPos(x,68,z),Blocks.STONE.defaultBlockState());});
            context.waitTicks(10);reject(context,"two-block-low-ceiling",new BlockPos(8,67,0));

            reset(context,world);
            world.getServer().runOnServer(server->{for(int x=3;x<=5;x++)for(int z=-2;z<=2;z++)server.overworld().setBlockAndUpdate(new BlockPos(x,64,z),Blocks.MAGMA_BLOCK.defaultBlockState());});
            context.waitTicks(10);run(context,world,"magma-detour",new BlockPos(9,65,0),true,false);
            check(travelled>9,"Hazard was not avoided");

            reset(context,world);platform(world,2);boost(world,1,600);context.waitTicks(10);
            start(context,"effect-removal",new BlockPos(8,67,0),true,false);context.waitTicks(2);
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().removeEffect(MobEffects.JUMP_BOOST));
            context.waitFor(mc->!AutoEquipmentClient.robot.running,250);
            context.runOnClient(mc->{check(!AutoEquipmentClient.robot.state.equals("COMPLETED"),"Stale boost route used");check(mc.player.getX()<4&&!mc.options.keyJump.isDown()&&!mc.options.keyUp.isDown(),"Unsafe input after effect removal");});
            save("effect-removal","safe-stop");

            reset(context,world);start(context,"dynamic-obstacle",new BlockPos(10,65,0),true,false);context.waitTicks(8);
            world.getServer().runOnServer(server->{for(int z=-2;z<=2;z++)for(int y=65;y<=68;y++)server.overworld().setBlockAndUpdate(new BlockPos(5,y,z),Blocks.STONE.defaultBlockState());});
            finish(context,world,"dynamic-obstacle",new BlockPos(10,65,0),true);check(active.plans>=2,"Route was not recalculated");

            reset(context,world);start(context,"manual-pause",new BlockPos(10,65,0),true,false);context.waitTicks(10);
            context.runOnClient(mc->AutoEquipmentClient.robot.manualPause("navigation test pause"));context.waitTicks(10);
            context.runOnClient(mc->check(!mc.options.keyUp.isDown()&&!mc.options.keyJump.isDown()&&!mc.options.keySprint.isDown()&&!mc.player.isSprinting(),"Pause retained movement keys"));
            context.runOnClient(mc->AutoEquipmentClient.robot.resume());finish(context,world,"manual-pause-resume",new BlockPos(10,65,0),true);

            reset(context,world);
            world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(6));
            context.waitTicks(10);run(context,world,"low-hunger-walk",new BlockPos(10,65,0),true,false);
            check(sprintTicks==0,"Sprint bypassed vanilla hunger restriction");
        } finally {
            try{Files.writeString(Path.of("navigation-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));}
            catch(Exception e){throw new IllegalStateException(e);}
        }
    }
    private void reset(ClientGameTestContext context,TestSingleplayerContext world){
        context.runOnClient(mc->{var r=AutoEquipmentClient.robot;r.running=false;r.release();r.queue.clear();r.close();r.uncertain=false;r.manualPaused=false;r.awaitingGood=false;r.food.reset();});
        context.runOnClient(mc->mc.options.toggleSprint().set(false));
        world.getServer().runOnServer(server->{
            var level=server.overworld();for(int x=-11;x<=17;x++)for(int z=-11;z<=17;z++){
                level.setBlockAndUpdate(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState());
                for(int y=65;y<=75;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            var player=server.getPlayerList().getPlayers().getFirst();player.removeAllEffects();player.setGameMode(GameType.SURVIVAL);
            player.getFoodData().setFoodLevel(20);player.setHealth(20);player.teleportTo(.5,65,.5);
        });
        context.waitTicks(15);
        context.runOnClient(mc->{var r=AutoEquipmentClient.robot;var bounds=new JsonObject();bounds.add("min",WorkConfig.pos(new BlockPos(-10,64,-10)));bounds.add("max",WorkConfig.pos(new BlockPos(16,74,16)));r.cfg.root.add("platformBounds",bounds);r.cfg.root.getAsJsonObject("notifications").addProperty("chatEnabled",false);mc.gui.setScreen(null);mc.options.autoJump().set(true);mc.player.setYRot(-90);mc.player.setXRot(10);});
    }
    private void platform(TestSingleplayerContext world,int height){world.getServer().runOnServer(server->{for(int x=4;x<=17;x++)for(int z=-11;z<=17;z++)for(int y=65;y<65+height;y++)server.overworld().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());});}
    private void boost(TestSingleplayerContext world,int amplifier,int duration){world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST,duration,amplifier)));}
    private void start(ClientGameTestContext context,String name,BlockPos target,boolean pickup,boolean open){
        context.runOnClient(mc->{
            var r=AutoEquipmentClient.robot;r.task=UUID.randomUUID().toString();r.server=r.server();r.dimension=r.dimension();r.state="NAV_TEST_"+name;r.stepStart=r.ticks;r.running=true;r.queue.clear();
            active=r.new Navigator(target,pickup);ticks=0;pauses=0;sprintTicks=0;travelled=0;maxTurn=0;last=mc.player.position();yaw=mc.player.getYRot();
            var opening=open?r.new Open(target,ChestMenu.class):null;if(opening!=null)opening.nav=(Robot.Navigator)active;
            r.queue.add(()->{ticks++;if(mc.player.isSprinting())sprintTicks++;travelled+=mc.player.position().distanceTo(last);last=mc.player.position();boolean done=opening==null?active.tick():opening.tick();if(!done&&!mc.options.keyUp.isDown())pauses++;if(opening==null)maxTurn=Math.max(maxTurn,Math.abs(net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot()-yaw)));yaw=mc.player.getYRot();check(mc.player.getHealth()==20,"Movement caused damage");return done;});
        });
    }
    private void run(ClientGameTestContext context,TestSingleplayerContext world,String name,BlockPos target,boolean pickup,boolean open){start(context,name,target,pickup,open);finish(context,world,name,target,pickup);}
    private void finish(ClientGameTestContext context,TestSingleplayerContext world,String name,BlockPos target,boolean pickup){
        context.waitFor(mc->!AutoEquipmentClient.robot.running,700);
        context.runOnClient(mc->check(AutoEquipmentClient.robot.state.equals("COMPLETED"),name+" stopped: "+AutoEquipmentClient.robot.state));
        context.runOnClient(mc->check(mc.options.autoJump().get(),"Navigation changed the user's auto-jump setting"));
        context.runOnClient(mc->check(!mc.options.keySprint.isDown()&&!mc.player.isSprinting(),"Sprint stayed active after arrival"));
        if(pickup)world.getServer().runOnServer(server->check(server.getPlayerList().getPlayers().getFirst().position().distanceTo(Vec3.atBottomCenterOf(target))<.8,"Server did not confirm arrival: "+name));
        save(name,"arrived");
    }
    private void reject(ClientGameTestContext context,String name,BlockPos target){
        context.runOnClient(mc->{active=AutoEquipmentClient.robot.new Navigator(target,true);boolean rejected=false;try{active.tick();}catch(IllegalStateException expected){rejected=expected.getMessage().startsWith("没有安全路线");}check(rejected,name+" accepted impossible route");check(!mc.options.keyUp.isDown()&&!mc.options.keyJump.isDown(),"Rejected route moved player");});
        ticks=0;travelled=0;pauses=0;sprintTicks=0;maxTurn=0;save(name,"rejected");
    }
    private void save(String name,String result){var row=new JsonObject();row.addProperty("name",name);row.addProperty("result",result);row.addProperty("ticks",ticks);row.addProperty("sprintTicks",sprintTicks);row.addProperty("travelled",travelled);row.addProperty("pausedTicks",pauses);row.addProperty("maxYawChange",maxTurn);row.addProperty("plans",active.plans);row.addProperty("jumps",active.jumps);row.addProperty("centeringTicks",active.centeringTicks);results.add(row);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
