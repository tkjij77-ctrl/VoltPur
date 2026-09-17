package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.World.Environment;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.logging.Logger;

/**
 * VoltPur WorldCheck - read-only reporting of world/storage state.
 *
 * REMOVED IN THE HARDENING PASS: {@code ensureServerProperties()}.
 * It rewrote server.properties at runtime and, when the file was missing, wrote a
 * brand new one containing "online-mode=false" (an open cracked server) and
 * "allow-flight=true", while also stripping every comment from an existing file
 * (Properties.store rewrites the whole file) and forcing allow-nether=true over
 * the operator's choice. Minecraft/Paper generate server.properties correctly on
 * first start, so VoltPur has no business writing it at all.
 *
 * What remains is diagnostic only: it reports loaded worlds, real region-file
 * counts on disk, and appends a stability line to logs/voltpur-stability.log.
 */
public class VoltPurWorldCheck {

    private static final String MODULE = "WorldStability";
    private static boolean scheduled = false;

    private VoltPurWorldCheck() {}

    public static void init() {
        if (scheduled) return;
        scheduled = true;
        Logger logger = Bukkit.getLogger();
        logger.info("[VoltPur-World] Stability check scheduled (read-only).");

        // Runs on the main thread via the internal plugin owner (see VoltPurPlugin).
        try {
            Bukkit.getScheduler().runTaskLater(VoltPurPlugin.get(), () -> VoltPurGuard.run(MODULE, () -> {
                reportWorlds();
                reportWorldFolders();
                writeStabilityReport();
            }), 200L); // ~10s after startup, when worlds are loaded
        } catch (Throwable t) {
            VoltPurGuard.failure(MODULE, t);
        }
    }

    private static void reportWorlds() {
        Logger logger = Bukkit.getLogger();
        boolean hasNether = false;
        boolean hasEnd = false;
        for (World world : Bukkit.getWorlds()) {
            int entities = 0;
            int chunks = 0;
            try {
                entities = world.getEntities().size();
            } catch (Throwable t) {
                logger.fine("[VoltPur-World] entity count unavailable for " + world.getName() + ": " + t.getMessage());
            }
            try {
                chunks = world.getLoadedChunks().length;
            } catch (Throwable t) {
                logger.fine("[VoltPur-World] chunk count unavailable for " + world.getName() + ": " + t.getMessage());
            }
            if (world.getEnvironment() == Environment.NETHER) hasNether = true;
            if (world.getEnvironment() == Environment.THE_END) hasEnd = true;
            logger.info("[VoltPur-World] - " + world.getName() + " (" + world.getEnvironment()
                    + ") E:" + entities + " C:" + chunks);
        }
        int count = Bukkit.getWorlds().size();
        logger.info("[VoltPur-World] Loaded worlds: " + count
                + " (overworld=" + (Bukkit.getWorld("world") != null ? "OK" : "not named 'world'")
                + ", nether=" + (hasNether ? "OK" : "not loaded")
                + ", end=" + (hasEnd ? "OK" : "not loaded") + ")");
        logger.info("[VoltPur-World] Note: Paper's WorldFolderMigration messages at startup are normal, not errors.");
    }

    /** Reports the folders that exist on disk, with real saved region counts (proof, not claims). */
    private static void reportWorldFolders() {
        Logger logger = Bukkit.getLogger();
        File pluginPro = new File("plugin-pro");
        if (!pluginPro.isDirectory() && pluginPro.mkdirs()) {
            logger.info("[VoltPur-World] Created plugin-pro/ (priority plugin folder).");
        }
        for (World world : Bukkit.getWorlds()) {
            File folder = world.getWorldFolder();
            if (folder == null || !folder.isDirectory()) continue;
            logger.info("[VoltPur-World] On disk: " + folder.getName() + "/ - " + countRegionFiles(folder) + " region file(s)");
        }
    }

    private static int countRegionFiles(File worldDir) {
        int[] counter = {0};
        countRecursive(worldDir, counter, 0);
        return counter[0];
    }

    private static void countRecursive(File dir, int[] counter, int depth) {
        if (dir == null || depth > 4) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) countRecursive(child, counter, depth + 1);
            else if (child.getName().endsWith(".mca")) counter[0]++;
        }
    }

    private static void writeStabilityReport() {
        try {
            Path logDir = Path.of("logs");
            Files.createDirectories(logDir);
            Path report = logDir.resolve("voltpur-stability.log");
            StringBuilder sb = new StringBuilder();
            sb.append("=== VoltPur stability report - ").append(new Date()).append(" ===\n");
            sb.append("version: ").append(VoltPur.VERSION).append('\n');
            sb.append("worlds: ").append(Bukkit.getWorlds().size()).append('\n');
            for (World world : Bukkit.getWorlds()) {
                File folder = world.getWorldFolder();
                int regions = (folder != null && folder.isDirectory()) ? countRegionFiles(folder) : 0;
                sb.append("  - ").append(world.getName()).append(" (").append(world.getEnvironment())
                        .append(") region files: ").append(regions).append('\n');
            }
            sb.append("modules: ").append(VoltPurModules.activeCount()).append(" active / ")
                    .append(VoltPurModules.totalCount()).append(" tracked\n");
            sb.append('\n');
            Files.writeString(report, sb.toString(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            VoltPurGuard.failure(MODULE, e);
        }
    }
}
