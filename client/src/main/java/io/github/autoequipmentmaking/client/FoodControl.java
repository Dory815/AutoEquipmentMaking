package io.github.autoequipmentmaking.client;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;

/** Runs only between closed-menu steps. Initial inventory protection permits this one food item. */
final class FoodControl {
    final Robot r;boolean due=true,warned;Eat active;
    FoodControl(Robot r){this.r=r;}
    void reset(){due=true;warned=false;active=null;}
    boolean eligible(ItemStack s){
        var food=s.get(DataComponents.FOOD);var use=s.get(DataComponents.CONSUMABLE);
        return !s.isEmpty()&&food!=null&&food.nutrition()>0&&use!=null&&use.onConsumeEffects().isEmpty()&&!s.has(DataComponents.USE_REMAINDER);
    }
    int find(){for(int i=0;i<36;i++)if(eligible(r.mc.player.getInventory().getItem(i)))return i;return eligible(r.mc.player.getOffhandItem())?40:-1;}
    boolean tick(){
        if(active!=null)return false;
        if(!due||r.mining||r.mc.player.containerMenu!=r.mc.player.inventoryMenu||!r.mc.player.containerMenu.getCarried().isEmpty())return false;
        if(r.queue.peek() instanceof Robot.Open o&&o.sent)return false;
        due=false;int slot=find();if(slot>=0)warned=false;
        if(r.mc.player.getFoodData().getFoodLevel()>14)return false;
        if(slot<0){if(!warned){warned=true;r.announce("食物缺货：身上没有可安全食用的食物，继续工作");r.record("FOOD MISSING CONTINUE");}return false;}
        active=new Eat(slot);r.queue.addFirst(active);return false;
    }
    void pause(){r.mc.options.keyUse.setDown(false);if(active!=null&&active.started){r.mc.gameMode.releaseUsingItem(r.mc.player);active.started=false;}}
    final class Eat implements Robot.Step {
        final int original;int hand=-1,selected=-1,beforeCount,beforeFood;ItemStack before,displacedStack;boolean moved,swapped,started,returning;long start,slotRevision,foodRevision;
        Eat(int slot){original=slot;}
        public boolean tick(){
            if(returning)return true;
            if(hand<0){
                selected=r.mc.player.getInventory().getSelectedSlot();hand=original;before=r.mc.player.getInventory().getItem(original).copy();beforeCount=before.getCount();beforeFood=r.mc.player.getFoodData().getFoodLevel();
                if(original>=9&&original!=40){hand=-1;for(int i=0;i<9;i++)if(!r.protectedSlots.contains(i)&&r.mc.player.getInventory().getItem(i).isEmpty()){hand=i;break;}
                    var steps=new ArrayList<Robot.Step>();
                    if(hand<0){for(int i=0;i<9;i++)if(!r.protectedSlots.contains(i)){hand=i;break;}r.require(hand>=0,"没有任务快捷栏槽可用于进食");swapped=true;displacedStack=r.mc.player.getInventory().getItem(hand).copy();steps.addAll(swap(original,hand,before,displacedStack));}
                    else {moved=true;steps.add(r.new Move(r.menuSlot(original),r.menuSlot(hand),beforeCount,before));}steps.add(this);r.prepend(steps);return true;
                }
            }
            var held=r.mc.player.getInventory().getItem(hand);
            boolean consumed=startedOrPreviouslyUsed()&&(beforeCount==1?held.isEmpty():ItemStack.isSameItemSameComponents(held,before)&&held.getCount()==beforeCount-1)&&r.mc.player.getFoodData().getFoodLevel()>beforeFood&&Robot.playerSlotRevisions.getOrDefault(hand,0L)>slotRevision&&Robot.foodRevision>foodRevision;
            if(consumed){
                r.mc.options.keyUse.setDown(false);r.mc.gameMode.releaseUsingItem(r.mc.player);r.mc.player.getInventory().setSelectedSlot(selected);r.mc.getConnection().send(new ServerboundSetCarriedItemPacket(selected));
                var steps=new ArrayList<Robot.Step>();if(moved&&!held.isEmpty())steps.add(r.new Move(r.menuSlot(hand),r.menuSlot(original),held.getCount(),held));if(swapped){if(held.isEmpty())steps.add(r.new Move(r.menuSlot(original),r.menuSlot(hand),displacedStack.getCount(),displacedStack));else steps.addAll(swap(original,hand,displacedStack,held.copy()));}if(!steps.isEmpty()){returning=true;r.prepend(steps);}
                r.record("FOOD EATEN one=1");active=null;return true;
            }
            if(!started){r.release();r.close();if(hand!=40){r.mc.player.getInventory().setSelectedSlot(hand);r.mc.getConnection().send(new ServerboundSetCarriedItemPacket(hand));}if(start==0){slotRevision=Robot.playerSlotRevisions.getOrDefault(hand,0L);foodRevision=Robot.foodRevision;}r.mc.options.keyUse.setDown(true);r.mc.gameMode.useItem(r.mc.player,hand==40?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND);started=true;start=r.ticks;}
            r.require(r.ticks-start<160,"进食结果未确认，停止以核对物品");return false;
        }
        boolean startedOrPreviouslyUsed(){return start!=0;}
        List<Robot.Step> swap(int a,int b,ItemStack first,ItemStack second){
            int from=r.menuSlot(a),to=r.menuSlot(b);var menu=r.mc.player.inventoryMenu;
            return List.of(r.new Click(from,0,()->menu.getSlot(from).getItem().isEmpty()&&ItemStack.matches(menu.getCarried(),first)),r.new Click(to,0,()->ItemStack.matches(menu.getSlot(to).getItem(),first)&&ItemStack.matches(menu.getCarried(),second)),r.new Click(from,0,()->menu.getCarried().isEmpty()&&ItemStack.matches(menu.getSlot(from).getItem(),second)));
        }
    }
}
