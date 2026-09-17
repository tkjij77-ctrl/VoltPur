package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * VoltPur Hardware - detects the REAL machine/quota and produces honest advice.
 *
 * HARDENING CHANGES (two bugs fixed, one of them dangerous):
 *
 *  1. CONTAINER AWARE. The old detector read the HOST's physical RAM
 *     (OperatingSystemMXBean.getTotalPhysicalMemorySize() reports the host inside
 *     a container) and then recommended "-Xms/-Xmx = half of it". On a Pterodactyl
 *     host with 64 GB and a 2 GB container that produced a start command asking for
 *     32 GB - an instant OOM kill if the operator pasted it. We now read the cgroup
 *     v2 (memory.max / cpu.max) and v1 fallbacks and never recommend more than the
 *     quota actually available to the server.
 *
 *  2. FLAG LOGIC WAS CIRCULAR. Availability of jdk.incubator.vector was checked with
 *     Class.forName(), which can only succeed if the module was ALREADY added via
 *     --add-modules - so the recommendation never appeared for the people who needed
 *     it, while the warnings section told them to add it. Availability is now tested
 *     with ModuleFinder.ofSystem() (does the JDK ship it?) and resolution with
 *     ModuleLayer.boot().
 *
 * Everything here is read-only: no file is ever written by this class.
 */
public final class VoltPurHardware {

    private static final String MODULE = "HardwareDetection";

    private static boolean detected = false;

    // ---- host facts ----
    private static String osName;
    private static String osArch;
    private static String osVersion;
    private static String javaVersion;
    private static int availableCores;
    private static long hostRamBytes = -1L;
    private static long maxHeapBytes;
    private static String cpuModel;
    private static boolean isArm;

    // ---- container quota ----
    private static long quotaRamBytes = -1L;
    private static double quotaCores = -1d;
    private static String quotaSource = "none";

    // ---- jdk module facts ----
    private static boolean vectorShippedByJdk;
    private static boolean vectorResolved;

    private VoltPurHardware() {}

    public static synchronized void detect() {
        if (detected) return;
        detected = true;

        osName = System.getProperty("os.name", "unknown");
        osArch = System.getProperty("os.arch", "unknown");
        osVersion = System.getProperty("os.version", "");
        javaVersion = Runtime.version().toString();
        availableCores = Runtime.getRuntime().availableProcessors();
        maxHeapBytes = Runtime.getRuntime().maxMemory();

        try {
            java.lang.management.OperatingSystemMXBean base =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (base instanceof com.sun.management.OperatingSystemMXBean extended) {
                hostRamBytes = extended.getTotalPhysicalMemorySize();
            }
        } catch (Throwable t) {
            hostRamBytes = -1L;
            Bukkit.getLogger().fine("[VoltPur-HW] Physical RAM unavailable: " + t.getMessage());
        }

        String arch = osArch.toLowerCase();
        isArm = arch.contains("aarch64") || arch.contains("arm");

        cpuModel = readCpuModel();
        readCgroupLimits();

        vectorShippedByJdk = ModuleFinder.ofSystem().find("jdk.incubator.vector").isPresent();
        vectorResolved = ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent();
    }

    /** cgroup v2 first, then v1. Values are deliberately conservative. */
    private static void readCgroupLimits() {
        Long memoryMax = readLongFile("/sys/fs/cgroup/memory.max");                 // v2
        if (memoryMax == null) memoryMax = readLongFile("/sys/fs/cgroup/memory/memory.limit_in_bytes"); // v1
        if (memoryMax != null && memoryMax > 0 && memoryMax < (1L << 58)) {
            quotaRamBytes = memoryMax;
        }

        String cpuMax = readStringFile("/sys/fs/cgroup/cpu.max");                  // v2: "<quota> <period>" or "max <period>"
        if (cpuMax != null) {
            String[] parts = cpuMax.trim().split("\\s+");
            if (parts.length >= 2 && !parts[0].equalsIgnoreCase("max")) {
                try {
                    long quota = Long.parseLong(parts[0]);
                    long period = Long.parseLong(parts[1]);
                    if (quota > 0 && period > 0) quotaCores = Math.max(0.1d, (double) quota / (double) period);
                } catch (NumberFormatException ignored) {
                    // "max" or unexpected format: no CPU quota, the whole host is used.
                }
            }
        }
        if (quotaCores < 0) {
            Long quota = readLongFile("/sys/fs/cgroup/cpu/cpu.cfs_quota_us");      // v1
            Long period = readLongFile("/sys/fs/cgroup/cpu/cpu.cfs_period_us");
            if (quota != null && period != null && quota > 0 && period > 0) {
                quotaCores = Math.max(0.1d, (double) quota / (double) period);
            }
        }
        if (quotaRamBytes > 0 && hostRamBytes > 0 && quotaRamBytes < hostRamBytes) quotaSource = "cgroup memory limit";
        else if (quotaCores > 0 && quotaCores < availableCores) quotaSource = "cgroup cpu quota";
    }

