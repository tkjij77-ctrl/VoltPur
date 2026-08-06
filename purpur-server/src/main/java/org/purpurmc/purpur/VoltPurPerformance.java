package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

/**
 * VoltPur Performance - REAL performance tasks that are actually wired to run.
 *
 * Phase 1 keeps every effect honest:
 *   - ItemLimiter : removes excess dropped items (real, measurable).
 *   - TPSMonitor  : logs when TPS is low (real).
 *   - ChunkCheck  : warns on excessive loaded chunks (real, cheap).
 *   - HopperOptimization / EntityActivation are NOT claimed here (needs NMS,
 *     Phase 2). They are reported as PLANNED in /voltpur modules.
 */
public class VoltPurPerformance {
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        Logger logger = Bukkit.getLogger();
        logger.info("[VoltPur-Perf] Initializing real performance tasks...");

        // Wait for server + a plugin to be available (for the scheduler).
        new Thread(() -> {
            try {
                Thread.sleep(20000);
                Plugin plugin = null;
                for (int i = 0; i < 10 && plugin == null; i++) {
                    try {
                        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
                        if (plugins.length > 0) plugin = plugins[0];
                    } catch (Exception ignored) {}
                    if (plugin == null) Thread.sleep(5000);
                }
                if (plugin == null) {
                    logger.info("[VoltPur-Perf] No plugin found, using fallback timer");
                    startWithGlobalScheduler();
                    return;
                }
                final Plugin p = plugin;
                Bukkit.getScheduler().runTask(p, () -> startRepeatingTasks(p));
            } catch (Exception e) {
                logger.warning("[VoltPur-Perf] Init thread failed: " + e.getMessage());
            }
        }, "VoltPur-Perf-Init").start();

        logger.info("[VoltPur-Perf] Init thread started - tasks activate shortly after startup");
    }

    private static void startWithGlobalScheduler() {
        try {
            java.util.Timer timer = new java.util.Timer(true);
            timer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    try {
                        performCleanup();
                        checkChunks();
                    } catch (Exception ignored) {}
                }
            }, 300000, 300000); // every 5 min
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur-Perf] Fallback timer failed: " + e.getMessage());
        }
    }

    /** The single repeating task loop that does the REAL work. */
    private static void startRepeatingTasks(Plugin plugin) {
        Logger logger = Bukkit.getLogger();

        // Every 5 minutes: item cleanup (real) + chunk warning (real).
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try { if (VoltPurConfig.performanceEnabled) performCleanup(); } catch (Exception ignored) {}
            try { checkChunks(); } catch (Exception ignored) {}
        }, 6000L, 6000L);

        // Every 1 minute: TPS monitor (real).
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try {
                if (!VoltPurConfig.tpsMonitor) return;
                double[] tps = Bukkit.getServer().getTPS();
                if (tps[0] < 18.0) {
                    logger.warning("[VoltPur-Perf] Low TPS: " + String.format("%.2f", tps[0])
                            + " - run /voltpur benchmark and review view-distance / heavy plugins");
                }
            } catch (Exception ignored) {}
        }, 1200L, 1200L);

        logger.info("[VoltPur-Perf] Real tasks started | item limit: "
                + VoltPurConfig.maxItemsPerWorld + "/world | benchmark via /voltpur benchmark");
    }

    /**
     * Real item limiter: removes dropped items above the per-world cap.
     * Returns number removed (for benchmark / logging).
     */
    public static int performCleanup() {
        if (!VoltPurConfig.performanceEnabled) return 0;
        Logger logger = Bukkit.getLogger();
        int removedItems = 0;
        for (World world : Bukkit.getWorlds()) {
            int itemsInWorld = 0;
            try {
                for (Entity ent : world.getEntities()) {
                    if (ent instanceof Item) itemsInWorld++;
                }
            } catch (Exception ignored) { continue; }
            if (itemsInWorld > VoltPurConfig.maxItemsPerWorld) {
                int toRemove = itemsInWorld - VoltPurConfig.maxItemsPerWorld;
                for (Entity ent : world.getEntities()) {
                    if (toRemove <= 0) break;
                    if (ent instanceof Item) {
                        ent.remove(); removedItems++; toRemove--;
                    }
                }
            }
        }
        if (removedItems > 0) {
            logger.info("[VoltPur-Perf] Removed " + removedItems + " excess dropped items (cap "
                    + VoltPurConfig.maxItemsPerWorld + "/world)");
        }
        return removedItems;
    }

    /** Real, cheap chunk warning based on loaded chunks. */
    public static void checkChunks() {
        if (!VoltPurConfig.chunkOptimization) return;
        try {
            int totalChunks = 0;
            for (World world : Bukkit.getWorlds()) {
                totalChunks += world.getLoadedChunks().length;
            }
            if (totalChunks > 3000) {
                Bukkit.getLogger().warning("[VoltPur-Perf] High loaded chunks: " + totalChunks
                        + " - consider reducing view-distance (see /voltpur hardware)");
            }
        } catch (Exception ignored) {}
    }

    /**
     * Hopper candidate count for Phase-2 planning (does NOT modify hoppers).
     * Not wired into the repeating loop: it must not print fake savings.
     * In Phase 2 this becomes a real NMS patch (hopper cooldown sleep).
     */
    public static int countSleepableHoppers() {
        int candidates = 0;
        try {
            for (World world : Bukkit.getWorlds()) {
                for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                    try {
                        for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                            if (state instanceof org.bukkit.block.Hopper) {
                                org.bukkit.block.Hopper hopper = (org.bukkit.block.Hopper) state;
                                boolean hasInventoryAbove =
                                        hopper.getBlock().getRelative(org.bukkit.block.BlockFace.UP).getState()
                                                instanceof org.bukkit.inventory.InventoryHolder;
                                boolean powered = hopper.getBlock().isBlockPowered()
                                        || hopper.getBlock().isBlockIndirectlyPowered();
                                if (hopper.getInventory().isEmpty() && !hasInventoryAbove && !powered) {
                                    candidates++;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return candidates;
    }
}
