package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * VoltPur Performance - REAL performance tasks that are actually wired to run.
 *
 * Phase 1 keeps every effect honest:
 *   - ItemLimiter : removes excess dropped items with smart proximity and age heuristics.
 *   - TPSMonitor  : logs when TPS is low.
 *   - ChunkCheck  : warns on excessive loaded chunks.
 */
public class VoltPurPerformance {
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        Logger logger = Bukkit.getLogger();
        logger.info("[VoltPur-Perf] Initializing real performance tasks...");

        VoltPurPlugin.whenAvailable(() -> {
            org.bukkit.plugin.Plugin p = VoltPurPlugin.get();
            if (p == null) return;
            Bukkit.getScheduler().scheduleSyncRepeatingTask(p, () -> {
                try { if (VoltPurConfig.performanceEnabled) performCleanup(); } catch (Exception ignored) {}
                try { checkChunks(); } catch (Exception ignored) {}
            }, 6000L, 6000L);
            Bukkit.getScheduler().scheduleSyncRepeatingTask(p, () -> {
                try {
                    if (!VoltPurConfig.tpsMonitor) return;
                    double[] tps = Bukkit.getServer().getTPS();
                    if (tps[0] < 18.0) {
                        Bukkit.getLogger().warning("[VoltPur-Perf] Low TPS: " + String.format("%.2f", tps[0])
                                + " - run /voltpur benchmark and review view-distance / heavy plugins");
                    }
                } catch (Exception ignored) {}
            }, 1200L, 1200L);
            logger.info("[VoltPur-Perf] Tasks started on main thread via Bukkit scheduler.");
        });
        logger.info("[VoltPur-Perf] Init complete");
    }

    /**
     * Smart item limiter: removes excess dropped items above the per-world cap.
     * When autotuneSmartItemLimiter is enabled:
     * - Young items (< autotuneItemExemptTicks) are protected.
     * - Items near players (< autotuneItemExemptRadius) are protected.
     * - Oldest eligible items are pruned first.
     */
    public static int performCleanup() {
        if (!VoltPurConfig.performanceEnabled) return 0;
        Logger logger = Bukkit.getLogger();
        int removedItems = 0;

        for (World world : Bukkit.getWorlds()) {
            List<Item> allItems = new ArrayList<>();
            try {
                for (Entity ent : world.getEntities()) {
                    if (ent instanceof Item) {
                        allItems.add((Item) ent);
                    }
                }
            } catch (Exception ignored) {
                continue;
            }

            int itemsInWorld = allItems.size();
            if (itemsInWorld > VoltPurConfig.maxItemsPerWorld) {
                int toRemove = itemsInWorld - VoltPurConfig.maxItemsPerWorld;

                if (VoltPurConfig.autotuneSmartItemLimiter) {
                    List<Player> players = new ArrayList<>();
                    try {
                        if (world.getPlayers() != null) {
                            players.addAll(world.getPlayers());
                        }
                    } catch (Throwable ignored) {}

                    double exemptRadiusSq = VoltPurConfig.autotuneItemExemptRadius * VoltPurConfig.autotuneItemExemptRadius;
                    List<Item> candidates = new ArrayList<>();

                    for (Item item : allItems) {
                        if (item.getTicksLived() < VoltPurConfig.autotuneItemExemptTicks) {
                            continue; // Exempt: too young
                        }

                        boolean nearPlayer = false;
                        Location itemLoc = item.getLocation();
                        if (itemLoc != null) {
                            for (Player player : players) {
                                Location pLoc = player.getLocation();
                                if (pLoc != null && pLoc.getWorld() == itemLoc.getWorld()) {
                                    if (pLoc.distanceSquared(itemLoc) <= exemptRadiusSq) {
                                        nearPlayer = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (!nearPlayer) {
                            candidates.add(item);
                        }
                    }

                    // Sort candidates oldest first (highest ticksLived)
                    candidates.sort((a, b) -> Integer.compare(b.getTicksLived(), a.getTicksLived()));

                    for (Item candidate : candidates) {
                        if (toRemove <= 0) break;
                        candidate.remove();
                        removedItems++;
                        toRemove--;
                    }
                } else {
                    for (Item item : allItems) {
                        if (toRemove <= 0) break;
                        item.remove();
                        removedItems++;
                        toRemove--;
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
