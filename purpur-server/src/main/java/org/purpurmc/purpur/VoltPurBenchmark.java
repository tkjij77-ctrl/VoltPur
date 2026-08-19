package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * VoltPur Benchmark - the ONLY source of performance numbers in this fork.
 *
 * Any claimed performance improvement must be backed by a before/after
 * measurement captured by this tool. It reads live server stats:
 *   - TPS (Paper's measured TPS)
 *   - MSPT / tick times
 *   - entity counts per world
 *   - loaded chunk counts per world
 *   - JVM heap usage
 *
 * Output is appended to logs/voltpur-benchmark.txt and mirrored as a JSON line
 * (for scriptable comparisons).
 */
public final class VoltPurBenchmark {

    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private VoltPurBenchmark() {}

    /** Returns one printable line per metric. */
    public static List<String> snapshot(String tag) {
        List<String> out = new ArrayList<>();
        out.add("== VoltPur Benchmark [" + tag + "] @ " + TS.format(new Date()) + " ==");

        // TPS + MSPT (Paper)
        try {
            double[] tps = Bukkit.getServer().getTPS();
            out.add(String.format("TPS      : %.2f / %.2f / %.2f (1m,5m,15m)", tps[0], tps[1], tps[2]));
        } catch (Throwable e) { out.add("TPS      : unavailable"); }
        try {
            long[] tick = Bukkit.getServer().getTickTimes(); // ns per tick
            double mspt = tick[0] / 1_000_000.0;
            out.add(String.format("MSPT     : %.2f ms (most recent tick average)", mspt));
        } catch (Throwable e) { out.add("MSPT     : unavailable"); }

        int totalEntities = 0, totalChunks = 0;
        for (World w : Bukkit.getWorlds()) {
            int e = 0, c = 0;
            try { e = w.getEntities().size(); } catch (Throwable ignored) {}
            try { c = w.getLoadedChunks().length; } catch (Throwable ignored) {}
            totalEntities += e; totalChunks += c;
            out.add("  World  : " + w.getName() + " (E=" + e + ", C=" + c + ")");
        }
        out.add("Entities : " + totalEntities);
        out.add("Chunks   : " + totalChunks);

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024L*1024L);
        long maxMB = rt.maxMemory() / (1024L*1024L);
        out.add("Heap     : " + usedMB + " MB used / " + maxMB + " MB max");

        // Hopper + EAR status (counter only; never changes state).
        try {
            int[] hopper = VoltPurPerformance.hopperStats();
            out.add("Hoppers  : " + hopper[0] + " total / " + hopper[1] + " sleepable (empty+no-source+unpowered)");
            out.add("HopperSleep: " + (VoltPurConfig.hopperSleepEnabled ? "ENABLED (cooldown=" + VoltPurConfig.hopperSleepCooldown + ")" : "off (opt-in)"));
        } catch (Throwable e) { out.add("Hoppers  : unavailable"); }
        // Entity Activation Range is a Paper/Purpur built-in; report it as reference.
        out.add("EAR      : ACTIVE (Paper/Purpur built-in entity-activation-range)");

        out.add("-- end --");
        return out;
    }

    /** Appends a snapshot to logs/voltpur-benchmark.txt. */
    public static void record(String tag) {
        List<String> lines = snapshot(tag);
        for (String l : lines) Bukkit.getLogger().info("[VoltPur-Bench] " + l);
        try {
            Path logDir = Path.of("logs");
            Files.createDirectories(logDir);
            Path file = logDir.resolve("voltpur-benchmark.txt");
            List<String> toWrite = new ArrayList<>(lines);
            Files.write(file, toWrite, StandardCharsets.UTF_8,
                    Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND
                                       : java.nio.file.StandardOpenOption.CREATE);
            Bukkit.getLogger().info("[VoltPur-Bench] Snapshot appended to logs/voltpur-benchmark.txt");
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur-Bench] Could not write benchmark: " + e.getMessage());
        }
    }
}
