package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * VoltPur Hardware - detects the host machine's components and produces
 * compatibility checks + optimal JVM/server tuning for that specific hardware.
 *
 * Idea: "تحسين التوافق بين قطع الجهاز وزيادة الأداء"
 *   - Detect CPU cores, physical RAM, heap, OS, architecture, Java version.
 *   - Flag component incompatibilities (e.g. ARM without vector incubator,
 *     Java too old, too little RAM, low core count).
 *   - Generate optimal Aikar-style JVM flags + GC tuning sized to the machine.
 *   - Recommend server settings (view-distance, threads) scaled to hardware.
 *
 * All detection is read-only and side-effect free (safe by default).
 */
public final class VoltPurHardware {

    private static boolean detected = false;

    // ---- Cached hardware facts -------------------------------------------------
    private static String osName;
    private static String osArch;
    private static String osVersion;
    private static String javaVersion;
    private static int    availableCores;
    private static long   physicalRamBytes = -1;
    private static long   maxHeapBytes;
    private static String cpuModel;
    private static boolean isArm;
    private static boolean vectorAvailable;

    private VoltPurHardware() {}

    public static synchronized void detect() {
        if (detected) return;
        detected = true;

        osName      = System.getProperty("os.name", "unknown");
        osArch      = System.getProperty("os.arch", "unknown");
        osVersion   = System.getProperty("os.version", "");
        javaVersion = Runtime.version().toString();
        availableCores = Runtime.getRuntime().availableProcessors();
        maxHeapBytes   = Runtime.getRuntime().maxMemory();

        // Physical RAM via com.sun.management.OperatingSystemMXBean when available
        try {
            java.lang.management.OperatingSystemMXBean base =
                java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (base instanceof com.sun.management.OperatingSystemMXBean) {
                physicalRamBytes =
                    ((com.sun.management.OperatingSystemMXBean) base).getTotalPhysicalMemorySize();
            }
        } catch (Throwable ignored) {
            physicalRamBytes = -1;
        }

        String a = osArch.toLowerCase();
        isArm = a.contains("aarch64") || a.contains("arm") || a.contains("arm64");

        cpuModel = readCpuModel();

        // jdk.incubator.vector is optional (added via --add-modules). Detect if usable.
        boolean vec = false;
        try {
            Class.forName("jdk.incubator.vector.VectorSpecies");
            vec = true;
        } catch (Throwable ignored) {
            vec = false;
        }
        vectorAvailable = vec;
    }

