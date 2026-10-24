package io.github.autoequipmentmaking.client.mixin;
import io.github.autoequipmentmaking.client.Robot;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPacketListener.class)
public class MenuSyncMixin {
    @Inject(method="handleBlockUpdate",at=@At("TAIL"))
    private void block(ClientboundBlockUpdatePacket p,CallbackInfo ci){Robot.confirmBlock(p.getPos());}
    @Inject(method="handleChunkBlocksUpdate",at=@At("TAIL"))
    private void blocks(ClientboundSectionBlocksUpdatePacket p,CallbackInfo ci){p.runUpdates((pos,state)->Robot.confirmBlock(pos));}
    @Inject(method="handleContainerSetSlot",at=@At("TAIL"))
    private void slot(ClientboundContainerSetSlotPacket p,CallbackInfo ci){Robot.confirm(p.getContainerId());var mc=net.minecraft.client.Minecraft.getInstance();if(p.getContainerId()==0&&mc.player!=null&&p.getSlot()>=0&&p.getSlot()<mc.player.inventoryMenu.slots.size()){var s=mc.player.inventoryMenu.getSlot(p.getSlot());if(s.container==mc.player.getInventory())Robot.confirmPlayerSlot(s.getContainerSlot());}}
    @Inject(method="handleContainerContent",at=@At("TAIL"))
    private void content(ClientboundContainerSetContentPacket p,CallbackInfo ci){Robot.confirm(p.containerId());if(p.containerId()==0){for(int i=0;i<36;i++)Robot.confirmPlayerSlot(i);Robot.confirmPlayerSlot(40);}}
    @Inject(method="handleSetPlayerInventory",at=@At("TAIL"))
    private void inventory(ClientboundSetPlayerInventoryPacket p,CallbackInfo ci){Robot.confirmPlayerSlot(p.slot());}
    @Inject(method="handleSetHealth",at=@At("TAIL"))
    private void food(ClientboundSetHealthPacket p,CallbackInfo ci){Robot.confirmFood();}
}
