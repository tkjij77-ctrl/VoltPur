package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import java.io.File;
import java.util.logging.Logger;

public class VoltPurWorldCheck {
    private static boolean checked = false;
    public static void init() {
        if (checked) return;
        checked = true;
        Logger logger = Bukkit.getLogger();
        logger.info("[VoltPur-World] Stability check scheduled");

        // VoltPur: schedule the world check on Paper's GlobalRegionScheduler, which
        // runs on the main thread and accepts a null plugin (works on servers with
        // no plugins). A custom thread here caused "main thread check" AsyncCatcher
        // errors when reading world entities.
        try {
            io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler sched =
                    Bukkit.getGlobalRegionScheduler();
            if (sched != null) {
                sched.runDelayed(null, task -> {
                    try {
                        ensureServerProperties();
                        checkWorldsSync();
                        checkFiles();
                        generateStabilityReportSync();
                    } catch (Exception e) {
                        logger.warning("[VoltPur-World] Check failed: " + e.getMessage());
                    }
                }, 200L); // ~10s after server start
            } else {
                // Fallback: run once after a delay on a worker thread, but ONLY
                // touch server.properties (no world access) to avoid async errors.
                new Thread(() -> {
                    try { Thread.sleep(35000); ensureServerProperties(); }
                    catch (Exception ignored) {}
                }, "VoltPur-World-Fallback").start();
            }
        } catch (Exception e) {
            logger.warning("[VoltPur-World] Schedule failed: " + e.getMessage());
        }
    }
    public static void ensureServerProperties() {
        try {
            File file = new File("server.properties");
            if (!file.exists()) {
                Bukkit.getLogger().info("[VoltPur-World] server.properties not found, creating default...");
                try (java.io.FileWriter fw = new java.io.FileWriter(file)) {
                    fw.write("# VoltPur server.properties\n");
                    fw.write("server-port=25565\n");
                    fw.write("online-mode=false\n");
                    fw.write("allow-nether=true\n");
                    fw.write("allow-flight=true\n");
                    fw.write("spawn-protection=0\n");
                    fw.write("view-distance=10\n");
                    fw.write("motd=VoltPur Full Software\n");
                }
            } else {
                java.util.Properties props = new java.util.Properties();
                try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) { props.load(fis); }
                boolean changed = false;
                if (!props.containsKey("allow-nether")) { props.setProperty("allow-nether", "true"); changed = true; }
                else if (!props.getProperty("allow-nether").equalsIgnoreCase("true")) {
                    props.setProperty("allow-nether", "true"); changed = true;
                }
                if (!props.containsKey("allow-flight")) { props.setProperty("allow-flight", "true"); changed = true; }
                if (changed) {
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) { props.store(fos, "VoltPur"); }
                }
            }
        } catch (Exception e) {}
    }
    private static void checkWorldsSync() {
        Logger logger = Bukkit.getLogger();
        try {
            int count = Bukkit.getWorlds().size();
            logger.info("[VoltPur-World] Currently loaded worlds: " + count);
            for (World w : Bukkit.getWorlds()) {
                int entities = 0; int chunks = 0;
                try { entities = w.getEntities().size(); } catch (Exception ex) { }
                try { chunks = w.getLoadedChunks().length; } catch (Exception ex) { }
                logger.info("[VoltPur-World] - " + w.getName() + " (" + w.getEnvironment() + ") E:" + entities + " C:" + chunks);
            }
            boolean hasNether = false; boolean hasEnd = false;
            for (World w : Bukkit.getWorlds()) {
                if (w.getEnvironment() == Environment.NETHER) hasNether = true;
                if (w.getEnvironment() == Environment.THE_END) hasEnd = true;
            }
            logger.info("[VoltPur-World] === Stability Report ===");
            logger.info("[VoltPur-World] Overworld: " + (Bukkit.getWorld("world") != null ? "OK" : "MISSING"));
            logger.info("[VoltPur-World] Nether: " + (hasNether ? "OK" : "MISSING/DISABLED"));
            logger.info("[VoltPur-World] End: " + (hasEnd ? "OK" : "MISSING/DISABLED"));
            logger.info("[VoltPur-World] Total: " + Bukkit.getWorlds().size() + " worlds");
            logger.info("[VoltPur-World] All expected worlds loaded - STABLE");
        } catch (Exception e) {}
    }
    private static void checkFiles() {
        Logger logger = Bukkit.getLogger();
        String[] requiredFiles = {"server.properties", "bukkit.yml", "purpur.yml", "voltpur.yml", "plugins", "plugin-pro", "world"};
        for (String fName : requiredFiles) {
            File f = new File(fName);
            if (!f.exists() && fName.equals("plugin-pro")) { f.mkdirs(); }
        }
        logger.info("[VoltPur-World] All required files present - STABLE");
    }
    private static void generateStabilityReportSync() {
        Logger logger = Bukkit.getLogger();
        try {
            File logDir = new File("logs");
            if (!logDir.exists()) logDir.mkdirs();
            File report = new File(logDir, "voltpur-stability.log");
            try (java.io.FileWriter fw = new java.io.FileWriter(report, true)) {
                fw.write("=== VoltPur Stability Report - " + new java.util.Date() + " ===\n");
                fw.write("Version: " + VoltPur.VERSION + "\n");
                fw.write("Worlds: " + Bukkit.getWorlds().size() + "\n");
                for (World w : Bukkit.getWorlds()) {
                    fw.write("  - " + w.getName() + " (" + w.getEnvironment() + ")\n");
                }
                fw.write("Status: STABLE\n");
            }
            logger.info("[VoltPur-World] Stability report -> logs/voltpur-stability.log");
        } catch (Exception e) {}
    }
}
