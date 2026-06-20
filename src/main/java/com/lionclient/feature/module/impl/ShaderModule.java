package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;

/**
 * Shaders — applies one of Minecraft's built-in GLSL post-processing shaders
 * (the "super secret settings" effects, shipped in assets/minecraft/shaders/post)
 * as a fullscreen effect through vanilla's {@code EntityRenderer} shader pipeline.
 *
 * <p>Reuses {@code EntityRenderer.loadShader} / {@code stopUseShader}, so it needs
 * no custom framebuffer or GLSL plumbing and behaves exactly like vanilla shaders.</p>
 */
public final class ShaderModule extends Module {

    public enum Shader {
        BLUR("Blur", "blur"),
        DESATURATE("Desaturate", "desaturate"),
        INVERT("Invert", "invert"),
        GREEN("Green", "green"),
        SOBEL("Sobel", "sobel"),
        OUTLINE("Outline", "outline"),
        PENCIL("Pencil", "pencil"),
        BITS("Bits", "bits"),
        ART("Art", "art"),
        BLOBS("Blobs", "blobs"),
        BLOBS2("Blobs 2", "blobs2"),
        BUMPY("Bumpy", "bumpy"),
        CONVOLVE("Convolve", "color_convolve"),
        DECONVERGE("Deconverge", "deconverge"),
        CREEPER("Creeper", "creeper"),
        SPIDER("Spider", "spider"),
        NTSC("NTSC", "ntsc"),
        NOTCH("Notch", "notch"),
        PHOSPHOR("Phosphor", "phosphor"),
        PINCUSHION("Pincushion", "scan_pincushion"),
        WOBBLE("Wobble", "wobble"),
        FLIP("Flip", "flip"),
        MOTION_BLUR("Motion Blur", "motion_blur");

        private final String label;
        private final String file;

        Shader(String label, String file) {
            this.label = label;
            this.file = file;
        }

        public String location() {
            return "shaders/post/" + file + ".json";
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final EnumSetting<Shader> shader = new EnumSetting<Shader>("Shader", Shader.values(), Shader.BLUR);

    private final Minecraft mc = Minecraft.getMinecraft();
    private Shader applied;
    private boolean warned;
    private boolean failed;

    public ShaderModule() {
        super("Shaders", "Applies a built-in GLSL post-processing shader to the screen.", Category.RENDER, Keyboard.KEY_NONE);
        addSetting(shader);
    }

    @Override
    protected void onEnable() {
        warned = false;
        failed = false;
        applied = null;
        applyShader();
    }

    @Override
    protected void onDisable() {
        stopShader();
    }

    @Override
    public void onClientTick() {
        if (!OpenGlHelper.shadersSupported) {
            return;
        }
        // Selection changed → (re)load it and clear any previous failure.
        if (applied != shader.getValue()) {
            failed = false;
            applyShader();
            return;
        }
        // If a load failed, don't keep retrying every tick (that recreates the
        // ShaderGroup each tick and tanks FPS). Only re-apply when vanilla dropped
        // a previously-working shader (resize / world reload).
        if (failed) {
            return;
        }
        if (!mc.entityRenderer.isShaderActive()) {
            applyShader();
        }
    }

    private void applyShader() {
        if (mc.entityRenderer == null) {
            return;
        }
        if (!OpenGlHelper.shadersSupported) {
            if (!warned && mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    "\u00a78[\u00a7bLion\u00a78] \u00a7cShaders require OpenGL 2.1+ (not supported here)."));
                warned = true;
            }
            return;
        }
        Shader selected = shader.getValue();
        mc.entityRenderer.stopUseShader();
        mc.entityRenderer.loadShader(new ResourceLocation(selected.location()));
        applied = selected;
        // If the shader didn't actually become active, the JSON/program failed to
        // load — stop retrying until the user picks a different shader.
        failed = !mc.entityRenderer.isShaderActive();
    }

    private void stopShader() {
        if (applied != null && mc.entityRenderer != null) {
            mc.entityRenderer.stopUseShader();
        }
        applied = null;
    }

    @Override
    public String getHudInfo() {
        return shader.getValue().toString();
    }
}
