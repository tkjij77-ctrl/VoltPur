package org.purpurmc.purpur.plus;

/**
 * VoltPur Plus Module Interface (§6.3, §6.5).
 *
 * Modules residing in plugin-plus/ implement this interface to perform
 * low-level pre-launch transformations and post-boot integrations.
 */
public interface VoltPurPlusModule {

    /**
     * Called early during bootstrap, before the Minecraft server engine loads.
     * Use this phase to register bytecode transformers, verify environments, or configure low-level hooks.
     *
     * @param context the module execution context
     */
    void onPreLaunch(PlusContext context);

    /**
     * Called once the Minecraft server and Bukkit API are fully initialized and ready.
     * Safe to access Bukkit worlds, plugins, and services.
     */
    void onServerReady();
}
