package io.github.autoequipmentmaking.client;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Replenishes only configured anvil pads; placement is sent once and confirmed by server state. */
final class AnvilSupply implements Robot.Step {
    private static final Map<Robot,State> STATES=new WeakHashMap<>();
    private static final class State {BlockPos available,placement;long revision;}
    private static State state(Robot r){return STATES.computeIfAbsent(r,k->new State());}
    static void reset(Robot r){STATES.remove(r);}
    static void confirmBlock(Robot r,BlockPos p){var s=state(r);if(p.equals(s.placement))s.revision++;}
    final Robot r;
    boolean refreshed;
    AnvilSupply(Robot r){this(r,false);}
    private AnvilSupply(Robot r,boolean refreshed){this.r=r;this.refreshed=refreshed;}
    static boolean spare(ItemStack s){return Stacks.base(s,"minecraft:anvil")||Stacks.base(s,"minecraft:chipped_anvil")||Stacks.base(s,"minecraft:damaged_anvil");}
    static String carried(Robot r){
        for(Item item:List.of(Items.ANVIL,Items.CHIPPED_ANVIL,Items.DAMAGED_ANVIL))for(int i=0;i<36;i++)if(!r.protectedSlots.contains(i)&&r.mc.player.getInventory().getItem(i).is(item)&&spare(r.mc.player.getInventory().getItem(i)))return Stacks.id(r.mc.player.getInventory().getItem(i));
        return null;
    }
    static BlockPos available(Robot r){
        var state=state(r);
        if(state.available!=null&&r.inside(state.available)&&r.mc.level.getBlockState(state.available).getBlock() instanceof AnvilBlock&&!(r.mc.level.getBlockState(state.available.below()).getBlock() instanceof AnvilBlock)&&Block.canSupportCenter(r.mc.level,state.available.below(),Direction.UP))return state.available;
        for(var entry:r.cfg.root.getAsJsonObject("workstations").getAsJsonArray("anvils")){
            var p=WorkConfig.read(entry);if(r.inside(p)&&r.mc.level.getBlockState(p).getBlock() instanceof AnvilBlock&&!(r.mc.level.getBlockState(p.below()).getBlock() instanceof AnvilBlock)&&Block.canSupportCenter(r.mc.level,p.below(),Direction.UP)){state.available=p;return p;}
        }
        // Includes anvils that fell after a supporting anvil broke, and replenishment placed by players.
        for(BlockPos p:BlockPos.betweenClosed(r.cfg.min(),r.cfg.max())){
            r.require(r.mc.level.hasChunkAt(p),"铁砧区域未加载，库存未确认");
            if(r.mc.level.getBlockState(p).getBlock() instanceof AnvilBlock&&!(r.mc.level.getBlockState(p.below()).getBlock() instanceof AnvilBlock)&&Block.canSupportCenter(r.mc.level,p.below(),Direction.UP)){state.available=p.immutable();return state.available;}
        }return null;
    }
    static BlockPos pad(Robot r){
        for(var entry:r.cfg.root.getAsJsonObject("workstations").getAsJsonArray("anvils")){
            var p=WorkConfig.read(entry);if(safePad(r,p))return p;
        }return null;
    }
    private static boolean safePad(Robot r,BlockPos p){
        var below=p.below();var support=r.mc.level.getBlockState(below);
        return r.inside(p)&&r.inside(below)&&r.mc.level.getBlockState(p).isAir()&&r.mc.level.getFluidState(p).isEmpty()
                &&Block.canSupportCenter(r.mc.level,below,Direction.UP)&&support.getMenuProvider(r.mc.level,below)==null;
    }
    @Override public boolean tick(){
        BlockPos existing=available(r);
        if(existing!=null){if(r.mc.player.containerMenu instanceof AnvilMenu menu&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&menu.getCarried().isEmpty())return true;r.prepend(List.of(r.new Open(existing,AnvilMenu.class)));return true;}
        r.close();
        if(!r.allowSpareAnvils)throw new Robot.Waiting("铁砧缺货：设置禁止取出备用铁砧，请直接补放铁砧");
        if(!refreshed){r.prepend(List.of(r.new Scan(),new AnvilSupply(r,true)));return true;}
        String selected=carried(r);
        if(selected==null)for(String id:List.of("minecraft:anvil","minecraft:chipped_anvil","minecraft:damaged_anvil"))if(r.count(r.base(id))>0){selected=id;break;}
        if(selected==null){refreshed=false;throw new Robot.Waiting("铁砧缺货：可用铁砧耗尽，原料箱内没有备用铁砧");}
        BlockPos target=pad(r);
        if(target==null){refreshed=false;throw new Robot.Waiting("铁砧工作位不可放置：请清空配置位置或直接补放铁砧");}
        String id=selected;r.record("ANVIL RESTOCK source="+id+" target="+target);
        r.prepend(List.of(r.new Fetch(r.base(id),1),new Place(r,target,id),new AnvilSupply(r)));return true;
    }
    static final class Place implements Robot.Step {
        final Robot r;final BlockPos target;final String id;
        Robot.Navigator nav;int hand=-1,beforeCount;long sentAt,revision;boolean sent;
        ItemStack before;
        Place(Robot r,BlockPos target,String id){this.r=r;this.target=target;this.id=id;}
        @Override public boolean tick(){
            var mc=r.mc;
            if(!sent){
                r.close();
                if(available(r)!=null)return true;
                if(!safePad(r,target))throw new Robot.Waiting("铁砧工作位发生变化，请清空或补放铁砧");
                if(hand<0){
                    int slot=r.inventorySlot(r.base(id));hand=slot;
                    if(slot>=9){
                        hand=-1;for(int i=0;i<9;i++)if(!r.protectedSlots.contains(i)&&mc.player.getInventory().getItem(i).isEmpty()){hand=i;break;}
                        var moves=new ArrayList<Robot.Step>();
                        if(hand<0){for(int i=0;i<9;i++)if(!r.protectedSlots.contains(i)){hand=i;break;}r.require(hand>=0,"没有可用于放置铁砧的任务快捷栏槽");var displaced=mc.player.getInventory().getItem(hand).copy();moves.add(r.new Move(r.menuSlot(hand),r.menuSlot(r.empty()),displaced.getCount(),displaced));}
                        moves.add(r.new Move(r.menuSlot(slot),r.menuSlot(hand),1,mc.player.getInventory().getItem(slot)));moves.add(this);r.prepend(moves);return true;
                    }
                }
                if(nav==null)nav=r.new Navigator(target);if(!nav.tick())return false;
                if(mc.player.getBoundingBox().intersects(new AABB(target)))throw new Robot.Waiting("铁砧工作位被玩家占用，请让出位置");
                r.require(mc.player.containerMenu==mc.player.inventoryMenu&&mc.player.containerMenu.getCarried().isEmpty(),"放置铁砧前菜单或光标非空");
                before=mc.player.getInventory().getItem(hand).copy();r.require(r.base(id).test(before),"待放置铁砧发生变化");beforeCount=before.getCount();
                mc.player.getInventory().setSelectedSlot(hand);mc.getConnection().send(new ServerboundSetCarriedItemPacket(hand));
                Vec3 hit=Vec3.atBottomCenterOf(target);r.look(hit);revision=state(r).revision;state(r).placement=target;
                r.record("ANVIL PLACE INTENT "+target);sent=true;sentAt=r.ticks;
                // No client-side inventory or world prediction: both changes must arrive from the server.
                try(var prediction=prediction(r).startPredicting()){
                    mc.getConnection().send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,new BlockHitResult(hit,Direction.UP,target.below(),false),prediction.currentSequence()));
                }return false;
            }
            var held=mc.player.getInventory().getItem(hand);
            boolean consumed=beforeCount==1?held.isEmpty():ItemStack.isSameItemSameComponents(held,before)&&held.getCount()==beforeCount-1;
            if(state(r).revision>revision&&mc.level.getBlockState(target).getBlock()==((BlockItem)before.getItem()).getBlock()&&consumed){
                state(r).placement=null;state(r).available=target;r.record("ANVIL PLACE CONFIRMED "+target);return true;
            }
            r.require(r.ticks-sentAt<120,"ACTION_UNCERTAIN：铁砧放置未确认，禁止重复放置");return false;
        }
    }
    private static net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler prediction(Robot r){
        try{var method=net.minecraft.client.multiplayer.ClientLevel.class.getDeclaredMethod("getBlockStatePredictionHandler");method.setAccessible(true);return (net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler)method.invoke(r.mc.level);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("铁砧交互序列 API 不兼容",e);}
    }
}
