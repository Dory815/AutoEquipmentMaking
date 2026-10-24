package io.github.autoequipmentmaking.client.mixin;

import io.github.autoequipmentmaking.client.AutoEquipmentClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Holding use keeps a single food action alive, but must never start a second item action. */
@Mixin(Minecraft.class)
public class FoodUseMixin {
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void preventRepeat(CallbackInfo ci){if(AutoEquipmentClient.robot!=null&&AutoEquipmentClient.robot.ownsEating())ci.cancel();}
}
