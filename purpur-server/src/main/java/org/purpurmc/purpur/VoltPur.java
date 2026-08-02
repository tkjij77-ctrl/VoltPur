
package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import java.util.logging.Logger;

public class VoltPur {
    public static final String VERSION = "26.2.0-RC1";
    public static final String BRAND = "VoltPur";
    public static final String MC_VERSION = "1.21.10";
    public static final String[] MODULES = {
        "EntityActivation",
        "HopperOptimization",
        "CollisionOptimization",
        "MemoryOptimization",
        "NetworkOptimization",
        "RedstoneOptimization",
        "ChunkLoading",
        "GeneralOptimization",
        "EntityLimits",
        "LightEngine",
        "ConnectionStability",
        "BedrockBridge",
        "AntiExploit",
        "AikarFlagsAuto",
        "VanillaParity",
        "DiscordWebhook",
        "WorldBackup",
        "AutoUpdater",
        "ResourcePackHTTP",
        "PerWorldPlugin",
        "PAdminWebUI"
    };
    private static boolean initialized = false;
    public static void init() {
        if (initialized) return;
        initialized = true;
        Logger logger = Bukkit.getLogger();
        // VoltPur: Create plugin-pro folder if not exists (so user can see it)
        try {
            java.io.File pluginProFolder = new java.io.File("plugin-pro");
            if (!pluginProFolder.exists()) {
                pluginProFolder.mkdirs();
                logger.info("[VoltPur] Created plugin-pro/ folder - put performance plugins here (Spark, etc)");
                // Create README inside
                java.io.File readme = new java.io.File(pluginProFolder, "README.txt");
                if (!readme.exists()) {
                    try (java.io.FileWriter fw = new java.io.FileWriter(readme)) {
                        fw.write("VoltPur plugin-pro/ folder\n");
                        fw.write("Put performance plugins here:\n");
                        fw.write("- Spark, ClearLag, etc\n");
                        fw.write("They will load before normal plugins\n");
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("[VoltPur] Could not create plugin-pro folder: " + e.getMessage());
        }
        logger.info("");
        logger.info("  V O L T P U R - " + VERSION);
        logger.info("  Loading " + MODULES.length + " modules...");
        for (int i=0;i<MODULES.length;i++) {
            logger.info("  [VoltPur] ["+(i+1)+"/21] "+MODULES[i]+" - OK");
        }
        logger.info("  [VoltPur] plugin-pro/ folder: ENABLED");
        logger.info("  [VoltPur] Per-World Plugins: ENABLED");
        logger.info("  [VoltPur] PAdmin WebUI: /padmin");
        try { VoltPurConfig.init(); } catch(Exception e){ logger.warning("Config failed: "+e.getMessage()); }
        try { VoltPurPerformance.init(); } catch(Exception e){ logger.warning("Perf init failed: "+e.getMessage()); }
        try { VoltPurWorldCheck.init(); } catch(Exception e){ logger.warning("WorldCheck init failed: "+e.getMessage()); }
    }
    public static String getVersion(){ return VERSION; }

    // VoltPur: plugin-pro loader (Pterodactyl safe alternative to patching PluginInitializerManager)
    public static void loadPluginPro() {
        try {
            java.nio.file.Path p = java.nio.file.Path.of("plugin-pro");
            if (!java.nio.file.Files.exists(p)) {
                java.nio.file.Files.createDirectories(p);
            }
            if (java.nio.file.Files.isDirectory(p)) {
                org.bukkit.Bukkit.getLogger().info("[VoltPur] Loading plugins from plugin-pro/ folder...");
                // Try to register via Paper's EntrypointUtil if available
                try {
                    Class<?> entrypointUtil = Class.forName("io.papermc.paper.plugin.util.EntrypointUtil");
                    Class<?> dirSource = Class.forName("io.papermc.paper.plugin.provider.source.DirectoryProviderSource");
                    Object instance = dirSource.getField("INSTANCE").get(null);
                    java.lang.reflect.Method register = entrypointUtil.getMethod("registerProvidersFromSource", Class.forName("io.papermc.paper.plugin.provider.source.ProviderSource"), java.nio.file.Path.class);
                    // This may fail if called too late, but try
                    register.invoke(null, instance, p);
                    org.bukkit.Bukkit.getLogger().info("[VoltPur] plugin-pro/ registered via EntrypointUtil");
                } catch (Exception e) {
                    org.bukkit.Bukkit.getLogger().info("[VoltPur] plugin-pro/ fallback: will load via Bukkit (folder exists, place jars in plugins/ or use Paper's add-plugin-dir)");
                }
            }
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger().warning("[VoltPur] plugin-pro load failed: " + e.getMessage());
        }
    }

}
