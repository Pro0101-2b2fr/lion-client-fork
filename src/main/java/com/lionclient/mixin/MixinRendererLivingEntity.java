package com.lionclient.mixin;

import com.lionclient.LionClient;
import com.lionclient.feature.module.impl.ChamsModule;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> {

    @Inject(method = "doRender", at = @At("HEAD"))
    private void lionclient$chamsPre(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        ChamsModule chamsModule = LionClient.getInstance() != null ? LionClient.getInstance().getModuleManager().getModule(ChamsModule.class) : null;
        if (chamsModule == null || !chamsModule.isEnabled()) return;

        if (!ChamsModule.shouldRender(entity)) return;

        // Render through walls by forcing the depth test to always pass instead of
        // an extreme polygon offset. glPolygonOffset(1, -1000000) underflows the
        // computed depth, which then gets clipped by the near plane at certain
        // angles/distances — that was the "model sometimes disappears" bug.
        GL11.glDepthFunc(GL11.GL_ALWAYS);

        // Apply mode-specific GL state
        switch (chamsModule.getMode()) {
            case WIREFRAME:
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
                GL11.glLineWidth(2.0F);
                break;
            case GHOST:
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                break;
            case GLASS:
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                break;
            case SOLID:
            default:
                // Solid keeps default state, just rendered on top via GL_ALWAYS.
                break;
        }
    }

    @Inject(method = "doRender", at = @At("RETURN"))
    private void lionclient$chamsPost(T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        ChamsModule chamsModule = LionClient.getInstance() != null ? LionClient.getInstance().getModuleManager().getModule(ChamsModule.class) : null;
        if (chamsModule == null || !chamsModule.isEnabled()) return;

        if (!ChamsModule.shouldRender(entity)) return;

        // Restore mode-specific GL state
        switch (chamsModule.getMode()) {
            case WIREFRAME:
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
                GL11.glEnable(GL11.GL_CULL_FACE);
                GL11.glLineWidth(1.0F);
                break;
            case GHOST:
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glEnable(GL11.GL_CULL_FACE);
                break;
            case GLASS:
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glEnable(GL11.GL_CULL_FACE);
                break;
            case SOLID:
            default:
                break;
        }

        // Restore the normal depth comparison used by the rest of the world render.
        GL11.glDepthFunc(GL11.GL_LEQUAL);
    }
}