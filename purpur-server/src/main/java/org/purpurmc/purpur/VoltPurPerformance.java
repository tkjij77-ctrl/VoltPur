package org.purpurmc.purpur;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * VoltPur Performance - real, conservative scheduled tasks.
 *
 * HARDENING CHANGES
 *  - ItemLimiter is OPT-IN (default off) and SAFE WHEN ON: it only removes items
 *    that lived at least `min-age-seconds` and (by default) are not named or
 *    enchanted. It broadcasts a warning before removing, and it removes the
 *    OLDEST items first so the loss is predictable. Previously it deleted
 *    arbitrary player items every 5 minutes, by default, silently.
 *  - No empty catch blocks: every task runs through VoltPurGuard, so a broken
 *    module becomes visible in /voltpur modules instead of pretending to work.
 *  - Tasks are owned by VoltPur's internal plugin, so they cannot die when a
 *    third-party plugin is reloaded or disabled.
 */
public class VoltPurPerformance {

    private static final String ITEM_LIMITER = "ItemLimiter";
    private static final String TPS_MONITOR = "TPSMonitor";
    private static final String CHUNK_WARNING = "ChunkWarning";

    private static final int CHUNK_WARN_THRESHOLD = 3000;
    private static boolean initialized = false;

    private VoltPurPerformance() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        VoltPurModules.setRuntime(ITEM_LIMITER, VoltPurConfig.itemLimiterEnabled);
        VoltPurModules.setRuntime(TPS_MONITOR, VoltPurConfig.tpsMonitor);
        VoltPurModules.setRuntime(CHUNK_WARNING, VoltPurConfig.chunkWarning);

        Bukkit.getLogger().info("[VoltPur-Perf] ItemLimiter: "
                + (VoltPurConfig.itemLimiterEnabled ? "ENABLED" : "disabled (opt-in) - nothing is ever removed")
                + " | TPSMonitor: " + (VoltPurConfig.tpsMonitor ? "on" : "off")
                + " | ChunkWarning: " + (VoltPurConfig.chunkWarning ? "on" : "off"));

