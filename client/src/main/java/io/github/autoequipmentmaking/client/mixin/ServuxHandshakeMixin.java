package io.github.autoequipmentmaking.client.mixin;

import io.github.autoequipmentmaking.client.ServuxSession;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="fi.dy.masa.minihud.data.EntityDataManager",remap=false)
public class ServuxHandshakeMixin {
    @Inject(method="receiveServuxMetadata(Lnet/minecraft/nbt/CompoundTag;)Z",at=@At("HEAD"),cancellable=true,remap=false)
    private void aem$metadata(CompoundTag nbt,CallbackInfoReturnable<Boolean> callback){
        if(!net.minecraft.client.Minecraft.getInstance().hasSingleplayerServer()&&!ServuxSession.metadata(nbt))callback.setReturnValue(false);
    }
}
