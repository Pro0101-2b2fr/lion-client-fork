package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.ColorSetting;
import com.lionclient.feature.setting.DecimalSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.gui.ShaderUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.glu.GLU;

public final class PlayerEspModule extends Module {
    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.MODERN);
    private final ColorSetting classicColor = new ColorSetting("Classic Color", 0xFFFF3C3C);
    private final BooleanSetting seeInvis = new BooleanSetting("See Invis", false);
    private final BooleanSetting showHealth = new BooleanSetting("Show Health", true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", false);
    private final BooleanSetting hostileOnly = new BooleanSetting("Hostile Only", false);
    private final BooleanSetting animals = new BooleanSetting("Animals", true);
    private final DecimalSetting lineWidth =
        new DecimalSetting("Line Width", 1.0D, 5.0D, 0.5D, 2.5D);
    private final DecimalSetting glowRadius =
        new DecimalSetting("Glow Radius", 1.0D, 4.0D, 0.5D, 2.5D);
    private final ColorSetting glowColor = new ColorSetting("Glow Color", 0xFF4A9EFF);
    private final BooleanSetting glowRainbow = new BooleanSetting("Glow Animated", true);

    // Reusable buffers for 3D→2D projection (no per-frame allocation).
    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer screenCoords = BufferUtils.createFloatBuffer(4);
    // Screen-space boxes collected during the world render, drawn in the 2D overlay.
    private final List<float[]> twoDBoxes = new ArrayList<float[]>();
    private int twoDCount;
    // Reused per-frame to avoid allocating in the render loop (AGENTS.md perf rule).
    private final float[] projectOut = new float[2];
    private final float[] colorBuf = new float[3];
    private int frameScaleFactor = 1;

    // Shader-outline ("Glow") state.
    private final ShaderUtils outlineShader = new ShaderUtils(OUTLINE_FRAGMENT);
    private Framebuffer glowFbo;

    public PlayerEspModule() {
        super("PlayerESP", "Draws a box around players and mobs through walls.", Category.RENDER, Keyboard.KEY_NONE);
        classicColor.setVisibility(() -> mode.getValue() == Mode.CLASSIC);
        lineWidth.setVisibility(() -> mode.getValue() == Mode.OUTLINE);
        glowRadius.setVisibility(() -> mode.getValue() == Mode.GLOW);
        glowColor.setVisibility(() -> mode.getValue() == Mode.GLOW && !glowRainbow.isEnabled());
        glowRainbow.setVisibility(() -> mode.getValue() == Mode.GLOW);
        hostileOnly.setVisibility(mobs::isEnabled);
        animals.setVisibility(() -> mobs.isEnabled() && !hostileOnly.isEnabled());
        addSetting(mode);
        addSetting(classicColor);
        addSetting(lineWidth);
        addSetting(glowRadius);
        addSetting(glowColor);
        addSetting(glowRainbow);
        addSetting(seeInvis);
        addSetting(showHealth);
        addSetting(mobs);
        addSetting(hostileOnly);
        addSetting(animals);
    }

    @Override
    public void onRenderWorld(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || minecraft.thePlayer == null) {
            return;
        }

        float partialTicks = event.partialTicks;
        Mode m = mode.getValue();
        double viewerX = minecraft.getRenderManager().viewerPosX;
        double viewerY = minecraft.getRenderManager().viewerPosY;
        double viewerZ = minecraft.getRenderManager().viewerPosZ;

        // 2D mode: project boxes here (matrices are valid), draw later in the overlay.
        if (m == Mode.TWO_D) {
            twoDCount = 0;
            captureMatrices();
            int screenHeight = minecraft.displayHeight;
            for (Object object : minecraft.theWorld.loadedEntityList) {
                if (!(object instanceof EntityLivingBase)) continue;
                EntityLivingBase entity = (EntityLivingBase) object;
                if (!shouldRender(minecraft, entity)) continue;
                collectTwoD(minecraft, entity, partialTicks, viewerX, viewerY, viewerZ, screenHeight);
            }
            return;
        }

        // Glow (shader outline): render target models into an offscreen buffer now;
        // the outline shader composites it onto the screen in the overlay pass.
        if (m == Mode.GLOW) {
            renderGlowSilhouette(minecraft, partialTicks, viewerX, viewerY, viewerZ);
            return;
        }

        boolean modern = m == Mode.MODERN;
        boolean outline = m == Mode.OUTLINE;
        float viewYaw = minecraft.getRenderManager().playerViewY;

        GL11.glPushMatrix();
        try {
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            if (outline) {
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
                GL11.glLineWidth((float) lineWidth.getValue());
            } else {
                GL11.glLineWidth(1.8F);
            }

            for (Object object : minecraft.theWorld.loadedEntityList) {
                if (!(object instanceof EntityLivingBase)) {
                    continue;
                }
                EntityLivingBase entity = (EntityLivingBase) object;
                if (!shouldRender(minecraft, entity)) {
                    continue;
                }
                renderEntity(minecraft, entity, partialTicks, viewerX, viewerY, viewerZ, m, viewYaw);
            }
        } finally {
            GL11.glLineWidth(1.0F);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GlStateManager.enableCull();
            GlStateManager.disableLighting();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
        }
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (mode.getValue() == Mode.GLOW) {
            compositeGlow(Minecraft.getMinecraft());
            return;
        }
        if (mode.getValue() != Mode.TWO_D || twoDCount == 0) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        for (int idx = 0; idx < twoDCount; idx++) {
            float[] b = twoDBoxes.get(idx);
            int x1 = (int) b[0];
            int y1 = (int) b[1];
            int x2 = (int) b[2];
            int y2 = (int) b[3];
            int color = packColor(b[4], b[5], b[6], 1.0F);
            // Outline rectangle (1px borders).
            Gui.drawRect(x1, y1, x2, y1 + 1, color);
            Gui.drawRect(x1, y2 - 1, x2, y2, color);
            Gui.drawRect(x1, y1, x1 + 1, y2, color);
            Gui.drawRect(x2 - 1, y1, x2, y2, color);

            // Vertical health bar on the left edge.
            float ratio = b[7];
            int barH = (int) ((y2 - y1) * ratio);
            int hr = ratio > 0.5F ? (int) ((1.0F - ratio) * 2.0F * 255) : 255;
            int hg = ratio > 0.5F ? 255 : (int) (ratio * 2.0F * 255);
            int hcolor = 0xFF000000 | (clamp255(hr) << 16) | (clamp255(hg) << 8);
            Gui.drawRect(x1 - 3, y1, x1 - 1, y2, 0x90000000);
            Gui.drawRect(x1 - 3, y2 - barH, x1 - 1, y2, hcolor);
        }
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Projects the entity's bounding box to screen space and stores the resulting
     * 2D rectangle (in scaled GUI coordinates) for the overlay pass.
     */
    private void collectTwoD(Minecraft mc, EntityLivingBase entity, float partialTicks,
                             double viewerX, double viewerY, double viewerZ, int screenHeight) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - viewerX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - viewerY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - viewerZ;

        AxisAlignedBB bb = entity.getEntityBoundingBox();
        double minX = bb.minX - entity.posX + x;
        double minY = bb.minY - entity.posY + y;
        double minZ = bb.minZ - entity.posZ + z;
        double maxX = bb.maxX - entity.posX + x;
        double maxY = bb.maxY - entity.posY + y;
        double maxZ = bb.maxZ - entity.posZ + z;

        double left = Double.MAX_VALUE, top = Double.MAX_VALUE, right = -1.0D, bottom = -1.0D;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            double cx = (i & 1) == 0 ? minX : maxX;
            double cy = (i & 2) == 0 ? minY : maxY;
            double cz = (i & 4) == 0 ? minZ : maxZ;
            if (!project(cx, cy, cz, screenHeight)) {
                return; // a corner is behind the camera → skip this entity
            }
            any = true;
            if (projectOut[0] < left) left = projectOut[0];
            if (projectOut[0] > right) right = projectOut[0];
            if (projectOut[1] < top) top = projectOut[1];
            if (projectOut[1] > bottom) bottom = projectOut[1];
        }
        if (!any || right <= left || bottom <= top) {
            return;
        }

        float[] colors = getModernColor(entity);
        float ratio = entity.getMaxHealth() > 0.0F
            ? Math.max(0.0F, Math.min(1.0F, entity.getHealth() / entity.getMaxHealth()))
            : 0.0F;
        addTwoDBox((float) left, (float) top, (float) right, (float) bottom,
            colors[0], colors[1], colors[2], ratio);
    }

    /** Stores a 2D box, reusing pooled arrays so frames don't allocate. */
    private void addTwoDBox(float left, float top, float right, float bottom,
                            float r, float g, float b, float ratio) {
        float[] arr;
        if (twoDCount < twoDBoxes.size()) {
            arr = twoDBoxes.get(twoDCount);
        } else {
            arr = new float[8];
            twoDBoxes.add(arr);
        }
        arr[0] = left; arr[1] = top; arr[2] = right; arr[3] = bottom;
        arr[4] = r; arr[5] = g; arr[6] = b; arr[7] = ratio;
        twoDCount++;
    }

    private void captureMatrices() {
        modelView.clear();
        projection.clear();
        viewport.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        // Cache the scale factor once per frame instead of building a
        // ScaledResolution on every one of the 8 corner projections per entity.
        frameScaleFactor = new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor();
    }

    /** Writes scaled-GUI screen coords into {@link #projectOut}; false if off-screen. */
    private boolean project(double x, double y, double z, int screenHeight) {
        screenCoords.clear();
        if (!GLU.gluProject((float) x, (float) y, (float) z, modelView, projection, viewport, screenCoords)) {
            return false;
        }
        float sz = screenCoords.get(2);
        if (sz < 0.0F || sz > 1.0F) {
            return false; // behind the near/far plane
        }
        projectOut[0] = screenCoords.get(0) / frameScaleFactor;
        projectOut[1] = (screenHeight - screenCoords.get(1)) / frameScaleFactor;
        return true;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int packColor(float r, float g, float b, float a) {
        return ((int) (a * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // ── Shader outline ("Glow") ──────────────────────────────────────────────

    /** Renders the target models into the offscreen buffer (alpha = silhouette). */
    private void renderGlowSilhouette(Minecraft mc, float partialTicks, double vx, double vy, double vz) {
        if (!outlineShader.ensureCompiled() || !ensureGlowFbo(mc)) {
            return;
        }
        try {
            glowFbo.framebufferClear();
            glowFbo.bindFramebuffer(true);
            RenderHelper.enableStandardItemLighting();
            for (Object object : mc.theWorld.loadedEntityList) {
                if (!(object instanceof EntityLivingBase)) {
                    continue;
                }
                EntityLivingBase entity = (EntityLivingBase) object;
                if (!shouldRender(mc, entity)) {
                    continue;
                }
                double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - vx;
                double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - vy;
                double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - vz;
                float yaw = entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks;
                @SuppressWarnings("rawtypes")
                Render render = mc.getRenderManager().getEntityRenderObject(entity);
                if (render != null) {
                    render.doRender(entity, x, y, z, yaw, partialTicks);
                }
            }
            RenderHelper.disableStandardItemLighting();
        } catch (Throwable ignored) {
            // Never let the ESP crash the world render.
        } finally {
            mc.getFramebuffer().bindFramebuffer(true);
        }
    }

    /** Runs the outline shader over the silhouette buffer onto the screen. */
    private void compositeGlow(Minecraft mc) {
        if (glowFbo == null || !outlineShader.ensureCompiled()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        float w = (float) sr.getScaledWidth_double();
        float h = (float) sr.getScaledHeight_double();
        float[] col = glowColorRgb();

        try {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.disableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            outlineShader.use();
            int p = outlineShader.getProgram();
            GL20.glUniform1i(GL20.glGetUniformLocation(p, "sceneTex"), 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(p, "texelSize"),
                1.0F / glowFbo.framebufferTextureWidth, 1.0F / glowFbo.framebufferTextureHeight);
            GL20.glUniform1f(GL20.glGetUniformLocation(p, "radius"), (float) glowRadius.getValue());
            GL20.glUniform3f(GL20.glGetUniformLocation(p, "outlineColor"), col[0], col[1], col[2]);

            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            glowFbo.bindFramebufferTexture();
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(0.0F, 0.0F);
            GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(0.0F, h);
            GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(w, h);
            GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(w, 0.0F);
            GL11.glEnd();
            glowFbo.unbindFramebufferTexture();
            ShaderUtils.stop();
        } catch (Throwable ignored) {
        } finally {
            GlStateManager.enableAlpha();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private boolean ensureGlowFbo(Minecraft mc) {
        try {
            // Render the silhouette at half resolution: re-rendering every target
            // model is the heavy part, and a half-res buffer cuts that fill cost ~4x
            // (and makes the outline shader sample a much smaller, cache-friendly
            // texture). The result is upscaled when composited — fine for a glow.
            int w = Math.max(1, mc.displayWidth / 2);
            int h = Math.max(1, mc.displayHeight / 2);
            if (glowFbo == null) {
                glowFbo = new Framebuffer(w, h, true);
                glowFbo.setFramebufferFilter(GL11.GL_LINEAR);
            } else if (glowFbo.framebufferWidth != w || glowFbo.framebufferHeight != h) {
                glowFbo.createBindFramebuffer(w, h);
                glowFbo.setFramebufferFilter(GL11.GL_LINEAR);
            }
            return true;
        } catch (Throwable t) {
            glowFbo = null;
            return false;
        }
    }

    private float[] glowColorRgb() {
        int rgb;
        if (glowRainbow.isEnabled()) {
            double time = System.currentTimeMillis() / 340.0D;
            float wave = (float) ((Math.sin(time) + 1.0D) * 0.5D);
            rgb = ClickGuiModule.blendColor(ClickGuiModule.getLightAccentColor(), ClickGuiModule.getDarkAccentColor(), wave);
        } else {
            rgb = glowColor.getRgb();
        }
        return new float[] {
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >> 8) & 0xFF) / 255.0F,
            (rgb & 0xFF) / 255.0F
        };
    }

    private boolean shouldRender(Minecraft minecraft, EntityLivingBase entity) {
        if (entity == minecraft.thePlayer || entity.isDead || entity.getHealth() <= 0.0F) {
            return false;
        }
        if (!seeInvis.isEnabled() && entity.isInvisible()) {
            return false;
        }

        if (entity instanceof EntityPlayer) {
            return !AntiBotModule.shouldIgnore((EntityPlayer) entity);
        }

        // Non-player living entity → only when "Mobs" is on, with hostile/animal filters.
        if (!mobs.isEnabled()) {
            return false;
        }
        boolean hostile = entity instanceof IMob;
        if (hostileOnly.isEnabled()) {
            return hostile;
        }
        return hostile || animals.isEnabled();
    }

    private void renderEntity(Minecraft minecraft, EntityLivingBase entity, float partialTicks,
                              double viewerX, double viewerY, double viewerZ, Mode m, float viewYaw) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks - viewerX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks - viewerY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks - viewerZ;

        AxisAlignedBB bb = entity.getEntityBoundingBox();
        AxisAlignedBB renderBox = new AxisAlignedBB(
            bb.minX - entity.posX + x,
            bb.minY - entity.posY + y,
            bb.minZ - entity.posZ + z,
            bb.maxX - entity.posX + x,
            bb.maxY - entity.posY + y,
            bb.maxZ - entity.posZ + z
        ).expand(0.05D, 0.1D, 0.05D);

        boolean classic = m == Mode.CLASSIC;
        float[] colors = classic ? getClassicColor() : getModernColor(entity);
        if (m == Mode.MODERN) {
            drawFilledBox(renderBox, colors[0], colors[1], colors[2], 0.12F);
        }
        float lineAlpha = m == Mode.OUTLINE ? 1.0F : (m == Mode.MODERN ? 0.95F : 1.0F);
        drawOutlinedBox(renderBox, colors[0], colors[1], colors[2], lineAlpha);

        if (showHealth.isEnabled()) {
            drawHealthBar(renderBox, entity.getHealth(), entity.getMaxHealth(), viewYaw);
        }
    }

    /**
     * Health bar rendered as a cylindrical billboard (rotated to face the camera
     * around the Y axis). A fixed-plane quad would foreshorten to a thin sliver
     * when viewed edge-on; billboarding keeps a constant on-screen width at any
     * viewing angle.
     */
    private void drawHealthBar(AxisAlignedBB bb, float health, float maxHealth, float viewYaw) {
        float ratio = maxHealth > 0.0F ? Math.max(0.0F, Math.min(1.0F, health / maxHealth)) : 0.0F;
        double cx = (bb.minX + bb.maxX) / 2.0D;
        double cz = (bb.minZ + bb.maxZ) / 2.0D;
        double height = bb.maxY - bb.minY;
        double radius = Math.max(bb.maxX - bb.minX, bb.maxZ - bb.minZ) / 2.0D + 0.1D;

        float r = ratio > 0.5F ? (1.0F - ratio) * 2.0F : 1.0F;
        float g = ratio > 0.5F ? 1.0F : ratio * 2.0F;

        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, bb.minY, cz);
        GlStateManager.rotate(-viewYaw, 0.0F, 1.0F, 0.0F);

        double bx = -radius - 0.08D; // place the bar just left of the box, facing camera
        double bw = 0.08D;

        // Background
        GlStateManager.color(0.2F, 0.2F, 0.2F, 0.6F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(bx, 0.0D, 0.0D);
        GL11.glVertex3d(bx, height, 0.0D);
        GL11.glVertex3d(bx + bw, height, 0.0D);
        GL11.glVertex3d(bx + bw, 0.0D, 0.0D);
        GL11.glEnd();

        // Fill (from bottom up)
        GlStateManager.color(r, g, 0.0F, 0.85F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(bx, 0.0D, 0.0D);
        GL11.glVertex3d(bx, height * ratio, 0.0D);
        GL11.glVertex3d(bx + bw, height * ratio, 0.0D);
        GL11.glVertex3d(bx + bw, 0.0D, 0.0D);
        GL11.glEnd();

        GlStateManager.popMatrix();
    }

    private void drawOutlinedBox(AxisAlignedBB bb, float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a);
        GL11.glBegin(GL11.GL_LINES);

        vertex(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ);
        vertex(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ);
        vertex(bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);
        vertex(bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ);

        vertex(bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        vertex(bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
        vertex(bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        vertex(bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ);

        vertex(bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        vertex(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ);
        vertex(bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ);
        vertex(bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);

        GL11.glEnd();
    }

    private void drawFilledBox(AxisAlignedBB bb, float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);

        quad(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.minX, bb.maxY, bb.minZ);
        quad(bb.minX, bb.minY, bb.maxZ, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        quad(bb.minX, bb.minY, bb.minZ, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ);
        quad(bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, bb.maxX, bb.maxY, bb.minZ);
        quad(bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ);
        quad(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ);

        GL11.glEnd();
    }

    private void vertex(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    private void quad(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glVertex3d(x3, y3, z3);
        GL11.glVertex3d(x4, y4, z4);
    }

    private float[] getClassicColor() {
        int rgb = classicColor.getRgb();
        colorBuf[0] = ((rgb >> 16) & 0xFF) / 255.0F;
        colorBuf[1] = ((rgb >> 8) & 0xFF) / 255.0F;
        colorBuf[2] = (rgb & 0xFF) / 255.0F;
        return colorBuf;
    }

    private float[] getModernColor(Entity entity) {
        double time = System.currentTimeMillis() / 340.0D;
        float wave = (float) ((Math.sin(time + (entity.getEntityId() * 0.35D)) + 1.0D) * 0.5D);
        int color = ClickGuiModule.blendColor(ClickGuiModule.getLightAccentColor(), ClickGuiModule.getDarkAccentColor(), wave);
        colorBuf[0] = ((color >>> 16) & 255) / 255.0F;
        colorBuf[1] = ((color >>> 8) & 255) / 255.0F;
        colorBuf[2] = (color & 255) / 255.0F;
        return colorBuf;
    }

    @Override
    protected void onDisable() {
        if (glowFbo != null) {
            try {
                glowFbo.deleteFramebuffer();
            } catch (Throwable ignored) {
            }
            glowFbo = null;
        }
    }

    private static final String OUTLINE_FRAGMENT =
        "#version 120\n"
        + "uniform sampler2D sceneTex;\n"
        + "uniform vec2 texelSize;\n"
        + "uniform float radius;\n"
        + "uniform vec3 outlineColor;\n"
        + "const int MAX_R = 4;\n"
        + "void main() {\n"
        + "    vec2 uv = gl_TexCoord[0].st;\n"
        + "    float center = texture2D(sceneTex, uv).a;\n"
        + "    if (center > 0.05) { discard; }\n"
        + "    float sum = 0.0;\n"
        + "    for (int x = -MAX_R; x <= MAX_R; x++) {\n"
        + "        for (int y = -MAX_R; y <= MAX_R; y++) {\n"
        + "            if (float(x * x + y * y) > radius * radius) continue;\n"
        + "            sum += texture2D(sceneTex, uv + vec2(float(x), float(y)) * texelSize).a;\n"
        + "        }\n"
        + "    }\n"
        + "    if (sum > 0.05) {\n"
        + "        gl_FragColor = vec4(outlineColor, 1.0);\n"
        + "    } else {\n"
        + "        discard;\n"
        + "    }\n"
        + "}\n";

    private enum Mode {
        MODERN("Modern"),
        CLASSIC("Classic"),
        OUTLINE("Outline"),
        GLOW("Glow"),
        TWO_D("2D");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
