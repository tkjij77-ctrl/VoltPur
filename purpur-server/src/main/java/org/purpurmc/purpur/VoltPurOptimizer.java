package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.SpawnCategory;

import java.util.HashMap;
import java.util.Map;

/**
 * VoltPur DynamicOptimizer - adaptive tuning that always returns to normal.
 *
 * HARDENING CHANGES (this used to be dangerous and always-on):
 *  - OPT-IN: does nothing unless modules.performance.optimizer.enabled=true.
 *  - STARTUP GRACE: ignores metrics for the first `startup-grace-seconds`, because
 *    a freshly started server legitimately reports low TPS while loading chunks.
 *  - HYSTERESIS: the pressure must persist for `sustain-seconds` before acting
 *    (the old version acted on a single 1-minute TPS sample).
 *  - AUTO-REVERT: after `revert-after-minutes` it restores the ORIGINAL spawn
 *    limits and simulation distances, so a temporary lag spike cannot permanently
 *    disable mob farms.
 *  - Every value it changes is recorded and logged before/after, and the whole
 *    cycle is wrapped in VoltPurGuard so a failure is visible.
 *
 * It only uses the Bukkit API (no NMS, no file edits), so it is safe to run and
 * safe to stop.
 */
public final class VoltPurOptimizer {

    private static final String MODULE = "DynamicOptimizer";
    private static final long EVALUATION_PERIOD_TICKS = 600L; // every 30s

    private record Original(int spawnLimit, int simulationDistance) {}

    private static final Map<String, Original> ORIGINALS = new HashMap<>();
    private static boolean scheduled = false;
    private static boolean applied = false;
    private static long lowSince = 0L;
    private static long appliedAt = 0L;

    private VoltPurOptimizer() {}

    public static void init() {
        if (scheduled) return;
        scheduled = true;
        VoltPurModules.setRuntime(MODULE, VoltPurConfig.optimizerEnabled);
        if (!VoltPurConfig.optimizerEnabled) {
            Bukkit.getLogger().info("[VoltPur-Opt] Dynamic optimizer is disabled (opt-in). "
                    + "Enable modules.performance.optimizer.enabled in voltpur.yml if you want adaptive tuning.");
            return;
        }
        long graceTicks = (long) VoltPurConfig.optimizerStartupGraceSeconds * 20L;
        try {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(VoltPurPlugin.get(),
                    () -> VoltPurGuard.run(MODULE, VoltPurOptimizer::evaluate),
                    graceTicks, EVALUATION_PERIOD_TICKS);
            Bukkit.getLogger().info("[VoltPur-Opt] Enabled: evaluating every 30s after a "
                    + VoltPurConfig.optimizerStartupGraceSeconds + "s startup grace, reverting after "
                    + VoltPurConfig.optimizerRevertAfterMinutes + " min.");
        } catch (Throwable t) {
            VoltPurGuard.failure(MODULE, t);
        }
    }

    /** One evaluation tick (main thread). */
    private static void evaluate() {
        if (!VoltPurConfig.optimizerEnabled) return;

        double tps = safeTps();
        long now = System.currentTimeMillis();
        long sustainMs = VoltPurConfig.optimizerSustainSeconds * 1000L;
        long revertMs = VoltPurConfig.optimizerRevertAfterMinutes * 60_000L;

        boolean pressured = tps > 0 && tps < 18.0;

        if (!pressured) {
            lowSince = 0L;
            if (applied && (now - appliedAt) >= 0L) {
                revert("TPS recovered to " + String.format("%.2f", tps));
            }
            return;
        }

        if (lowSince == 0L) {
            lowSince = now;
            Bukkit.getLogger().info("[VoltPur-Opt] TPS " + String.format("%.2f", tps)
                    + " below 18 - watching for " + VoltPurConfig.optimizerSustainSeconds + "s before acting.");
            return;
        }
        if ((now - lowSince) < sustainMs) return; // not sustained: do nothing

        if (applied) {
            if ((now - appliedAt) > revertMs) revert("revert window elapsed while TPS was still low");
            return;
        }
        applyTuning(tps);
    }

    private static void applyTuning(double tps) {
        int entityCount = 0;
        for (World world : Bukkit.getWorlds()) {
            entityCount += world.getEntities().size();
        }
        int targetMonsters = tps < 15.0 ? 20 : 30;
        int targetSimDistance = tps < 15.0 ? 4 : 6;

        ORIGINALS.clear();
        int touched = 0;
        for (World world : Bukkit.getWorlds()) {
            try {
                int originalSpawn = world.getSpawnLimit(SpawnCategory.MONSTER);
                int originalSim = world.getSimulationDistance();
                ORIGINALS.put(world.getName(), new Original(originalSpawn, originalSim));
                world.setSpawnLimit(SpawnCategory.MONSTER, targetMonsters);
                world.setSimulationDistance(targetSimDistance);
                touched++;
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[VoltPur-Opt] Could not adjust world '" + world.getName()
                        + "': " + t.getMessage());
            }
        }
        applied = true;
        appliedAt = System.currentTimeMillis();
        Bukkit.getLogger().info("[VoltPur-Opt] APPLIED (tps=" + String.format("%.2f", tps) + ", entities=" + entityCount
                + "): monster-spawn-limit=" + targetMonsters + ", simulation-distance=" + targetSimDistance
                + " across " + touched + " world(s). Will restore automatically in "
                + VoltPurConfig.optimizerRevertAfterMinutes + " min.");
    }

    private static void revert(String reason) {
        int restored = 0;
        for (World world : Bukkit.getWorlds()) {
            Original original = ORIGINALS.get(world.getName());
            if (original == null) continue;
            try {
                world.setSpawnLimit(SpawnCategory.MONSTER, original.spawnLimit());
                world.setSimulationDistance(original.simulationDistance());
                restored++;
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[VoltPur-Opt] Could not restore world '" + world.getName()
                        + "': " + t.getMessage());
            }
        }
        ORIGINALS.clear();
        applied = false;
        appliedAt = 0L;
        lowSince = 0L;
        Bukkit.getLogger().info("[VoltPur-Opt] RESTORED original settings on " + restored + " world(s) - " + reason + ".");
    }

    /** Human-readable state for /voltpur modules and the PAdmin page. */
    public static String describe() {
        if (!VoltPurConfig.optimizerEnabled) return "disabled (opt-in)";
        if (!applied) return "watching (no changes applied)";
        long seconds = (System.currentTimeMillis() - appliedAt) / 1000L;
        return "tuning active for " + seconds + "s (auto-reverts)";
    }

    private static double safeTps() {
        try {
            return Bukkit.getServer().getTPS()[0];
        } catch (Throwable t) {
            return -1.0;
        }
    }
}
