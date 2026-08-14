package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import java.util.logging.Logger;

public class VoltPur {
    public static final String VERSION = "26.2.0-RC1";
    public static final String BRAND = "VoltPur";
    public static final String MC_VERSION = "1.21.10";
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        Logger logger = Bukkit.getLogger();
        logger.info("");
        logger.info("  V O L T P U R - " + VERSION);
        logger.info("  VoltCore real modules: " + VoltPurModules.activeCount() + "/" + VoltPurModules.totalCount() + " ACTIVE");
        for (String name : VoltPurModules.all().keySet()) {
            logger.info("  [VoltPur] " + VoltPurModules.line(name));
        }
        logger.info("  [VoltPur] /voltpur hardware  -> device compatibility");
        logger.info("  [VoltPur] /voltpur benchmark -> live measured performance");
        logger.info("  [VoltPur] /vo up             -> updater");

        try { VoltPurConfig.init(); } catch (Exception e) { logger.warning("Config failed: " + e.getMessage()); }
        try { VoltPurPerformance.init(); } catch (Exception e) { logger.warning("Perf init failed: " + e.getMessage()); }
        try { VoltPurWorldCheck.init(); } catch (Exception e) { logger.warning("WorldCheck init failed: " + e.getMessage()); }

        // Hardware detection + optional report (safe, read-only).
        try {
            VoltPurHardware.detect();
            if (VoltPurConfig.hardwareReport) VoltPurHardware.reportToLogAndFile(VoltPurConfig.hardwareWarn);
        } catch (Exception e) {
            logger.warning("[VoltPur] Hardware detection failed: " + e.getMessage());
        }

        // Dynamic Optimizer - VoltCore signature feature. Runs after a plugin is
        // enabled and worlds are loaded, then applies adaptive spigot.yml tuning.
        try {
            VoltPurPlugin.whenAvailable(() -> {
                org.bukkit.plugin.Plugin p = VoltPurPlugin.get();
                if (p == null) return;
                Bukkit.getScheduler().runTaskLater(p, VoltPurOptimizer::apply, 400L);
            });
        } catch (Exception e) {
            logger.warning("[VoltPur] Optimizer schedule failed: " + e.getMessage());
        }

        // Auto-tune applied after startup (opt-in).
        if (VoltPurConfig.hardwareAutoTune) {
            try {
                Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugins().length > 0 ? Bukkit.getPluginManager().getPlugins()[0] : null,
                    VoltPurTuning::onServerStart,
                    800L
                );
            } catch (Exception e) {
                logger.warning("[VoltPur] Auto-tune scheduling failed: " + e.getMessage());
            }
        }
    }
    public static String getVersion() { return VERSION; }

    /**
     * plugin-pro/ is registered as a real plugin source by the paperweight patch
     * (paper-patches/files/.../PluginInitializerManager.java.patch), which loads it
     * BEFORE plugins/. Here we only ensure the folder exists so it is ready.
     */
    public static void ensurePluginProFolder() {
        try {
            java.io.File folder = new java.io.File("plugin-pro");
            if (!folder.exists()) {
                folder.mkdirs();
                java.io.File readme = new java.io.File(folder, "README.txt");
                if (!readme.exists()) {
                    try (java.io.FileWriter fw = new java.io.FileWriter(readme)) {
                        fw.write("VoltPur plugin-pro/ - loads BEFORE plugins/ (via paperweight patch).\n");
                        fw.write("Put performance plugins (Spark, ClearLag) here to load first.\n");
                        fw.write("Regular gameplay plugins still go in plugins/.\n");
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur] Could not create plugin-pro folder: " + e.getMessage());
        }
    }

    /**
     * Backwards-compatible wrapper (called by PurpurConfig.init()).
     * plugin-pro/ is loaded by the paperweight patch during PluginInitializerManager.load(),
     * before plugins/. This method only ensures the folder exists (no duplicate loading).
     */
    public static void loadPluginPro() {
        ensurePluginProFolder();
        Bukkit.getLogger().info("[VoltPur] plugin-pro/ registered as a plugin source (loads before plugins/) via paperweight patch.");
    }
}
