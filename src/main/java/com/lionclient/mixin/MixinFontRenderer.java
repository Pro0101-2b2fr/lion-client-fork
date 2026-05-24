package com.lionclient.mixin;

import com.lionclient.feature.module.impl.NameProtectModule;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FontRenderer.class)
public abstract class MixinFontRenderer {
    @ModifyVariable(method = "drawString(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), ordinal = 0)
    private String lionclient$protectNameDrawString(String text) {
        return NameProtectModule.protect(text);
    }

    @ModifyVariable(method = "getStringWidth", at = @At("HEAD"), ordinal = 0)
    private String lionclient$protectNameGetWidth(String text) {
        return NameProtectModule.protect(text);
    }
}
