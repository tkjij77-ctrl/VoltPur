package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * VoltPur Plugin owner helper.
 *
 * Paper's schedulers (Bukkit.getScheduler(), etc.) require a NON-NULL owning
 * plugin, and scheduling a task on a plugin that is not yet ENABLED throws
 * "Plugin attempted to register task while disabled". VoltPur runs inside the
 * server (not as a plugin), and at init time (PurpurConfig.init) plugins are
 * only just being loaded. This helper waits until a plugin is actually ENABLED,
 * then returns it so tasks can be scheduled safely.
 */
public final class VoltPurPlugin {

    private static volatile Plugin cached;
    private static volatile Plugin internal;

    private VoltPurPlugin() {}

    /**
     * Shared internal owner used when the server has NO real plugins (VoltPur runs
     * inside the server, not as a plugin). This is EXACTLY how Purpur itself owns
     * its own scheduled tasks (see BossBarTask / BeehiveTask, which schedule with
     * `new MinecraftInternalPlugin()`), so the Bukkit scheduler accepts it and our
     * modules actually run. isEnabled() returns true, which is all the scheduler
     * needs.
     */
    private static Plugin internal() {
        if (internal == null) {
            synchronized (VoltPurPlugin.class) {
                if (internal == null) {
                    internal = new org.purpurmc.purpur.util.MinecraftInternalPlugin();
                }
            }
        }
        return internal;
    }

    /**
     * Returns a schedulable, ENABLED plugin - NEVER null.
     *
     * Prefers a real enabled plugin if the admin installed one; otherwise falls
     * back to the shared MinecraftInternalPlugin so VoltPur's scheduled modules
     * (ItemLimiter, TPSMonitor, WorldStability, DynamicOptimizer, ...) run even on
     * a server with 0 plugins - which was the root cause of them staying dormant.
     */
    public static Plugin get() {
        if (cached != null && cached.isEnabled()) return cached;
        cached = null;
        try {
            Plugin[] pl = Bukkit.getPluginManager().getPlugins();
            for (Plugin p : pl) {
                if (p.isEnabled()) {
                    cached = p;
                    return cached;
                }
            }
        } catch (Throwable ignored) {}
        // No real plugin available - use the internal owner (same as Purpur core tasks).
        return internal();
    }

    /**
     * Runs `then` once a plugin is ENABLED (waits up to ~90s). `then` is executed
     * on the thread of the caller for the immediate case; for the delayed case it
     * runs on a worker thread, so `then` must only SCHEDULE Bukkit tasks (never do
     * world access directly). This is exactly how VoltPurPerformance / WorldCheck
     * use it.
     */
    public static void whenAvailable(Runnable then) {
        // Fast path: a plugin is already enabled (e.g. command executed later).
        if (get() != null) {
            try { then.run(); } catch (Throwable t) {
                Bukkit.getLogger().warning("[VoltPur] whenAvailable task failed: " + t.getMessage());
            }
            return;
        }
        new Thread(() -> {
            for (int i = 0; i < 45; i++) { // ~90s max
                if (get() != null) {
                    try {
                        then.run();
                    } catch (Throwable t) {
                        Bukkit.getLogger().warning("[VoltPur] whenAvailable task failed: " + t.getMessage());
                    }
                    return;
                }
                try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
            }
            // Log once, quietly, only if the server actually has no plugins at all.
            try {
                org.bukkit.plugin.Plugin[] all = Bukkit.getPluginManager().getPlugins();
                if (all.length == 0) {
                    Bukkit.getLogger().info("[VoltPur] No plugins loaded - auto sync tasks stay off (use /voltpur benchmark manually).");
                }
            } catch (Throwable ignored) {}
        }, "VoltPur-Plugin-Wait").start();
    }
}
