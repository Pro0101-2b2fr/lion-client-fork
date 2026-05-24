package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import com.lionclient.gui.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public final class ArmorHudModule extends Module implements HudElement {
    private static final int ITEM_SIZE = 16;
    private static final int ITEM_GAP = 2;

    private final EnumSetting<Direction> direction = new EnumSetting<Direction>("Direction", Direction.values(), Direction.HORIZONTAL);
    private final BooleanSetting showHeldItem = new BooleanSetting("Show Held Item", false);
    private final BooleanSetting showDurability = new BooleanSetting("Show Durability", true);
    private final NumberSetting hudX = new NumberSetting("X", 0, 4000, 1, 90);
    private final NumberSetting hudY = new NumberSetting("Y", 0, 4000, 1, 4000);

    public ArmorHudModule() {
        super("ArmorHUD", "Renders equipped armor pieces with durability.", Category.RENDER, Keyboard.KEY_NONE);
        java.util.function.BooleanSupplier hidden = new java.util.function.BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
                return false;
            }
        };
        hudX.setVisibility(hidden);
        hudY.setVisibility(hidden);
        addSetting(direction);
        addSetting(showHeldItem);
        addSetting(showDurability);
        addSetting(hudX);
        addSetting(hudY);
    }

    @Override
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.gameSettings.showDebugInfo) {
            return;
        }

        InventoryPlayer inv = player.inventory;
        ItemStack[] pieces = new ItemStack[showHeldItem.isEnabled() ? 5 : 4];
        // Vanilla armor slots: 0 = boots, 1 = leggings, 2 = chest, 3 = helmet
        // Render head→feet so the slot order is reversed.
        pieces[0] = inv.armorInventory[3];
        pieces[1] = inv.armorInventory[2];
        pieces[2] = inv.armorInventory[1];
        pieces[3] = inv.armorInventory[0];
        if (showHeldItem.isEnabled()) {
            pieces[4] = player.getHeldItem();
        }

        ScaledResolution res = event.resolution;
        boolean horizontal = direction.getValue() == Direction.HORIZONTAL;
        int totalWidth = horizontal ? pieces.length * (ITEM_SIZE + ITEM_GAP) - ITEM_GAP : ITEM_SIZE;
        int totalHeight = horizontal ? ITEM_SIZE : pieces.length * (ITEM_SIZE + ITEM_GAP) - ITEM_GAP;
        int anchorX = clamp(hudX.getValue(), 0, res.getScaledWidth() - totalWidth);
        int anchorY = clamp(hudY.getValue(), 0, res.getScaledHeight() - totalHeight);

        FontRenderer font = minecraft.fontRendererObj;
        RenderItem renderItem = minecraft.getRenderItem();
        RenderHelper.enableGUIStandardItemLighting();

        for (int i = 0; i < pieces.length; i++) {
            ItemStack stack = pieces[i];
            if (stack == null) {
                continue;
            }
            int slotX = horizontal ? anchorX + i * (ITEM_SIZE + ITEM_GAP) : anchorX;
            int slotY = horizontal ? anchorY : anchorY + i * (ITEM_SIZE + ITEM_GAP);

            renderItem.renderItemAndEffectIntoGUI(stack, slotX, slotY);
            renderItem.renderItemOverlayIntoGUI(font, stack, slotX, slotY, null);

            if (showDurability.isEnabled() && stack.getMaxDamage() > 0) {
                drawDurability(font, stack, slotX, slotY);
            }
        }

        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL11.GL_LIGHTING);
        Gui.drawRect(0, 0, 0, 0, 0); // restore color state
    }

    private void drawDurability(FontRenderer font, ItemStack stack, int slotX, int slotY) {
        int max = stack.getMaxDamage();
        int current = max - stack.getItemDamage();
        if (current >= max) {
            return;
        }
        String label = Integer.toString(current);
        int textX = slotX + (ITEM_SIZE - font.getStringWidth(label)) / 2;
        int textY = slotY + ITEM_SIZE - font.FONT_HEIGHT;
        float ratio = current / (float) max;
        int color = ratio < 0.25F ? 0xFFFF5050 : ratio < 0.5F ? 0xFFFFB055 : 0xFFE0F0E0;
        font.drawStringWithShadow(label, textX, textY, color);
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private int getElementWidth() {
        boolean horizontal = direction.getValue() == Direction.HORIZONTAL;
        int count = showHeldItem.isEnabled() ? 5 : 4;
        return horizontal ? count * (ITEM_SIZE + ITEM_GAP) - ITEM_GAP : ITEM_SIZE;
    }

    private int getElementHeight() {
        boolean horizontal = direction.getValue() == Direction.HORIZONTAL;
        int count = showHeldItem.isEnabled() ? 5 : 4;
        return horizontal ? ITEM_SIZE : count * (ITEM_SIZE + ITEM_GAP) - ITEM_GAP;
    }

    @Override
    public String getHudElementName() {
        return "Armor";
    }

    @Override
    public boolean isHudElementVisible() {
        return isEnabled();
    }

    @Override
    public int getHudX() {
        return hudX.getValue();
    }

    @Override
    public int getHudY() {
        return hudY.getValue();
    }

    @Override
    public int getHudWidth(ScaledResolution resolution) {
        return getElementWidth();
    }

    @Override
    public int getHudHeight(ScaledResolution resolution) {
        return getElementHeight();
    }

    @Override
    public boolean isHudRightAligned(ScaledResolution resolution) {
        return false;
    }

    @Override
    public void setHudPosition(int x, int y) {
        hudX.setManualValue(x);
        hudY.setManualValue(y);
    }

    private enum Direction {
        HORIZONTAL,
        VERTICAL
    }
}
