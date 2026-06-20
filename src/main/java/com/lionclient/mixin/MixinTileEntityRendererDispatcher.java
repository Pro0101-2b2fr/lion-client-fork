package com.lionclient.mixin;

import com.lionclient.LionClient;
import com.lionclient.feature.module.impl.ChamsModule;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcher {

    @Inject(method = "renderTileEntity", at = @At("HEAD"))
    private void lionclient$chamsPre(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        ChamsModule chamsModule = LionClient.getInstance() != null ? LionClient.getInstance().getModuleManager().getModule(ChamsModule.class) : null;
        if (chamsModule == null || !chamsModule.isEnabled()) return;

        if (!ChamsModule.doRenderTileEntities()) return;

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.0F, -1000000F);
    }

    @Inject(method = "renderTileEntity", at = @At("RETURN"))
    private void lionclient$chamsPost(TileEntity tileEntity, float partialTicks, int destroyStage, CallbackInfo ci) {
        ChamsModule chamsModule = LionClient.getInstance() != null ? LionClient.getInstance().getModuleManager().getModule(ChamsModule.class) : null;
        if (chamsModule == null || !chamsModule.isEnabled()) return;

        if (!ChamsModule.doRenderTileEntities()) return;

        GL11.glPolygonOffset(1.0F, 1000000F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }
}