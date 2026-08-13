package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * VoltPur Plugin owner helper.
 *
 * Paper's schedulers (Bukkit.getScheduler(), GlobalRegionScheduler, AsyncScheduler)
 * require a NON-NULL owning Plugin. VoltPur runs inside the server (not as a
 * plugin), and at init time (PurpurConfig.init) no plugin may be loaded yet.
 * This helper waits until a plugin becomes available, then returns it so tasks
 * can be scheduled correctly. If the server ever has zero plugins, auto tasks
 * simply stay disabled (they can still run via /voltpur benchmark on main thread).
 */
public final class VoltPurPlugin {

    private static volatile Plugin cached;

    private VoltPurPlugin() {}

    /** Returns the first loaded plugin, or null if none are loaded yet. */
    public static Plugin get() {
        if (cached != null) return cached;
        try {
            Plugin[] pl = Bukkit.getPluginManager().getPlugins();
            if (pl.length > 0) {
                cached = pl[0];
                return cached;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Runs `then` on the MAIN thread once a plugin is available (waits up to
     * ~80s). This lets us schedule repeating/sync tasks with a real plugin owner.
     * If no plugin ever appears, `then` is not run and a warning is logged.
     */
    public static void whenAvailable(Runnable then) {
        // Return early on main thread if a plugin is already present.
        Plugin p = get();
        if (p != null) {
            try { then.run(); } catch (Throwable t) {
                Bukkit.getLogger().warning("[VoltPur] whenAvailable task failed: " + t.getMessage());
            }
            return;
        }
        new Thread(() -> {
            for (int i = 0; i < 40; i++) { // ~80s max
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
            Bukkit.getLogger().warning("[VoltPur] No plugin available after retries - auto tasks disabled (use /voltpur benchmark).");
        }, "VoltPur-Plugin-Wait").start();
    }
}
