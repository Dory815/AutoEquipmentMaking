package io.github.autoequipmentmaking.client.mixin;

import io.github.autoequipmentmaking.client.AutoEquipmentClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LocalPlayer.class)
public class NavigationJumpMixin {
    @Shadow private int autoJumpTime;
    private boolean active(){return AutoEquipmentClient.robot!=null&&AutoEquipmentClient.robot.ownsNavigation();}
    @Inject(method="canAutoJump",at=@At("HEAD"),cancellable=true)
    private void planned(CallbackInfoReturnable<Boolean> ci){if(active())ci.setReturnValue(false);}
    @Inject(method="aiStep",at=@At("HEAD"))
    private void clearPending(CallbackInfo ci){if(active())autoJumpTime=0;}
}
