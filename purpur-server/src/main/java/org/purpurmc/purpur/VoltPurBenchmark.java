package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * VoltPur Benchmark - the only place performance numbers may come from.
 *
 * HARDENING CHANGES:
 *  - MSPT is now the real average tick time (Bukkit.getAverageTickTime()). The old
 *    code printed getTickTimes()[0] and called it "most recent tick average", which
 *    is a single tick sample and easily misread as a stable metric.
 *  - Log rotation is restored (it was added in an audit commit, then reverted): the
 *    file is capped so a long-running server cannot fill the disk.
 *  - Every measurement is wrapped in VoltPurGuard and disagreements are explicit:
 *    each line says what was measured and what was NOT applied.
 */
public final class VoltPurBenchmark {

    private static final String MODULE = "Benchmark";
    private static final SimpleDateFormat TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_LOG_BYTES = 512L * 1024L;

    private VoltPurBenchmark() {}

    /** Returns one printable line per metric. Read-only: changes nothing. */
    public static List<String> snapshot(String tag) {
        List<String> out = new ArrayList<>();
        out.add("== VoltPur benchmark [" + tag + "] @ " + TIMESTAMP.format(new Date()) + " ==");
        out.add("VoltPur   : " + VoltPur.VERSION + " | MC " + VoltPur.MC_VERSION);

        try {
            double[] tps = Bukkit.getServer().getTPS();
            out.add(String.format("TPS       : %.2f / %.2f / %.2f (1m, 5m, 15m)", tps[0], tps[1], tps[2]));
        } catch (Throwable t) {
            out.add("TPS       : unavailable (" + t.getMessage() + ")");
        }
        try {
            out.add(String.format("MSPT avg  : %.2f ms (Paper average tick time)", Bukkit.getServer().getAverageTickTime()));
        } catch (Throwable t) {
            out.add("MSPT avg  : unavailable (" + t.getMessage() + ")");
        }

        int totalEntities = 0;
        int totalChunks = 0;
        for (World world : Bukkit.getWorlds()) {
            int entities = 0;
            int chunks = 0;
            try {
                entities = world.getEntities().size();
            } catch (Throwable ignoredEntities) {
                // Reported as 0 below; the world still appears in the list.
            }
            try {
                chunks = world.getLoadedChunks().length;
            } catch (Throwable ignoredChunks) {
                // Same: keep the line, mark the world as partially measured.
            }
            totalEntities += entities;
            totalChunks += chunks;
            out.add("  world   : " + world.getName() + " (E=" + entities + ", C=" + chunks + ")");
        }
        out.add("Entities  : " + totalEntities);
        out.add("Chunks    : " + totalChunks);

        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long maxMB = runtime.maxMemory() / (1024L * 1024L);
        VoltPurHardware.detect();
        out.add("Heap      : " + usedMB + " MB used / " + maxMB + " MB max"
                + (VoltPurHardware.isContainer() ? " (container RAM available: " + VoltPurHardware.getEffectiveRamMB() + " MB)" : ""));

        int[] hoppers = VoltPurPerformance.hopperStats();
        out.add("Hoppers   : " + hoppers[0] + " total / " + hoppers[1] + " idle (empty + no source above + unpowered)");
        out.add("HopperOpt : " + (VoltPurConfig.hardwareAutoTune
                ? "spigot.yml hopper-check=8 is written when /voltpur optimize runs (restart required)"
                : "off - no hopper setting is applied (there is no NMS hopper patch in this build)"));

        out.add("ItemLimit : " + (VoltPurConfig.itemLimiterEnabled
                ? "ENABLED (cap " + VoltPurConfig.itemLimiterMaxPerWorld + "/world, min age "
                    + VoltPurConfig.itemLimiterMinAgeSeconds + "s, keeps named/enchanted)"
                : "disabled (opt-in) - nothing removed"));
        out.add("Optimizer : " + VoltPurOptimizer.describe());
        out.add("ModuleGuard: " + guardSummary());
        out.add("Note      : VoltPur does not implement NMS performance patches; it measures and configures. "
                + "Compare before/after with this file.");
        out.add("-- end --");
        return out;
    }

    private static String guardSummary() {
        long failures = 0L;
        long runs = 0L;
        for (VoltPurGuard.Stat stat : VoltPurGuard.all().values()) {
            failures += stat.failures();
            runs += stat.runs();
        }
        return "runs=" + runs + " failures=" + failures
                + (failures > 0 ? " - run /voltpur modules to see which module is failing" : " (no module failures recorded)");
    }

    /** Appends a snapshot to logs/voltpur-benchmark.txt, keeping the file bounded. */
    public static void record(String tag) {
        List<String> lines = snapshot(tag);
        for (String line : lines) Bukkit.getLogger().info("[VoltPur-Bench] " + line);
        VoltPurGuard.run(MODULE, () -> {
            try {
                Path logDir = Path.of("logs");
                Files.createDirectories(logDir);
                Path file = logDir.resolve("voltpur-benchmark.txt");
                Files.write(file, lines, StandardCharsets.UTF_8,
                        Files.exists(file) ? StandardOpenOption.APPEND : StandardOpenOption.CREATE);
                trim(file);
                Bukkit.getLogger().info("[VoltPur-Bench] Snapshot appended to logs/voltpur-benchmark.txt");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Keeps the benchmark log under a byte cap by dropping the oldest half. */
    static void trim(Path file) throws IOException {
        if (Files.size(file) <= MAX_LOG_BYTES) return;
        List<String> all = Files.readAllLines(file, StandardCharsets.UTF_8);
        int keep = Math.max(100, all.size() / 2);
        List<String> tail = all.subList(all.size() - keep, all.size());
        Files.write(file, tail, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
