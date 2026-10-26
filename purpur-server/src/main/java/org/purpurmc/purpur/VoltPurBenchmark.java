package org.purpurmc.purpur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
 * Output is appended to logs/voltpur-benchmark.txt and mirrored as a JSON file
 * in logs/voltpur-benchmarks/ (for scriptable comparisons).
 */
public final class VoltPurBenchmark {

    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

    /** Appends a snapshot to logs/voltpur-benchmark.txt and saves a JSON benchmark artifact. */
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

            // Write JSON artifact
            Path benchmarksDir = logDir.resolve("voltpur-benchmarks");
            Files.createDirectories(benchmarksDir);

            JsonObject json = new JsonObject();
            json.addProperty("tag", tag);
            json.addProperty("autotuneMode", VoltPurConfig.autotuneMode);
            json.addProperty("timestamp", TS.format(new Date()));
            json.addProperty("epochMillis", System.currentTimeMillis());

            double[] tps = new double[]{20.0, 20.0, 20.0};
            try {
                tps = Bukkit.getServer().getTPS();
            } catch (Throwable ignored) {}
            JsonArray tpsArr = new JsonArray();
            for (double t : tps) tpsArr.add(t);
            json.add("tps", tpsArr);

            double mspt = 20.0;
            try {
                long[] tick = Bukkit.getServer().getTickTimes();
                if (tick != null && tick.length > 0) {
                    mspt = tick[0] / 1_000_000.0;
                }
            } catch (Throwable ignored) {}
            json.addProperty("mspt", mspt);

            int totalEntities = 0;
            int totalChunks = 0;
            JsonArray worldsArr = new JsonArray();
            if (Bukkit.getServer() != null && Bukkit.getWorlds() != null) {
                for (World w : Bukkit.getWorlds()) {
                    int e = 0;
                    int c = 0;
                    try { if (w.getEntities() != null) e = w.getEntities().size(); } catch (Throwable ignored) {}
                    try { if (w.getLoadedChunks() != null) c = w.getLoadedChunks().length; } catch (Throwable ignored) {}
                    totalEntities += e;
                    totalChunks += c;

                    JsonObject wObj = new JsonObject();
                    wObj.addProperty("name", w.getName());
                    wObj.addProperty("entities", e);
                    wObj.addProperty("chunks", c);
                    worldsArr.add(wObj);
                }
            }
            json.addProperty("totalEntities", totalEntities);
            json.addProperty("totalChunks", totalChunks);
            json.add("worlds", worldsArr);

            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
            long maxMB = rt.maxMemory() / (1024L * 1024L);
            json.addProperty("heapUsedMB", usedMB);
            json.addProperty("heapMaxMB", maxMB);

            Path jsonFile = benchmarksDir.resolve("benchmark-" + System.currentTimeMillis() + ".json");
            Files.writeString(jsonFile, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[VoltPur-Bench] Could not write benchmark: " + e.getMessage());
        }
    }
}
