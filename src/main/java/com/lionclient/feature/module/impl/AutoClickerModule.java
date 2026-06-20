package com.lionclient.feature.module.impl;

import com.lionclient.feature.module.Category;
import com.lionclient.feature.module.Module;
import com.lionclient.feature.setting.BooleanSetting;
import com.lionclient.feature.setting.EnumSetting;
import com.lionclient.feature.setting.NumberSetting;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/**
 * Left-click auto-clicker. Delegates all click logic to {@link ClickEngine}.
 */
public final class AutoClickerModule extends Module {
    private final ClickEngine engine;

    private final EnumSetting<ClickEngine.Mode> mode = new EnumSetting<ClickEngine.Mode>("Mode", ClickEngine.Mode.values(), ClickEngine.Mode.NORMAL);
    private final BooleanSetting breakBlocks = new BooleanSetting("Break Blocks", true);
    private final BooleanSetting weaponOnly = new BooleanSetting("Weapon Only", false);
    private final BooleanSetting inventoryFill = new BooleanSetting("Inventory Fill", false);
    private final NumberSetting minCps = new NumberSetting("Min CPS", 1, 25, 1, 17);
    private final NumberSetting maxCps = new NumberSetting("Max CPS", 1, 25, 1, 22);
    private final NumberSetting jitterStrength = new NumberSetting("Jitter", 0, 10, 1, 0);

    public AutoClickerModule() {
        super("LeftClicker", "Automatically left-clicks. Use mode Record for strict anticheats like Polar.", Category.LEGIT, Keyboard.KEY_NONE);

        engine = new ClickEngine(this, 0, "[AutoClicker] ",
            mode, breakBlocks, weaponOnly, null, inventoryFill,
            minCps, maxCps, jitterStrength);

        // Visibility conditions
        java.util.function.BooleanSupplier notRecord = () -> mode.getValue() != ClickEngine.Mode.RECORD;
        minCps.setVisibility(notRecord);
        maxCps.setVisibility(notRecord);

        addSetting(mode);
        addSetting(breakBlocks);
        addSetting(weaponOnly);
        addSetting(inventoryFill);
        addSetting(minCps);
        addSetting(maxCps);
        addSetting(jitterStrength);
    }

    @Override
    protected void onEnable() { engine.onEnable(); }

    @Override
    protected void onDisable() { engine.onDisable(); }

    @Override
    public void onRenderTick(TickEvent.RenderTickEvent event) { engine.onRenderTick(event); }
}
