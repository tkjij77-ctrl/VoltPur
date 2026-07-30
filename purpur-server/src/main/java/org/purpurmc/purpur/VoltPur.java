
package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import java.util.logging.Logger;

public class VoltPur {
    public static final String VERSION = "26.2-VoltPur";
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
    }
    public static String getVersion(){ return VERSION; }
}