    private static String readCpuModel() {
        // Linux
        try {
            Path cpuinfo = Path.of("/proc/cpuinfo");
            if (Files.exists(cpuinfo)) {
                for (String line : Files.readAllLines(cpuinfo, StandardCharsets.UTF_8)) {
                    if (line.startsWith("model name") || line.startsWith("Hardware")) {
                        int i = line.indexOf(':');
                        if (i >= 0) return line.substring(i + 1).trim();
                    }
                }
            }
        } catch (IOException ignored) {}
        // macOS
        try {
            String[] cmd = {"sysctl", "-n", "machdep.cpu.brand_string"};
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!out.isEmpty()) return out;
        } catch (Throwable ignored) {}
        return null;
    }

    // ---- Accessors -------------------------------------------------------------

    public static String getOsName() { return osName; }
    public static String getOsArch() { return osArch; }
    public static String getOsVersion() { return osVersion; }
    public static String getJavaVersion() { return javaVersion; }
    public static int getCores() { return availableCores; }
    public static boolean isArm() { return isArm; }
    public static boolean isVectorAvailable() { return vectorAvailable; }
    public static String getCpuModel() { return cpuModel == null ? "unknown" : cpuModel; }

    /** Physical RAM in MB, or -1 if the JVM could not report it. */
    public static long getPhysicalRamMB() {
        return physicalRamBytes < 0 ? -1 : physicalRamBytes / (1024L * 1024L);
    }

    /** Max heap (-Xmx) in MB. */
    public static long getMaxHeapMB() {
        return maxHeapBytes / (1024L * 1024L);
    }

    // ---- Tuning computations ---------------------------------------------------

    /** Recommended -Xmx heap size in MB based on physical RAM. */
    public static long suggestHeapMB() {
        long ram = getPhysicalRamMB();
        if (ram <= 0) ram = getMaxHeapMB();          // fall back to current heap
        // Common safe rule: use up to half the RAM, but keep sane bounds.
        long suggested = ram / 2;
        if (suggested < 1024) suggested = 1024;      // at least 1 GB
        if (suggested > 16384) suggested = 16384;    // cap at 16 GB
        return suggested;
    }

    /**
     * Optimal JVM flags for THIS machine (Aikar flags + GC sizing + vector).
     * Returns a single command line string ready to append to `java`.
     */
    public static String recommendedJvmArgs() {
        StringBuilder sb = new StringBuilder();
        sb.append("-Xms").append(suggestHeapMB()).append("M")
          .append(" -Xmx").append(suggestHeapMB()).append("M");
        sb.append(" -XX:+UseG1GC -XX:+ParallelRefProcEnabled");
        sb.append(" -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions");
        sb.append(" -XX:+DisableExplicitGC -XX:+AlwaysPreTouch");
        sb.append(" -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40");
        sb.append(" -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20");
        sb.append(" -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4");
        sb.append(" -XX:InitiatingHeapOccupancyPercent=15");
        sb.append(" -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5");
        sb.append(" -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem");
        sb.append(" -XX:MaxTenuringThreshold=1");
        // G1GC heuristics for many cores / big heaps
        if (availableCores >= 8) sb.append(" -XX:ParallelGCThreads=").append(Math.min(availableCores, 16));
        if (availableCores < 4)  sb.append(" -XX:G1ConcRefinementThreads=2");
        // Java 25 + x86/ARM64: prefer incubator vector if available
        if (vectorAvailable) sb.append(" --add-modules=jdk.incubator.vector");
        sb.append(" -Dusing.aikars.flags=https://mcflags.emc.gs");
        sb.append(" -Daikars.new.flags=true");
        return sb.toString();
    }

    /** Which server.properties values best match this machine. */
    public static List<String> recommendedServerSettings() {
        List<String> out = new ArrayList<>();
        int cores = Math.max(1, availableCores);
        long ramMB = getPhysicalRamMB() > 0 ? getPhysicalRamMB() : getMaxHeapMB();
        long heapMB = getMaxHeapMB();

        int viewDistance;
        if (heapMB >= 12000) viewDistance = 12;
        else if (heapMB >= 6000) viewDistance = 10;
        else viewDistance = 8;

        int simDist = Math.max(4, viewDistance - 2);

        int maxPlayers = 20;
        if (ramMB >= 8000 && cores >= 6) maxPlayers = 100;
        else if (ramMB >= 4000 && cores >= 4) maxPlayers = 60;
        else maxPlayers = 30;

        out.add("view-distance=" + viewDistance);
        out.add("simulation-distance=" + simDist);
        out.add("max-players=" + maxPlayers);

        // Paper thread sizing
        int asyncThreads = Math.max(1, cores / 4);
        int ioThreads = Math.max(2, cores / 2);
        out.add("paper: async-chunk-loading-threads=" + asyncThreads);
        out.add("paper: io-threads=" + ioThreads);
        out.add("paper: max-auto-save=600");
        out.add("spigot: mob-spawn-range=" + (viewDistance >= 10 ? 5 : 4));
        return out;
    }

    /** Component compatibility warnings. Empty = fully compatible. */
    public static List<String> compatibilityWarnings() {
        List<String> w = new ArrayList<>();
        int cores = Math.max(1, availableCores);

        if (cores < 2) {
            w.add("Only " + cores + " logical CPU core(s) detected - expect reduced performance; reduce view-distance & mobs.");
        } else if (cores >= 2 && cores < 4) {
            w.add("Low core count (" + cores + "). Keep view-distance <=8 and avoid heavy plugins.");
        }

        long ram = getPhysicalRamMB();
        if (ram > 0 && ram < 2048) {
            w.add("Physical RAM below 2 GB (" + ram + " MB) - 1.21.10 may struggle; consider upgrading.");
        }
        long heap = getMaxHeapMB();
        if (heap > 0 && heap < 1024) {
            w.add("JVM max heap is small (" + heap + " MB). Consider -Xmx" + suggestHeapMB() + "M.");
        }

        if (isArm && !vectorAvailable) {
            w.add("ARM CPU but jdk.incubator.vector is not loaded - add --add-modules=jdk.incubator.vector for SIMD on ARM64.");
        } else if (!isArm && !vectorAvailable) {
            w.add("jdk.incubator.vector not loaded - add --add-modules=jdk.incubator.vector to enable SIMD optimizations.");
        }

        // Java version compatibility
        try {
            int major = Runtime.version().feature();
            if (major < 21) {
                w.add("Java " + major + " is below the recommended 21+ for 1.21.10 servers; use Java 25 (project target).");
            } else if (major >= 21 && major < 25) {
                w.add("Java " + major + " works, but the project targets Java 25 - upgrade for best performance.");
            }
        } catch (Throwable ignored) {}

        // Heap vs physical RAM ratio
        if (ram > 0 && heap > 0 && heap > ram) {
            w.add("Max heap (" + heap + " MB) exceeds physical RAM (" + ram + " MB) - risk of swapping. Lower -Xmx.");
        }
        return w;
    }

    /**
     * Produces a full hardware report as an ordered list of text lines
     * (suitable for both logging and the /voltpur hardware command).
     */
    public static List<String> hardwareReport() {
        List<String> out = new ArrayList<>();
        out.add("=== [VoltPur] Hardware Compatibility Report ===");
        out.add("OS        : " + osName + " " + osVersion + " (" + osArch + ")");
        out.add("CPU       : " + getCpuModel());
        out.add("Cores     : " + availableCores + " logical");
        out.add("CPU Arch  : " + (isArm ? "ARM64/ARM" : osArch));
        out.add("SIMD/Vec  : " + (vectorAvailable ? "jdk.incubator.vector AVAILABLE" : "not loaded"));
        out.add("RAM       : " + (getPhysicalRamMB() > 0 ? getPhysicalRamMB() + " MB" : "unreported"));
        out.add("Max Heap  : " + getMaxHeapMB() + " MB  (recommended -Xmx" + suggestHeapMB() + "M)");
        out.add("Java      : " + javaVersion);

        List<String> warns = compatibilityWarnings();
        if (warns.isEmpty()) {
            out.add("Compatibility: ALL COMPONENTS COMPATIBLE ✅");
        } else {
            out.add("Compatibility: " + warns.size() + " warning(s)");
            for (String s : warns) out.add("  ⚠ " + s);
        }
        out.add("");
        out.add("Recommended JVM start command:");
        out.add("java " + recommendedJvmArgs() + " -jar server.jar --nogui");
        out.add("");
        out.add("Recommended server settings:");
        for (String s : recommendedServerSettings()) out.add("  " + s);
        return out;
    }

    /** Logs the report at startup and writes it to logs/voltpur-hardware-report.txt */
    public static void reportToLogAndFile() {
        reportToLogAndFile(true);
    }

    /** Variant that can suppress the warning section (respects modules.hardware.warn-incompatible). */
    public static void reportToLogAndFile(boolean showWarnings) {
        List<String> lines = hardwareReport();
        if (!showWarnings) {
            // Remove the warning lines (they start with "  ⚠ " and "Compatibility:")
            lines.removeIf(l -> l.startsWith("  ⚠ ") || l.startsWith("Compatibility:"));
        }
        var logger = Bukkit.getLogger();
        for (String l : lines) logger.info("[VoltPur-HW] " + l);
        try {
            File logDir = new File("logs");
            if (!logDir.exists()) logDir.mkdirs();
            Files.write(Path.of("logs", "voltpur-hardware-report.txt"), lines, StandardCharsets.UTF_8);
            logger.info("[VoltPur-HW] Report -> logs/voltpur-hardware-report.txt");
        } catch (IOException e) {
            logger.warning("[VoltPur-HW] Could not write report file: " + e.getMessage());
        }
    }
}
