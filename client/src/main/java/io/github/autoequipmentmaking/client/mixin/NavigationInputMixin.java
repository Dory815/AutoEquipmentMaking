package io.github.autoequipmentmaking.client.mixin;

import io.github.autoequipmentmaking.client.AutoEquipmentClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Analog forward input for braking at narrow anchors, without changing player velocity. */
@Mixin(ClientInput.class)
public class NavigationInputMixin {
    @Inject(method="getMoveVector",at=@At("RETURN"),cancellable=true)
    private void navigationScale(CallbackInfoReturnable<Vec2> ci){
        var r=AutoEquipmentClient.robot;
        if(r!=null&&r.ownsNavigation())ci.setReturnValue(new Vec2(0,r.navigationInput()));
    }
}