        try {
            // Item limiter + chunk warning: every 5 minutes, main thread.
            Bukkit.getScheduler().scheduleSyncRepeatingTask(VoltPurPlugin.get(), () -> {
                if (!VoltPurConfig.performanceEnabled) {
                    VoltPurGuard.skip(ITEM_LIMITER);
                    VoltPurGuard.skip(CHUNK_WARNING);
                    return;
                }
                if (VoltPurConfig.itemLimiterEnabled) {
                    VoltPurGuard.run(ITEM_LIMITER, VoltPurPerformance::performCleanup);
                } else {
                    VoltPurGuard.skip(ITEM_LIMITER);
                }
                VoltPurGuard.run(CHUNK_WARNING, VoltPurPerformance::checkChunks);
            }, 6000L, 6000L);

            // TPS monitor: every minute, main thread.
            Bukkit.getScheduler().scheduleSyncRepeatingTask(VoltPurPlugin.get(), () -> {
                if (!VoltPurConfig.performanceEnabled || !VoltPurConfig.tpsMonitor) {
                    VoltPurGuard.skip(TPS_MONITOR);
                    return;
                }
                VoltPurGuard.run(TPS_MONITOR, VoltPurPerformance::checkTps);
            }, 1200L, 1200L);
        } catch (Throwable t) {
            VoltPurGuard.failure(ITEM_LIMITER, t);
            Bukkit.getLogger().warning("[VoltPur-Perf] Could not schedule tasks: " + t.getMessage());
        }
    }

    /**
     * Removes excess dropped items under strict safety rules. Returns how many were removed.
     * Does nothing at all unless modules.performance.item-limiter.enabled=true.
     */
    public static int performCleanup() {
        if (!VoltPurConfig.itemLimiterEnabled) return 0;

        int removedTotal = 0;
        long minAgeTicks = VoltPurConfig.itemLimiterMinAgeSeconds * 20L;

        for (World world : Bukkit.getWorlds()) {
            List<Item> items = new ArrayList<>();
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) items.add(item);
            }
            int cap = VoltPurConfig.itemLimiterMaxPerWorld;
            if (items.size() <= cap) continue;

            List<Item> eligible = new ArrayList<>();
            for (Item item : items) {
                if (isEligible(item, minAgeTicks)) eligible.add(item);
            }
            // Oldest first: what disappears is predictable and least likely to be wanted.
            eligible.sort(Comparator.comparingInt(Item::getTicksLived).reversed());

            int excess = items.size() - cap;
            int toRemove = Math.min(excess, eligible.size());
            if (toRemove <= 0) continue;

            if (VoltPurConfig.itemLimiterWarnPlayers) {
                Bukkit.broadcast(Component.text("[VoltPur] Clearing " + toRemove + " old dropped item stack(s) in '"
                        + world.getName() + "' (over the " + cap + " limit). Named/enchanted items are kept.",
                        NamedTextColor.YELLOW));
            }
            for (int i = 0; i < toRemove; i++) {
                eligible.get(i).remove();
                removedTotal++;
            }
        }

        if (removedTotal > 0) {
            Bukkit.getLogger().info("[VoltPur-Perf] ItemLimiter removed " + removedTotal
                    + " item stack(s) older than " + VoltPurConfig.itemLimiterMinAgeSeconds + "s (cap "
                    + VoltPurConfig.itemLimiterMaxPerWorld + "/world)");
        }
        return removedTotal;
    }

    /** Safety rules that decide whether an item may be removed. */
    private static boolean isEligible(Item item, long minAgeTicks) {
        if (item.getTicksLived() < minAgeTicks) return false;
        if (item.isCustomNameVisible()) return false;
        if (VoltPurConfig.itemLimiterKeepNamed) {
            ItemStack stack = item.getItemStack();
            if (stack == null) return false;
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && (meta.hasDisplayName() || meta.hasEnchants() || meta.isUnbreakable())) return false;
        }
        return true;
    }

    private static void checkTps() {
        double[] tps = Bukkit.getServer().getTPS();
        if (tps[0] < 18.0) {
            Bukkit.getLogger().warning("[VoltPur-Perf] Low TPS: " + String.format("%.2f", tps[0])
                    + " - run /voltpur benchmark and review view-distance/plugins. VoltPur does not change your config automatically.");
        }
    }

    private static void checkChunks() {
        if (!VoltPurConfig.chunkWarning) return;
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            total += world.getLoadedChunks().length;
        }
        if (total > CHUNK_WARN_THRESHOLD) {
            Bukkit.getLogger().warning("[VoltPur-Perf] High loaded chunks: " + total
                    + " - consider lowering view-distance (see /voltpur hardware).");
        }
    }

    /**
     * Hopper statistics for the benchmark. Counter only - never changes state.
     * "Sleepable" = empty inventory, no container above, not powered: the exact
     * condition under which hopper transfer is a guaranteed no-op.
     */
    public static int[] hopperStats() {
        return VoltPurGuard.run("HopperStats", () -> {
            int total = 0;
            int sleepable = 0;
            for (World world : Bukkit.getWorlds()) {
                for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                    for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                        if (!(state instanceof org.bukkit.block.Hopper hopper)) continue;
                        total++;
                        boolean hasContainerAbove = hopper.getBlock()
                                .getRelative(org.bukkit.block.BlockFace.UP).getState()
                                instanceof org.bukkit.inventory.InventoryHolder;
                        boolean powered = hopper.getBlock().isBlockPowered()
                                || hopper.getBlock().isBlockIndirectlyPowered();
                        if (hopper.getInventory().isEmpty() && !hasContainerAbove && !powered) sleepable++;
                    }
                }
            }
            return new int[]{total, sleepable};
        }, new int[]{0, 0});
    }
}
