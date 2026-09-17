package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * VoltPur Plugin owner helper.
 *
 * Paper's schedulers require a NON-NULL plugin owner, and scheduling on a plugin
 * that is not yet enabled throws "Plugin attempted to register task while
 * disabled". VoltPur runs INSIDE the server (it is not a plugin), so it owns its
 * tasks exactly the way Purpur owns its own (see BossBarTask / BeehiveTask):
 * with the shared {@link org.purpurmc.purpur.util.MinecraftInternalPlugin}.
 *
 * IMPORTANT (fixed in the hardening pass): the owner is now ALWAYS the internal
 * plugin. Previously it borrowed the first enabled third-party plugin, which
 * meant VoltPur's scheduled modules (item limiter, TPS monitor, world check,
 * optimizer, PAdmin snapshot) silently died whenever that plugin was disabled or
 * reloaded - and their failures were swallowed by empty catch blocks.
 *
 * Borrowing a third-party plugin was never necessary: the internal owner is a
 * valid, always-enabled Plugin (isEnabled() == true) which is all the scheduler
 * checks.
 */
public final class VoltPurPlugin {

    private static volatile Plugin internal;

    private VoltPurPlugin() {}

    /**
     * Returns the schedulable, always-ENABLED owner. Never null, never borrowed
     * from third-party plugins.
     */
    public static Plugin get() {
        Plugin cached = internal;
        if (cached == null) {
            synchronized (VoltPurPlugin.class) {
                if (internal == null) {
                    internal = new org.purpurmc.purpur.util.MinecraftInternalPlugin();
                }
                cached = internal;
            }
        }
        return cached;
    }
}
