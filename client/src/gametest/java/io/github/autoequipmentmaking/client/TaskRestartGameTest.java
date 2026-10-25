package io.github.autoequipmentmaking.client;

import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;

/** Cancel a failed task through the real hotkey callback, then run a fresh complete production. */
public final class TaskRestartGameTest extends V11ProductionGameTest {
    private final AtomicInteger discardedSteps=new AtomicInteger();
    private String cancelledTask;
    @Override protected void fixtures(ServerLevel level,ServerPlayer player){
        super.fixtures(level,player);
        // Keep the first, deliberately failed task from moving food before cancellation.
        player.getFoodData().setFoodLevel(20);
    }
    @Override protected void afterStart(ClientGameTestContext context,TestSingleplayerContext world){
        context.runOnClient(mc->{
            var r=AutoEquipmentClient.robot;cancelledTask=r.task;r.queue.clear();
            r.queue.add(()->{throw new IllegalStateException("玩家不在安全站立面上（重开任务回归用例）");});
            r.queue.add(()->{discardedSteps.incrementAndGet();return true;});
        });
        context.waitFor(mc->!AutoEquipmentClient.robot.running,100);
        context.runOnClient(mc->{
            var r=AutoEquipmentClient.robot;check(r.uncertain&&r.state.equals("RECOVERY_REQUIRED"),"Failure did not require recovery");
            r.resume();check(!r.running&&r.uncertain,"Resume bypassed an unconfirmed failed task");
            var abandoned=new CompletableFuture<List<Robot.Planned>>();r.planning=abandoned;
            r.stock.add(new Robot.Source(new BlockPos(0,65,0),0,new ItemStack(Items.DIAMOND,3)));
            r.batchBoxes.add(new BlockPos(6,65,5));r.activePacking=new BlockPos(6,65,5);
            r.awaitingGood=true;r.manualPaused=true;r.waitingReason="old task";r.trading.scanned=true;r.trading.spent=99;
            var key=RobotSettings.INSTANCE.cancel.getKeybind();check(key.getKeys().size()==2,"Cancel chord was not parsed");
            check(((KeybindMulti)key).getCallback().onKeyAction(KeyAction.PRESS,key),"Cancel hotkey callback not handled");
            check(!r.running&&!r.uncertain&&!r.awaitingGood&&!r.manualPaused&&r.queue.isEmpty(),"Cancel retained execution flags");
            check(r.planning==null&&abandoned.isCancelled()&&!abandoned.complete(List.of()),"Old planner survived cancellation");
            check(r.stock.isEmpty()&&r.plans.isEmpty()&&r.batchBoxes.isEmpty()&&r.activePacking==null&&r.batchCenter==null,"Cancel retained observations or boxes");
            check(!r.trading.scanned&&r.trading.spent==0&&r.trading.quotes.isEmpty()&&r.protectedSlots.isEmpty(),"Cancel retained task allocation");
            boolean detached=false;try{r.observer.requireWorld();}catch(IllegalStateException expected){detached=true;}check(detached,"Old observation connection retained");
            r.resume();check(!r.running&&r.state.equals("CANCELLED"),"Cancelled queue resumed");
        });
        world.getServer().runOnServer(server->server.getPlayerList().getPlayers().getFirst().getFoodData().setFoodLevel(14));
        context.waitTicks(2);
        context.runOnClient(mc->{
            var r=AutoEquipmentClient.robot;r.requestStart();check(r.awaitingDelivery,"Fresh start did not request delivery binding");
            r.playerChat("Player0","skip");check(r.running&&!r.task.equals(cancelledTask),"Fresh task did not start");
            check(r.protectedSlots.contains(0)&&r.protectedSlots.contains(1),"Initial private currency not protected by new task");
            check(!r.trading.scanned&&r.stock.isEmpty(),"Fresh task reused old scan");
            r.record("RESTART VERIFIED previous="+cancelledTask);
        });
        super.afterStart(context,world);
    }
    @Override protected void verifyExtra(ServerLevel level,ServerPlayer player){
        super.verifyExtra(level,player);
        check(discardedSteps.get()==0,"Cancelled task step executed in new task");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
