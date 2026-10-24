package io.github.autoequipmentmaking.client.mixin;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import io.github.autoequipmentmaking.client.RobotSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scale only text emitted by the AEM settings screen; keep widget hitboxes unchanged. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class SettingsTextScaleMixin {
    @Unique private boolean aem$textScaled;

    @Inject(method="text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",at=@At("HEAD"))
    private void aem$beforeText(Font font,FormattedCharSequence sequence,int x,int y,int color,boolean shadow,CallbackInfo ci){
        aem$textScaled=false;
        if(!(Minecraft.getInstance().gui.screen() instanceof GuiConfigsBase screen)||!"auto_equipment_making".equals(screen.getModId()))return;
        float scale=RobotSettings.guiFontScale();
        if(scale==1f)return;
        Matrix3x2fStack pose=((GuiGraphicsExtractor)(Object)this).pose();
        // Keep the fixed-size reset button legible at every scale.
        StringBuilder value=new StringBuilder();
        sequence.accept((index,style,codePoint)->{value.appendCodePoint(codePoint);return true;});
        if(value.toString().equals("RESET"))return;
        float centerX=x<RobotSettings.labelColumnWidth()?x:x+font.width(sequence)/2f;
        float centerY=y+font.lineHeight/2f;
        pose.pushMatrix();
        pose.translate(centerX,centerY);pose.scale(scale,scale);pose.translate(-centerX,-centerY);
        aem$textScaled=true;
    }

    @Inject(method="text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",at=@At("RETURN"))
    private void aem$afterText(Font font,FormattedCharSequence sequence,int x,int y,int color,boolean shadow,CallbackInfo ci){
        if(aem$textScaled){((GuiGraphicsExtractor)(Object)this).pose().popMatrix();aem$textScaled=false;}
    }
}
