package io.github.autoequipmentmaking.client.mixin;

import io.github.autoequipmentmaking.client.AutoEquipmentClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Navigation owns jump decisions; preserve the user's auto-jump setting outside navigation. */
@Mixin(LocalPlayer.class)
public class NavigationInputMixin {
    @Shadow private int autoJumpTime;
    private boolean aemNavigating(){return AutoEquipmentClient.robot!=null&&AutoEquipmentClient.robot.ownsNavigation();}
    @Inject(method="canAutoJump",at=@At("HEAD"),cancellable=true)
    private void plannedJumpOnly(CallbackInfoReturnable<Boolean> ci){if(aemNavigating())ci.setReturnValue(false);}
    @Inject(method="aiStep",at=@At("HEAD"))
    private void clearQueuedAutoJump(CallbackInfo ci){if(aemNavigating())autoJumpTime=0;}
}
