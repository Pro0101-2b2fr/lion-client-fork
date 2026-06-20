package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

/**
 * Chams — renders player models through walls using polygon offset (XQZ).
 * <p>
 * Uses mixins into RendererLivingEntity and TileEntityRendererDispatcher to apply
 * GL_POLYGON_OFFSET_FILL for the through-walls effect. This avoids the GL state
 * corruption (weird textures on health bar, etc.) caused by the old
 * RenderWorldLastEvent + disableDepth() approach.
 */
public final class ChamsModule extends Module {

    public enum Mode {
        SOLID("Solid"),
        WIREFRAME("Wireframe"),
        GHOST("Ghost"),
        GLASS("Glass");

        private final String label;
        Mode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.SOLID);
    private final BooleanSetting tileEntities = new BooleanSetting("Tile Entities", false);

    public ChamsModule() {
        super("Chams", "Renders player models through walls using polygon offset.", Category.RENDER, Keyboard.KEY_NONE);

        addSetting(mode);
        addSetting(tileEntities);
    }

    /**
     * Called from MixinRendererLivingEntity to determine if this entity should have chams applied.
     */
    public static boolean shouldRender(Entity entity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return false;
        if (entity == mc.thePlayer) return false;
        if (entity.isDead) return false;

        if (!(entity instanceof EntityPlayer)) return false;

        EntityPlayer player = (EntityPlayer) entity;

        // AntiBot check
        if (AntiBotModule.shouldIgnore(player)) return false;

        return true;
    }

    /**
     * Called from MixinTileEntityRendererDispatcher.
     */
    public static boolean doRenderTileEntities() {
        ChamsModule module = LionClient.getInstance() != null
                ? LionClient.getInstance().getModuleManager().getModule(ChamsModule.class)
                : null;
        return module != null && module.tileEntities.isEnabled();
    }

    public Mode getMode() { return mode.getValue(); }
    public boolean isXqzEnabled() { return true; } // Always enabled with polygon offset

    @Override
    public String getHudInfo() {
        return mode.getValue().toString();
    }
}