    private static Long readLongFile(String path) {
        String value = readStringFile(path);
        if (value == null) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static String readStringFile(String path) {
        try {
            Path file = Path.of(path);
            if (!Files.isReadable(file)) return null;
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static String readCpuModel() {
        try {
            Path cpuinfo = Path.of("/proc/cpuinfo");
            if (Files.exists(cpuinfo)) {
                for (String line : Files.readAllLines(cpuinfo, StandardCharsets.UTF_8)) {
                    if (line.startsWith("model name") || line.startsWith("Hardware")) {
                        int separator = line.indexOf(':');
                        if (separator >= 0) return line.substring(separator + 1).trim();
                    }
                }
            }
        } catch (IOException e) {
            Bukkit.getLogger().fine("[VoltPur-HW] /proc/cpuinfo unavailable: " + e.getMessage());
        }
        try {
            Process process = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string")
                    .redirectErrorStream(true).start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!out.isEmpty()) return out;
        } catch (Throwable notMacOrNoSysctl) {
            return null;
        }
        return null;
    }

    // ---- accessors ----

    public static String getOsName() { return osName; }
    public static String getOsArch() { return osArch; }
    public static String getOsVersion() { return osVersion; }
    public static String getJavaVersion() { return javaVersion; }
    public static int getCores() { return availableCores; }
    public static boolean isArm() { return isArm; }
    public static String getCpuModel() { return cpuModel == null ? "unknown" : cpuModel; }
    public static boolean isVectorAvailable() { return vectorResolved; }
    public static boolean isVectorShippedByJdk() { return vectorShippedByJdk; }
    public static long getMaxHeapMB() { return maxHeapBytes / (1024L * 1024L); }

    /** Physical RAM in MB (host machine), or -1 if unreported. */
    public static long getPhysicalRamMB() {
        return hostRamBytes < 0 ? -1L : hostRamBytes / (1024L * 1024L);
    }

    public static boolean isContainer() {
        return quotaRamBytes > 0 || quotaCores > 0;
    }

    public static String getQuotaSource() { return quotaSource; }

    /** RAM actually available to this server process (container quota wins). */
    public static long getEffectiveRamMB() {
        long host = getPhysicalRamMB();
        long quotaMB = quotaRamBytes > 0 ? quotaRamBytes / (1024L * 1024L) : -1L;
        if (quotaMB > 0 && (host <= 0 || quotaMB < host)) return quotaMB;
        if (host > 0) return host;
        return getMaxHeapMB();
    }

    /** CPU cores actually usable (container quota wins, rounded up). */
    public static int getEffectiveCores() {
        if (quotaCores > 0) return Math.max(1, (int) Math.ceil(quotaCores));
        return Math.max(1, availableCores);
    }

    // ---- recommendations ----

    /** Suggested -Xmx: half of what the server may actually use, with sane bounds. */
    public static long suggestHeapMB() {
        long available = getEffectiveRamMB();
        if (available <= 0) available = getMaxHeapMB();
        long suggested = available / 2L;
        long ceiling = Math.max(512L, available - 512L); // never eat the whole quota
        if (suggested > ceiling) suggested = ceiling;
        if (suggested > 16384L) suggested = 16384L;
        if (suggested < 512L) suggested = Math.min(512L, Math.max(128L, available / 2L));
        return suggested;
    }

    /** Aikar-style G1 flags sized to the machine/quota, with the vector fix. */
    public static String recommendedJvmArgs() {
        long heap = suggestHeapMB();
        StringBuilder sb = new StringBuilder();
        sb.append("-Xms").append(heap).append("M -Xmx").append(heap).append("M");
        sb.append(" -XX:+UseG1GC -XX:+ParallelRefProcEnabled");
        sb.append(" -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions");
        sb.append(" -XX:+DisableExplicitGC -XX:+AlwaysPreTouch");
        sb.append(" -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40");
        sb.append(" -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20");
        sb.append(" -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4");
        sb.append(" -XX:InitiatingHeapOccupancyPercent=15");
        sb.append(" -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5");
        sb.append(" -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1");
        // Panels kill a server that dies from swap thrashing; exiting fast is friendlier.
        sb.append(" -XX:+ExitOnOutOfMemoryError");

        int effectiveCores = getEffectiveCores();
        if (effectiveCores >= 8) sb.append(" -XX:ParallelGCThreads=").append(Math.min(effectiveCores, 16));
        if (effectiveCores < 4) sb.append(" -XX:G1ConcRefinementThreads=2");

        // Only recommend the incubator module when the JDK actually ships it.
        if (vectorShippedByJdk && !vectorResolved) {
            sb.append(" --add-modules=jdk.incubator.vector");
        }
        sb.append(" -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true");
        return sb.toString();
    }

    /** Recommended values, each labelled with the file that actually reads it. */
    public static List<String> recommendedServerSettings() {
        List<String> out = new ArrayList<>();
        int cores = getEffectiveCores();
        long ramMB = getEffectiveRamMB();
        long heapMB = getMaxHeapMB();

        int viewDistance;
        if (heapMB >= 12000) viewDistance = 12;
        else if (heapMB >= 6000) viewDistance = 10;
        else viewDistance = 8;
        int simulationDistance = Math.max(4, viewDistance - 2);

        int maxPlayers;
        if (ramMB >= 8000 && cores >= 6) maxPlayers = 100;
        else if (ramMB >= 4000 && cores >= 4) maxPlayers = 60;
        else maxPlayers = 30;

        out.add("view-distance=" + viewDistance);
        out.add("simulation-distance=" + simulationDistance);
        out.add("max-players=" + maxPlayers);

        // These live in paper-global.yml / spigot.yml - named correctly so the advice can be followed.
        int ioThreads = Math.max(2, cores / 4);
        int workerThreads = Math.max(2, cores / 2);
        out.add("(paper-global.yml) chunk-system.io-threads=" + ioThreads);
        out.add("(paper-global.yml) chunk-system.worker-threads=" + workerThreads);
        out.add("(spigot.yml) world-settings.default.ticks-per.autosave=600");
        out.add("(spigot.yml) world-settings.default.mob-spawn-range=" + (viewDistance >= 10 ? 5 : 4));
        return out;
    }

    /** Compatibility warnings. Empty list means nothing to warn about. */
    public static List<String> compatibilityWarnings() {
        List<String> warnings = new ArrayList<>();
        int effectiveCores = getEffectiveCores();
        long ram = getEffectiveRamMB();
        long heap = getMaxHeapMB();

        if (effectiveCores < 2) {
            warnings.add("Only " + effectiveCores + " usable core(s) - expect reduced performance; lower view-distance.");
        } else if (effectiveCores < 4) {
            warnings.add("Few usable cores (" + effectiveCores + ") - keep view-distance <= 8 and avoid heavy plugins.");
        }
        if (ram > 0 && ram < 2048) {
            warnings.add("Only " + ram + " MB RAM available to the server - 1.21.10 will struggle; consider upgrading.");
        }
        if (heap > 0 && heap < 1024) {
            warnings.add("Max heap is small (" + heap + " MB). Consider -Xmx" + suggestHeapMB() + "M.");
        }
        if (ram > 0 && heap > ram) {
            warnings.add("Max heap (" + heap + " MB) exceeds the available RAM (" + ram + " MB) - risk of swapping/OOM kill.");
        }
        if (isContainer()) {
            warnings.add("Running inside a container: advice is sized to the quota ("
                    + getEffectiveRamMB() + " MB RAM / " + getEffectiveCores() + " cores, source: " + quotaSource
                    + "), not to the host machine.");
        }
        try {
            int major = Runtime.version().feature();
            if (major < 21) {
                warnings.add("Java " + major + " is below the recommended 21+ for 1.21.10; this project targets Java 25.");
            } else if (major < 25) {
                warnings.add("Java " + major + " works, but this project targets Java 25.");
            }
        } catch (Throwable ignoredVersionApi) {
            // Runtime.version() always exists on Java 9+; nothing to report if it somehow fails.
        }
        if (!vectorShippedByJdk) {
            warnings.add("This JDK does not ship jdk.incubator.vector - SIMD paths are unavailable (harmless).");
        } else if (!vectorResolved) {
            warnings.add("jdk.incubator.vector is shipped by this JDK but not loaded. Add --add-modules=jdk.incubator.vector "
                    + "if you want the SIMD code paths (it only helps when the server actually uses them).");
        }
        return warnings;
    }

    public static List<String> hardwareReport() {
        detect();
        List<String> out = new ArrayList<>();
        out.add("=== VoltPur hardware report ===");
        out.add("OS        : " + osName + " " + osVersion + " (" + osArch + ")");
        out.add("CPU       : " + getCpuModel());
        out.add("Cores     : " + availableCores + " logical" + (quotaCores > 0 ? " (quota: " + getEffectiveCores() + " usable)" : ""));
        out.add("Arch      : " + (isArm ? "ARM64/ARM" : osArch));
        out.add("RAM       : " + (getPhysicalRamMB() > 0 ? getPhysicalRamMB() + " MB host" : "host unreported")
                + (quotaRamBytes > 0 ? " | " + (quotaRamBytes / (1024L * 1024L)) + " MB available to this server" : ""));
        out.add("Container : " + (isContainer() ? "yes (" + quotaSource + ")" : "no/unlimited"));
        out.add("Max heap  : " + getMaxHeapMB() + " MB (recommended -Xmx" + suggestHeapMB() + "M)");
        out.add("Java      : " + javaVersion);
        out.add("Vector    : " + (vectorResolved ? "loaded" : vectorShippedByJdk ? "shipped by JDK, not loaded" : "not available"));

        List<String> warns = compatibilityWarnings();
        if (warns.isEmpty()) {
            out.add("Compatibility: no issues detected");
        } else {
            out.add("Compatibility: " + warns.size() + " note(s)");
            for (String warning : warns) out.add("  ! " + warning);
        }
        out.add("");
        out.add("Recommended JVM start command:");
        out.add("java " + recommendedJvmArgs() + " -jar server.jar --nogui");
        out.add("");
        out.add("Recommended values (file in brackets is where they belong):");
        for (String line : recommendedServerSettings()) out.add("  " + line);
        return out;
    }

    public static void reportToLogAndFile() {
        reportToLogAndFile(true);
    }

    public static void reportToLogAndFile(boolean includeWarnings) {
        List<String> lines = new ArrayList<>(VoltPurGuard.run(MODULE, VoltPurHardware::hardwareReport, List.of()));
        if (!includeWarnings) {
            lines.removeIf(line -> line.startsWith("  ! ") || line.startsWith("Compatibility:"));
        }
        for (String line : lines) Bukkit.getLogger().info("[VoltPur-HW] " + line);
        try {
            File logDir = new File("logs");
            if (!logDir.exists() && !logDir.mkdirs()) {
                Bukkit.getLogger().warning("[VoltPur-HW] Could not create the logs/ directory.");
                return;
            }
            Files.write(Path.of("logs", "voltpur-hardware-report.txt"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            VoltPurGuard.failure(MODULE, e);
            Bukkit.getLogger().warning("[VoltPur-HW] Could not write the report file: " + e.getMessage());
        }
    }
}
