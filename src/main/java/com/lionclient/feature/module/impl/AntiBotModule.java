package com.lionclient.feature.module.impl;

import com.lionclient.LionClient;
import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.EnumSetting;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

/**
 * Filters NPCs from real players. Three matching strategies:
 *
 * <ul>
 *   <li>{@code TAB_LIST} - default, matches the previous behaviour. Players
 *     not present in the tab list are treated as bots.</li>
 *   <li>{@code STRICT} - same as TAB_LIST plus an extra check on the entity
 *     UUID version (real Mojang accounts are always v4 UUIDs).</li>
 *   <li>{@code DISTANCE} - lenient mode that only flags entities within
 *     suspiciously close range AND missing from the tab list, useful on
 *     servers where staff in vanish persists in the tab list.</li>
 * </ul>
 */
public final class AntiBotModule extends Module {
    private final EnumSetting<Mode> mode = new EnumSetting<Mode>("Mode", Mode.values(), Mode.TAB_LIST);

    public AntiBotModule() {
        super("AntiBot", "Filters NPCs from players.", Category.CLIENT, Keyboard.KEY_NONE);
        addSetting(mode);
    }

    public static boolean shouldIgnore(EntityPlayer player) {
        LionClient client = LionClient.getInstance();
        if (client == null) {
            return false;
        }

        AntiBotModule antiBot = client.getModuleManager().getModule(AntiBotModule.class);
        return antiBot != null && antiBot.isEnabled() && antiBot.isBot(player);
    }

    public boolean isBot(EntityPlayer player) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (player == null || minecraft.thePlayer == null || minecraft.theWorld == null) {
            return false;
        }
        if (player == minecraft.thePlayer) {
            return false;
        }

        boolean inTab = isInTabList(minecraft, player);
        switch (mode.getValue()) {
            case STRICT:
                if (!inTab) {
                    return true;
                }
                UUID uuid = player.getUniqueID();
                return uuid != null && uuid.version() != 4;
            case DISTANCE:
                if (inTab) {
                    return false;
                }
                double distSq = minecraft.thePlayer.getDistanceSqToEntity(player);
                return distSq < 144.0D; // 12 blocks
            case TAB_LIST:
            default:
                return !inTab;
        }
    }

    private boolean isInTabList(Minecraft minecraft, EntityPlayer player) {
        Collection<NetworkPlayerInfo> playerInfoMap = minecraft.getNetHandler() == null
            ? null
            : minecraft.getNetHandler().getPlayerInfoMap();
        if (playerInfoMap == null || playerInfoMap.isEmpty()) {
            return true;
        }

        for (NetworkPlayerInfo playerInfo : playerInfoMap) {
            if (playerInfo == null || playerInfo.getGameProfile() == null) {
                continue;
            }
            if (player.getUniqueID().equals(playerInfo.getGameProfile().getId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getHudInfo() {
        return mode.getValue().label;
    }

    private enum Mode {
        TAB_LIST("tab"),
        STRICT("strict"),
        DISTANCE("distance");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            switch (this) {
                case TAB_LIST: return "Tab List";
                case STRICT: return "Strict";
                case DISTANCE: return "Distance";
                default: return name();
            }
        }
    }
}
