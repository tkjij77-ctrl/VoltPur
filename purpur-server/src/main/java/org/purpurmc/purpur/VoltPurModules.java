package org.purpurmc.purpur;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VoltPur Modules - an HONEST module registry.
 *
 * Every module carries a real status:
 *   ACTIVE   - implemented and wired to run in this build.
 *   PARTIAL  - partial/limited implementation (safe, but not full).
 *   DEGRADED - module encountered repeated failures and entered degraded mode.
 *   PLANNED  - claimed name only; NOT implemented yet. Reported honestly.
 */
public final class VoltPurModules {

    public enum Status { ACTIVE, PARTIAL, DEGRADED, PLANNED }

    private static final Map<String, Status> MODULES = new LinkedHashMap<>();

    private VoltPurModules() {}

    static {
        // ---- Truly active in this build (verifiable) ----
        register("HardwareDetection",   Status.ACTIVE);   // /voltpur hardware
        register("HardwareAutoTune",    Status.ACTIVE);   // opt-in tuning
        register("ItemLimiter",         Status.ACTIVE);   // dropped-item cleanup
        register("TPSMonitor",          Status.ACTIVE);   // low-TPS logging
        register("WorldStability",      Status.ACTIVE);   // VoltPurWorldCheck
        register("PterodactylFix",      Status.ACTIVE);   // stability on panels
        register("Updater",             Status.ACTIVE);   // /vo up + /vo up list
        register("PAdminWebUI",         Status.ACTIVE);   // /padmin
        register("DynamicOptimizer",    Status.ACTIVE);   // adaptive spigot.yml tuning
        register("DiscordWebhook",      Status.ACTIVE);   // VoltPurDiscord (opt-in webhook notifications)
        register("WorldBackup",         Status.ACTIVE);   // VoltPurBackup (opt-in scheduled world zips)
        register("ResourcePackHTTP",    Status.ACTIVE);   // VoltPurResourcePack (opt-in HTTP pack server)
        register("PlusLoader",          Status.ACTIVE);   // plugin-plus low-level loader (§6)
        register("AiPracticalLayer",    Status.ACTIVE);   // practical AI router + client + doctor (§5)

        // ---- Partial ----
        register("AikarFlagsAuto",      Status.PARTIAL);  // recommends flags; not applied automatically
        register("BedrockBridge",       Status.PARTIAL);  // detection only (no QoS patch applied)
        register("AntiExploit",         Status.PARTIAL);  // minimal
        register("EntityActivation",    Status.PARTIAL);  // via Paper EAR (already on)

        // ---- Honest PLANNED (NOT implemented yet) ----
        register("HopperOptimization",  Status.PLANNED);
        register("CollisionOptimization", Status.PLANNED);
        register("MemoryOptimization",  Status.PLANNED);
        register("NetworkOptimization", Status.PLANNED);
        register("RedstoneOptimization", Status.PLANNED);
        register("ChunkLoading",        Status.PLANNED);
        register("LightEngine",         Status.PLANNED);
        register("ConnectionStability", Status.PLANNED);
        register("PerWorldPlugin",      Status.PLANNED);
    }

    private static void register(String name, Status s) {
        MODULES.put(name, s);
    }

    public static synchronized void setStatus(String name, Status s) {
        MODULES.put(name, s);
    }

    public static Map<String, Status> all() { return MODULES; }

    public static int activeCount() {
        return (int) MODULES.values().stream().filter(s -> s == Status.ACTIVE).count();
    }
    public static int partialCount() {
        return (int) MODULES.values().stream().filter(s -> s == Status.PARTIAL).count();
    }
    public static int degradedCount() {
        return (int) MODULES.values().stream().filter(s -> s == Status.DEGRADED).count();
    }
    public static int plannedCount() {
        return (int) MODULES.values().stream().filter(s -> s == Status.PLANNED).count();
    }
    public static int totalCount() { return MODULES.size(); }

    public static String line(String name) {
        Status s = MODULES.get(name);
        String icon = s == Status.ACTIVE ? "🟢" : s == Status.PARTIAL ? "🟠" : s == Status.DEGRADED ? "🔴" : "🟡";
        return icon + " " + name + " - " + s;
    }
}
