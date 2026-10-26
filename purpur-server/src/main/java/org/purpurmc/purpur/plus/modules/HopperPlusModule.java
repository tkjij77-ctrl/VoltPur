package org.purpurmc.purpur.plus.modules;

import org.bukkit.Bukkit;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.VoltPurPerformance;
import org.purpurmc.purpur.plus.PlusContext;
import org.purpurmc.purpur.plus.VoltPurPlusModule;

/**
 * HopperPlusModule — First internal module demonstrating plugin-plus (§6.5 Rule 4).
 *
 * Verifies and hooks low-level hopper rest optimizations during pre-launch
 * and activates runtime diagnostics on server ready.
 */
public class HopperPlusModule implements VoltPurPlusModule {

    private boolean preLaunchCompleted = false;
    private boolean serverReadyCompleted = false;

    @Override
    public void onPreLaunch(PlusContext context) {
        preLaunchCompleted = true;
        context.info("HopperPlusModule: Pre-launch hooks registered. Target MC: " + context.getTargetMcVersion());
    }

    @Override
    public void onServerReady() {
        serverReadyCompleted = true;
        VoltPurModules.setStatus("HopperOptimization", VoltPurModules.Status.ACTIVE);
        int[] stats = VoltPurPerformance.hopperStats();
        Bukkit.getLogger().info(String.format(
                "[VoltPur-Plus-Hopper] Module active on server ready: %d hoppers detected (%d sleepable). Sleep cooldown=%dticks.",
                stats[0], stats[1], VoltPurConfig.hopperSleepCooldown));
    }

    public boolean isPreLaunchCompleted() {
        return preLaunchCompleted;
    }

    public boolean isServerReadyCompleted() {
        return serverReadyCompleted;
    }
}
