package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
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

        // Use Paper's GlobalRegionScheduler (runs on the main thread, allows null
        // plugin, so it works even on a server with NO plugins). NEVER use a raw
        // java.util.Timer or a custom thread for world access: AsyncCatcher will
        // reject it. We schedule on the global region scheduler directly.
        startWithRegionScheduler();
        logger.info("[VoltPur-Perf] Init complete");
    }

    /**
     * Schedules repeating tasks on Paper's GlobalRegionScheduler, which executes on
     * the main thread and accepts a null plugin (safe for plugin-less servers).
     * If unavailable, tasks simply do not auto-run; they can still be triggered
     * manually via /voltpur benchmark (which runs on the main thread).
     */
    private static void startWithRegionScheduler() {
        try {
            io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler sched =
                    Bukkit.getGlobalRegionScheduler();
            if (sched == null) {
                Bukkit.getLogger().info("[VoltPur-Perf] GlobalRegionScheduler unavailable - auto tasks off (use /voltpur benchmark).");
                return;
            }
            sched.runAtFixedRate(null, task -> {
                try { if (VoltPurConfig.performanceEnabled) performCleanup(); } catch (Exception ignored) {}
                try { checkChunks(); } catch (Exception ignored) {}
            }, 6000L, 6000L);
            sched.runAtFixedRate(null, task -> {
                try {
                    if (!VoltPurConfig.tpsMonitor) return;
                    double[] tps = Bukkit.getServer().getTPS();
                    if (tps[0] < 18.0) {
                        Bukkit.getLogger().warning("[VoltPur-Perf] Low TPS: " + String.format("%.2f", tps[0])
                                + " - run /voltpur benchmark and review view-distance / heavy plugins");
                    }
                } catch (Exception ignored) {}
            }, 1200L, 1200L);
            Bukkit.getLogger().info("[VoltPur-Perf] Tasks started on GlobalRegionScheduler (main thread, safe).");
        } catch (Throwable e) {
            Bukkit.getLogger().warning("[VoltPur-Perf] Region scheduler unavailable: "
                    + (e.getMessage() == null ? e.toString() : e.getMessage())
                    + " - auto cleanup disabled (no async errors). Use /voltpur benchmark to run manually.");
        }
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
     * Hopper stats for the benchmark (does NOT modify hoppers).
     * Returns { totalHoppers, sleepableHoppers }.
     * "Sleepable" = empty inventory + no container above + not powered, i.e. the
     * exact safe condition under which tryMoveItems is a guaranteed no-op. This
     * is a COUNTER only (never changes hopper state), so it never fakes savings.
     */
    public static int[] hopperStats() {
        int total = 0;
        int sleepable = 0;
        try {
            for (World world : Bukkit.getWorlds()) {
                for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                    try {
                        for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                            if (state instanceof org.bukkit.block.Hopper) {
                                total++;
                                org.bukkit.block.Hopper hopper = (org.bukkit.block.Hopper) state;
                                boolean hasInventoryAbove =
                                        hopper.getBlock().getRelative(org.bukkit.block.BlockFace.UP).getState()
                                                instanceof org.bukkit.inventory.InventoryHolder;
                                boolean powered = hopper.getBlock().isBlockPowered()
                                        || hopper.getBlock().isBlockIndirectlyPowered();
                                if (hopper.getInventory().isEmpty() && !hasInventoryAbove && !powered) {
                                    sleepable++;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return new int[]{total, sleepable};
    }
}
