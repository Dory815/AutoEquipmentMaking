package io.github.autoequipmentmaking.client;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Stores only task-owned purchases/currency into empty raw-source slots when the backpack is full. */
final class ProcurementStorage implements Robot.Step {
    final Procurement procurement;final Robot r;final int desiredFree;final boolean currencyAllowed;
    Robot.Scan scan;List<BlockPos> candidates;int index;long requested;Robot.Open open;Robot.Move move;
    ProcurementStorage(Procurement procurement,int desiredFree,boolean currencyAllowed){this.procurement=procurement;r=procurement.r;this.desiredFree=desiredFree;this.currencyAllowed=currencyAllowed;scan=r.new Scan();}
    boolean eligible(ItemStack stack){return procurement.product(stack)||(currencyAllowed&&(Stacks.base(stack,"minecraft:emerald")||Stacks.base(stack,"minecraft:book")||Stacks.base(stack,"minecraft:emerald_block")));}
    int source(){for(int i=0;i<36;i++)if(!r.protectedSlots.contains(i)&&!r.mc.player.getInventory().getItem(i).isEmpty()&&eligible(r.mc.player.getInventory().getItem(i)))return i;return -1;}
    void next(){r.close();index++;requested=0;open=null;r.stepStart=r.ticks;}
    void waitForSpace(String message){
        r.require(move==null&&r.mc.player.containerMenu.getCarried().isEmpty(),"暂存移动未确认，禁止重新排队");
        r.close();scan=r.new Scan();candidates=null;index=0;requested=0;open=null;
        r.record("PRETRADE STORAGE WAIT refresh after good");throw new Robot.Waiting(message);
    }
    public boolean tick(){
        r.state="STORING PREPURCHASES";
        if(move!=null){if(!move.tick())return false;move=null;r.stepStart=r.ticks;r.record("PRETRADE STORED task item in raw source");}
        if(procurement.free()>=desiredFree){r.close();r.prepend(List.of(r.new Scan()));return true;}
        if(source()<0){waitForSpace("没有可暂存的任务物品，请腾出背包空间后继续集中采购");return false;}
        if(candidates==null){if(!scan.tick())return false;candidates=new ArrayList<>(scan.positions);candidates.sort(Comparator.comparingDouble(p->p.distSqr(r.cfg.station("crafting"))));}
        if(index>=candidates.size()){waitForSpace("原料容器没有空槽可暂存预购物品，请腾出未染色潜影盒或普通箱子的空间");return false;}
        BlockPos pos=candidates.get(index);
        if(!r.rawContainer(pos)){next();return false;}
        if(open==null){
            if(requested==0){r.observer.refresh(pos);requested=r.ticks;return false;}
            var cached=r.observer.cached(pos);if(cached==null){if(r.ticks-requested>=120)next();return false;}
            boolean empty=false;for(int i=0;i<cached.getContainerSize();i++)if(cached.getItem(i).isEmpty()){empty=true;break;}
            if(!empty){next();return false;}open=r.new Open(pos,AbstractContainerMenu.class);
        }
        try{if(!open.tick())return false;}
        catch(IllegalStateException failure){if(open.sent&&r.ticks-open.start>=100&&r.mc.player.containerMenu==r.mc.player.inventoryMenu){next();return false;}throw failure;}
        int destination=-1;var menu=r.mc.player.containerMenu;
        for(int i=0;i<menu.slots.size();i++)if(menu.slots.get(i).container!=r.mc.player.getInventory()&&menu.slots.get(i).getItem().isEmpty()){destination=i;break;}
        if(destination<0){next();return false;}
        int source=source();if(source<0){waitForSpace("任务物品位置改变，请核对背包后发送 good");return false;}
        var stack=r.mc.player.getInventory().getItem(source).copy();move=r.new Move(r.menuSlot(source),destination,stack.getCount(),stack);return false;
    }
}
