package io.github.autoequipmentmaking.client.mixin;
import io.github.autoequipmentmaking.client.AutoEquipmentClient;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MultiPlayerGameMode.class)
public class ExternalInventoryMixin {
    @Inject(method="stopDestroyBlock",at=@At("HEAD"),cancellable=true)
    private void keepTaskMining(CallbackInfo ci){if(AutoEquipmentClient.robot!=null&&AutoEquipmentClient.robot.ownsMining())ci.cancel();}
    @Inject(method="handleContainerInput",at=@At("HEAD"),cancellable=true)
    private void external(int containerId,int slot,int button,ContainerInput input,Player player,CallbackInfo ci){
        if(AutoEquipmentClient.robot!=null&&AutoEquipmentClient.robot.isRunning()){
            AutoEquipmentClient.robot.externalInput();ci.cancel();
        }
    }
}
