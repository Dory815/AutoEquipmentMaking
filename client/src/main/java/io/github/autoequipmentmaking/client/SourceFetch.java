package io.github.autoequipmentmaking.client;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Source relocation is recoverable only before a take click has been sent. */
final class SourceFetch implements Robot.Step {
    private static final Map<Robot,Set<BlockPos>> failedOpens=new WeakHashMap<>();
    final Robot r;final Predicate<ItemStack> predicate;final int amount,take;final Robot.Source source;final ItemStack expected;
    Robot.Open open;Robot.Move move;boolean finished;
    SourceFetch(Robot r,Predicate<ItemStack> predicate,int amount,Robot.Source source,int take,ItemStack expected){this.r=r;this.predicate=predicate;this.amount=amount;this.source=source;this.take=take;this.expected=expected.copy();}
    public boolean tick(){
        if(finished)return true;
        // Once a take begins, retain the existing confirmed-click transaction. Never replay it.
        if(move!=null){if(!move.tick())return false;failedOpens.computeIfAbsent(r,k->new HashSet<>()).remove(source.pos());r.close();return true;}
        if(r.inventoryCount(predicate)>=amount)return true;
        if(!r.rawContainer(source.pos()))return rescan("原料容器消失或颜色/类型改变",false);
        if(open==null)open=r.new Open(source.pos(),AbstractContainerMenu.class);
        try{if(!open.tick())return false;}
        catch(IllegalStateException failure){
            if(open.sent&&r.ticks-open.start>=100&&r.mc.player.containerMenu==r.mc.player.inventoryMenu)return rescan("原料容器打开失败",true);
            throw failure;
        }
        int needed=Math.min(take,amount-r.inventoryCount(predicate)),slot=-1;
        for(int i=0;i<r.mc.player.containerMenu.slots.size();i++){
            var s=r.mc.player.containerMenu.slots.get(i);
            if(s.container!=r.mc.player.getInventory()&&predicate.test(s.getItem())&&ItemStack.isSameItemSameComponents(s.getItem(),expected)&&s.getItem().getCount()>=needed){slot=i;break;}
        }
        if(slot<0)return rescan("原料库存或物品位置改变",false);
        move=r.new Move(slot,-1,needed,expected);return false;
    }
    boolean rescan(String why,boolean openFailed){
        r.require(r.mc.player.containerMenu.getCarried().isEmpty(),"光标非空，禁止重新扫描取料");r.close();
        r.record("SOURCE RESCAN "+why+" "+source.pos());
        r.prepend(List.of(r.new Scan(),r.new Fetch(predicate,amount)));
        if(openFailed&&!failedOpens.computeIfAbsent(r,k->new HashSet<>()).add(source.pos())){
            finished=true;
            throw new Robot.Waiting("原料容器重新扫描后仍无法打开："+source.pos().toShortString()+"；请检查场地");
        }
        return true;
    }
}
