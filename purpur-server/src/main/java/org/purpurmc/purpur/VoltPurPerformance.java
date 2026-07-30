
package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

/**
 * VoltPur Performance - Real performance improvements via Bukkit API
 */
public class VoltPurPerformance {
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        Logger logger = Bukkit.getLogger();
        logger.info("[VoltPur-Perf] Initializing real performance improvements...");

        // Start task in new thread that waits for server and plugins to be ready
        new Thread(() -> {
            try {
                Thread.sleep(30000); // Wait 30 seconds for server to fully start
                Plugin plugin = null;
                int attempts = 0;
                while (plugin == null && attempts < 10) {
                    try {
                        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
                        if (plugins.length > 0) {
                            plugin = plugins[0];
                            break;
                        }
                    } catch (Exception e) {}
                    try { Thread.sleep(5000); } catch (Exception e) {}
                    attempts++;
                }
                if (plugin == null) {
                    // Fallback: try to get any plugin, or use first world
                    logger.info("[VoltPur-Perf] No plugin found yet, using delayed init");
                    Bukkit.getScheduler().runTaskLater(Bukkit.getWorlds().isEmpty() ? null : Bukkit.getPluginManager().getPlugins().length > 0 ? Bukkit.getPluginManager().getPlugins()[0] : null, () -> startPerformanceTasksInternal(), 100L);
                    return;
                }
                final Plugin p = plugin;
                // Now schedule repeating tasks using the found plugin
                Bukkit.getScheduler().runTask(p, () -> startPerformanceTasksInternalWithPlugin(p));
            } catch (Exception e) {
                logger.warning("[VoltPur-Perf] Init thread failed: " + e.getMessage());
            }
        }, "VoltPur-Perf-Init").start();

        logger.info("[VoltPur-Perf] Performance init thread started - will activate after 30s");
    }

    private static void startPerformanceTasksInternal() {
        // Try to find plugin again
        try {
            Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
            if (plugins.length > 0) {
                startPerformanceTasksInternalWithPlugin(plugins[0]);
            } else {
                Bukkit.getLogger().info("[VoltPur-Perf] No plugins found for scheduler, using global scheduler");
                startWithGlobalScheduler();
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur-Perf] Failed to start tasks: " + e.getMessage());
        }
    }

    private static void startWithGlobalScheduler() {
        try {
            // Try Paper's GlobalRegionScheduler which may not need plugin in newer versions
            // Fallback to simple timer
            Bukkit.getLogger().info("[VoltPur-Perf] Using fallback timer for performance tasks");
            java.util.Timer timer = new java.util.Timer(true);
            timer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    try {
                        // Run sync via global scheduler if available
                        Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugins()[0], () -> performCleanup());
                    } catch (Exception e) {
                        try {
                            // Direct cleanup if scheduler fails
                            performCleanup();
                        } catch (Exception ex) {}
                    }
                }
            }, 300000, 300000); // Every 5 minutes
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur-Perf] Global scheduler failed: " + e.getMessage());
        }
    }

    private static void startPerformanceTasksInternalWithPlugin(Plugin plugin) {
        Logger logger = Bukkit.getLogger();
        if (plugin == null) {
            startWithGlobalScheduler();
            return;
        }

        // Task 1: Entity and item cleanup every 5 minutes
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try {
                performCleanup();
            } catch (Exception e) {}
        }, 6000L, 6000L);

        // Task 2: TPS monitor every 10 minutes
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            try {
                if (!VoltPurConfig.tpsMonitor) return;
                double[] tps = Bukkit.getServer().getTPS();
                if (tps[0] < 18.0) {
                    logger.warning("[VoltPur-Perf] Low TPS: " + String.format("%.2f", tps[0]) + " - consider reducing entities/redstone/hoppers");
                }
            } catch (Exception e) {}
        }, 12000L, 12000L);

        logger.info("[VoltPur-Perf] Performance tasks started with plugin " + plugin.getName() + " - item limit: " + VoltPurConfig.maxItemsPerWorld + " per world");
    }

    private static void performCleanup() {
        if (!VoltPurConfig.performanceEnabled) return;
        Logger logger = Bukkit.getLogger();
        int totalEntities = 0;
        int removedItems = 0;
        for (World world : Bukkit.getWorlds()) {
            int itemsInWorld = 0;
            for (Entity ent : world.getEntities()) {
                totalEntities++;
                if (ent instanceof Item) itemsInWorld++;
            }
            if (itemsInWorld > VoltPurConfig.maxItemsPerWorld) {
                int toRemove = itemsInWorld - VoltPurConfig.maxItemsPerWorld;
                for (Entity ent : world.getEntities()) {
                    if (toRemove <= 0) break;
                    if (ent instanceof Item) {
                        ent.remove();
                        removedItems++;
                        toRemove--;
                    }
                }
            }
        }
        if (removedItems > 0) {
            logger.info("[VoltPur-Perf] Cleared " + removedItems + " dropped items (limit: " + VoltPurConfig.maxItemsPerWorld + " per world)");
        }
    }

    public static void onServerStart() {
        Bukkit.getLogger().info("[VoltPur-Perf] Server started - performance active | Max items/world: " + VoltPurConfig.maxItemsPerWorld);
    }
}
