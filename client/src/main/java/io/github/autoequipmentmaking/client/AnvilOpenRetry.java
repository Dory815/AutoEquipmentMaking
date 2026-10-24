package io.github.autoequipmentmaking.client;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Only retries opening an empty menu. Never retries a transfer, placement or result pickup. */
final class AnvilOpenRetry {
    private static final Map<Robot.Open,Attempt> ATTEMPTS=new WeakHashMap<>();
    private static final class Attempt {int count,levels;final List<ItemStack> inventory=new ArrayList<>();}
    static void sent(Robot r,Robot.Open open){
        var a=ATTEMPTS.computeIfAbsent(open,k->new Attempt());a.count++;a.levels=r.mc.player.experienceLevel;a.inventory.clear();
        for(int i=0;i<36;i++)a.inventory.add(r.mc.player.getInventory().getItem(i).copy());
    }
    static boolean retry(Robot r,Robot.Open open){
        var a=ATTEMPTS.get(open);if(a==null||a.count>=2||r.mc.player.containerMenu!=r.mc.player.inventoryMenu||!r.mc.player.containerMenu.getCarried().isEmpty()||r.mc.player.experienceLevel!=a.levels)return false;
        for(int i=0;i<36;i++)if(!ItemStack.matches(a.inventory.get(i),r.mc.player.getInventory().getItem(i)))return false;
        open.sent=false;open.nav=null;r.record("ANVIL OPEN RETRY: no menu, inventory and XP unchanged; attempt="+(a.count+1));return true;
    }
    static void complete(Robot.Open open){ATTEMPTS.remove(open);}
    static BlockHitResult hit(Robot r,BlockPos p){
        Vec3 eye=r.mc.player.getEyePosition(),center=Vec3.atCenterOf(p);
        var ray=r.mc.level.clip(new ClipContext(eye,center,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,r.mc.player));
        if(ray.getType()==HitResult.Type.BLOCK&&ray.getBlockPos().equals(p))return ray;
        Vec3 delta=eye.subtract(center);Direction face=Direction.getApproximateNearest(delta.x,delta.y,delta.z);
        return new BlockHitResult(center.add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5),face,p,false);
    }
}
