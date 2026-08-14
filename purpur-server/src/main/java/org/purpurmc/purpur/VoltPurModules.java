package org.purpurmc.purpur;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VoltPur Modules - an HONEST module registry.
 *
 * The old system reported 21 modules as "ENABLED" even though most had no
 * implementation. Here every module carries a real status:
 *   ACTIVE  - implemented and wired to run in this build.
 *   PARTIAL - partial/limited implementation (safe, but not full).
 *   PLANNED - claimed name only; NOT implemented yet. Reported honestly.
 *
 * Definition of done: a module may only be set ACTIVE when it runs and has a
 * measurable line in /voltpur benchmark (or an equivalent verifiable effect).
 */
public final class VoltPurModules {

    public enum Status { ACTIVE, PARTIAL, PLANNED }

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

        // ---- Partial ----
        register("AikarFlagsAuto",      Status.PARTIAL);  // recommends flags; not applied automatically
        register("BedrockBridge",       Status.PARTIAL);  // detection only (no QoS patch applied)
        register("AntiExploit",         Status.PARTIAL);  // minimal
        // Entity Activation is ACTIVE via Paper's built-in ActivationRange (EAR);
        // VoltPur adds no duplicate patch. Reported PARTIAL until a VoltPur-specific
        // tuning/measurement is wired to it.
        register("EntityActivation",    Status.PARTIAL);  // via Paper EAR (already on)

        // ---- Honest PLANNED (NOT implemented yet) ----
        // Hopper sleep: Java-layer config is ready (opt-in) but the NMS patch
        // (empty-hopper rest in HopperBlockEntity.pushItemsTick) must be applied
        // via applyAllPatches+rebuildPatches on the build machine. See docs/HOPPER_SNIPPET.md.
        register("HopperOptimization",  Status.PLANNED);  // NMS patch pending applyPatches
        register("CollisionOptimization", Status.PLANNED);
        register("MemoryOptimization",  Status.PLANNED);  // FerriteCore not applied
        register("NetworkOptimization", Status.PLANNED);
        register("RedstoneOptimization", Status.PLANNED);
        register("ChunkLoading",        Status.PLANNED);  // C2ME not applied
        register("LightEngine",         Status.PLANNED);
        register("ConnectionStability", Status.PLANNED);
        register("DiscordWebhook",      Status.PLANNED);
        register("WorldBackup",         Status.PLANNED);
        register("ResourcePackHTTP",    Status.PLANNED);
        register("PerWorldPlugin",      Status.PLANNED);
    }

    private static void register(String name, Status s) {
        MODULES.put(name, s);
    }

    public static Map<String, Status> all() { return MODULES; }
    public static int activeCount() {
        return (int) MODULES.values().stream().filter(s -> s == Status.ACTIVE).count();
    }
    public static int totalCount() { return MODULES.size(); }

    public static String line(String name) {
        Status s = MODULES.get(name);
        String icon = s == Status.ACTIVE ? "🟢" : s == Status.PARTIAL ? "🟠" : "🟡";
        return icon + " " + name + " - " + s;
    }
}
