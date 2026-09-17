package org.purpurmc.purpur;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VoltPur module registry - implementation status AND live runtime state.
 *
 * Three honest states:
 *   ACTIVE  - implemented in this build (opt-in modules say so explicitly).
 *   PARTIAL - implemented with limits, or provided by upstream Paper/Purpur.
 *   PLANNED - name reserved, NOT implemented. Reported as such, never as working.
 *
 * The registry deliberately does NOT print "enabled" for something that is merely
 * implemented: enabled state is a runtime fact, set by the module itself through
 * {@link #setRuntime(String, boolean)} at init time. Health (runs/fails) comes
 * from {@link VoltPurGuard}.
 *
 * Definition of done for moving a module to ACTIVE:
 *   1. it runs,
 *   2. it is visible in /voltpur modules with non-zero runs,
 *   3. it has a measurable line in /voltpur benchmark OR a verifiable side effect.
 */
public final class VoltPurModules {

    public enum Status { ACTIVE, PARTIAL, PLANNED }

    private record Entry(Status status, boolean optIn, String note) {}

    private static final Map<String, Entry> MODULES = new LinkedHashMap<>();
    private static final Map<String, Boolean> RUNTIME = new ConcurrentHashMap<>();

    private VoltPurModules() {}

    static {
        // ---- Implemented, always-on ----
        register("HardwareDetection", Status.ACTIVE, false, "/voltpur hardware + cgroup-aware recommendations");
        register("ModuleGuard", Status.ACTIVE, false, "counts runs/failures so a dead module cannot look healthy");
        register("TPSMonitor", Status.ACTIVE, false, "logs when TPS drops below 18");
        register("ChunkWarning", Status.ACTIVE, false, "warns on excessive loaded chunks");
        register("WorldStability", Status.ACTIVE, false, "read-only world/storage report (never writes server.properties)");
        register("Updater", Status.ACTIVE, false, "stage -> verify (SHA-256) -> confirm, with rollback");

        // ---- Implemented, opt-in (default OFF; they change files, gameplay or network) ----
        register("ItemLimiter", Status.ACTIVE, true, "removes only old/unnamed dropped items; OFF by default");
        register("DynamicOptimizer", Status.ACTIVE, true, "adapts spawn limit/simulation distance and auto-reverts");
        register("HardwareAutoTune", Status.ACTIVE, true, "writes server.properties/spigot.yml (needs restart)");
        register("PAdminWebUI", Status.ACTIVE, true, "loopback + Basic Auth + read-only");
        register("DiscordWebhook", Status.ACTIVE, true, "server/player notifications over HTTPS webhook");
        register("WorldBackup", Status.ACTIVE, true, "consistent world zips (save-off/flush/save-on)");
        register("ResourcePackHTTP", Status.ACTIVE, true, "local pack server with an unguessable path");

        // ---- Partial ----
        register("AikarFlagsAuto", Status.PARTIAL, true, "recommends flags; never edits the panel start command");
        register("AntiExploit", Status.PARTIAL, true, "basic item-age guard only; no movement/packet heuristics");
        register("BedrockBridge", Status.PARTIAL, false, "detection/report only - Geyser still required");
        register("EntityActivation", Status.PARTIAL, false, "comes from Paper's built-in activation range");

        // ---- Honest PLANNED (not implemented - no code path exists) ----
        register("HopperOptimization", Status.PLANNED, false, "no NMS patch; only spigot.yml hopper-check via HardwareAutoTune");
        register("CollisionOptimization", Status.PLANNED, false, "not implemented");
        register("MemoryOptimization", Status.PLANNED, false, "not implemented");
        register("NetworkOptimization", Status.PLANNED, false, "not implemented");
        register("RedstoneOptimization", Status.PLANNED, false, "not implemented");
        register("ChunkLoading", Status.PLANNED, false, "not implemented (Paper's own chunk system is used)");
        register("LightEngine", Status.PLANNED, false, "not implemented");
        register("ConnectionStability", Status.PLANNED, false, "not implemented");
        register("PerWorldPlugin", Status.PLANNED, false, "not implemented (plugin-pro/ is only a folder)");
    }

    private static void register(String name, Status status, boolean optIn, String note) {
        MODULES.put(name, new Entry(status, optIn, note));
    }

    /** Module name -> implementation status (used by PAdmin's JSON API too). */
    public static Map<String, Status> all() {
        Map<String, Status> out = new LinkedHashMap<>();
        MODULES.forEach((name, entry) -> out.put(name, entry.status()));
        return out;
    }

    public static boolean isOptIn(String name) {
        Entry entry = MODULES.get(name);
        return entry != null && entry.optIn();
    }

    /** Called by each module at init so the registry reflects reality, not intent. */
    public static void setRuntime(String name, boolean running) {
        RUNTIME.put(name, running);
    }

    public static boolean isRunning(String name) {
        return Boolean.TRUE.equals(RUNTIME.get(name));
    }

    public static int activeCount() {
        return (int) MODULES.values().stream().filter(entry -> entry.status() == Status.ACTIVE).count();
    }

    public static int partialCount() {
        return (int) MODULES.values().stream().filter(entry -> entry.status() == Status.PARTIAL).count();
    }

    public static int plannedCount() {
        return (int) MODULES.values().stream().filter(entry -> entry.status() == Status.PLANNED).count();
    }

    public static int totalCount() {
        return MODULES.size();
    }

    public static String line(String name) {
        Entry entry = MODULES.get(name);
        if (entry == null) return "❔ " + name + " - untracked";
        String icon = switch (entry.status()) {
            case ACTIVE -> "🟢";
            case PARTIAL -> "🟠";
            case PLANNED -> "🟡";
        };
        StringBuilder sb = new StringBuilder();
        sb.append(icon).append(' ').append(name).append(" - ").append(entry.status());
        if (entry.optIn()) {
            Boolean running = RUNTIME.get(name);
            sb.append(running == null ? " [opt-in]" : running ? " [enabled]" : " [disabled]");
        }
        if (!entry.note().isEmpty()) sb.append(" · ").append(entry.note());
        return sb.toString();
    }
}
