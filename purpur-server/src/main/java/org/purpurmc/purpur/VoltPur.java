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
        logger.info("  VoltCore modules: " + VoltPurModules.activeCount() + " ACTIVE  |  "
            + VoltPurModules.partialCount() + " PARTIAL  |  " + VoltPurModules.plannedCount() + " PLANNED (roadmap)");
        for (String name : VoltPurModules.all().keySet()) {
            logger.info("  [VoltPur] " + VoltPurModules.line(name));
        }
        logger.info("  [VoltPur] /voltpur hardware  -> device compatibility");
        logger.info("  [VoltPur] /voltpur benchmark -> live measured performance");
        logger.info("  [VoltPur] /vo up             -> updater");
        printInstalledStamp(logger);

        try { VoltPurConfig.init(); } catch (Exception e) { logger.warning("Config failed: " + e.getMessage()); }
        try { VoltPurPerformance.init(); } catch (Exception e) { logger.warning("Perf init failed: " + e.getMessage()); }
        try { VoltPurWorldCheck.init(); } catch (Exception e) { logger.warning("WorldCheck init failed: " + e.getMessage()); }

        // Opt-in real modules (safe, pure server-side; no-op unless enabled in voltpur.yml).
        try { VoltPurDiscord.init(); } catch (Exception e) { logger.warning("[VoltPur] Discord init failed: " + e.getMessage()); }
        try { VoltPurBackup.init(); } catch (Exception e) { logger.warning("[VoltPur] Backup init failed: " + e.getMessage()); }
        try { VoltPurResourcePack.init(); } catch (Exception e) { logger.warning("[VoltPur] ResourcePack init failed: " + e.getMessage()); }

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

        // Auto-tune applied after startup (opt-in). Use the SAME safe pattern as
        // the other modules: wait until a plugin is ENABLED, then schedule on it.
        // Never pass a null (or a not-yet-enabled) plugin to the scheduler, as that
        // throws "Plugin may not be null" / "attempted to register task while disabled".
        if (VoltPurConfig.hardwareAutoTune) {
            try {
                VoltPurPlugin.whenAvailable(() -> {
                    org.bukkit.plugin.Plugin p = VoltPurPlugin.get();
                    if (p == null) {
                        logger.info("[VoltPur] Auto-tune skipped - no enabled plugin to own the task.");
                        return;
                    }
                    Bukkit.getScheduler().runTaskLater(p, VoltPurTuning::onServerStart, 800L);
                });
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
        Logger logger = Bukkit.getLogger();
        // Honest description: plugin-pro/ is a PRIORITY plugin folder. The paperweight
        // patch registers it as a plugin source that is scanned BEFORE plugins/. Jars here
        // are standard Paper/Bukkit plugins (NOT Fabric-style pre-game mods). A verifiable
        // load log is printed so the operator can confirm what was picked up.
        logger.info("[VoltPur] plugin-pro/ active - priority plugin folder (scanned before plugins/ via paperweight patch).");
        try {
            java.io.File folder = new java.io.File("plugin-pro");
            java.io.File[] jars = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
            int count = jars == null ? 0 : jars.length;
            if (count == 0) {
                logger.info("[VoltPur] plugin-pro/: no .jar plugins present (drop performance plugins here to load them first).");
            } else {
                logger.info("[VoltPur] plugin-pro/: found " + count + " plugin jar(s) to load first:");
                for (java.io.File j : jars) logger.info("[VoltPur]   - " + j.getName());
            }
        } catch (Exception e) {
            logger.warning("[VoltPur] plugin-pro/ scan failed: " + e.getMessage());
        }
    }

    /**
     * After a /vo up update, the updater writes voltpur-installed.txt with the build
     * identity. On the next boot we print it so the operator can CONFIRM the exact build
     * that is now running (answers "which version did I actually move to?").
     */
    private static void printInstalledStamp(Logger logger) {
        try {
            java.io.File f = new java.io.File("voltpur-installed.txt");
            if (!f.exists()) return;
            java.util.Properties p = new java.util.Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { p.load(in); }
            String commit = p.getProperty("commit", "");
            String shortSha = commit.length() >= 7 ? commit.substring(0, 7) : commit;
            logger.info("  [VoltPur] 📌 Installed via /vo up -> build #" + p.getProperty("build", "?")
                + " | run " + p.getProperty("run", "?")
                + " | commit " + (shortSha.isEmpty() ? "?" : shortSha)
                + (p.getProperty("exact", "true").equals("false") ? " (latest fallback)" : "")
                + " | at " + p.getProperty("installed-at", "?"));
            logger.info("  [VoltPur] ✅ Cross-check: the 'This server is running Purpur ...@<commit>' line above should show " + (shortSha.isEmpty() ? "the same commit" : shortSha) + ".");
        } catch (Exception ignored) {}
    }
}
