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
     * Honest handling of the plugin-pro/ concept.
     * Paper has no runtime API to register a second plugin folder. Until a real
     * patch (Phase 2, F8) adds it as a plugin source, we do NOT claim it loads.
     * We simply ensure the folder exists and tell the admin to place jars in
     * plugins/ (the folder Paper actually loads).
     */
    public static void ensurePluginProFolder() {
        try {
            java.io.File folder = new java.io.File("plugin-pro");
            if (!folder.exists()) {
                folder.mkdirs();
                java.io.File readme = new java.io.File(folder, "README.txt");
                if (!readme.exists()) {
                    try (java.io.FileWriter fw = new java.io.FileWriter(readme)) {
                        fw.write("VoltPur plugin-pro/ - ORGANISATIONAL folder only (Phase 1).\n");
                        fw.write("Paper loads plugins ONLY from the plugins/ folder.\n");
                        fw.write("This folder is for organising/backing up performance plugin jars.\n");
                        fw.write("Place jars you want loaded into plugins/ instead.\n");
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur] Could not create plugin-pro folder: " + e.getMessage());
        }
    }

    /**
     * Backwards-compatible wrapper (called by PurpurConfig.init()).
     * In Phase 1 this only ensures the folder exists - it does NOT claim to load
     * plugins. Real plugin-pro loading is a Phase 2 patch (F8).
     */
    public static void loadPluginPro() {
        ensurePluginProFolder();
        Bukkit.getLogger().info("[VoltPur] plugin-pro/ is an organisational folder (Phase 1)."
                + " Plugins are loaded from plugins/ by Paper.");
    }
}
