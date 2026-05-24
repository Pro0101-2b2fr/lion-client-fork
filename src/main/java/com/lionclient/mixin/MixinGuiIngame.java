package com.lionclient.mixin;

import com.lionclient.feature.module.impl.CleanScoreboardModule;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.scoreboard.ScoreObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiIngame.class)
public abstract class MixinGuiIngame {
    /**
     * Redirects the score number string to empty when CleanScoreboard is active.
     * The vanilla method calls {@code EnumChatFormatting.RED + "" + score.getScorePoints()}
     * and draws it. We redirect the drawString call's text argument.
     */
    @ModifyArg(
        method = "renderScoreboard",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/FontRenderer;drawString(Ljava/lang/String;III)I", ordinal = 1),
        index = 0
    )
    private String lionclient$hideScoreNumber(String text) {
        if (CleanScoreboardModule.shouldHideNumbers()) {
            return "";
        }
        return text;
    }
}
