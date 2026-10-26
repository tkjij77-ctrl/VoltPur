package org.purpurmc.purpur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.entity.SpawnCategory;
import org.purpurmc.purpur.util.VoltPurLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VoltPur Autotune (§4.1, §4.3)
 * Adaptive performance controller with hysteresis, root-cause diagnosis,
 * verification loop, and automatic/manual rollback to base values.
 */
public final class VoltPurAutotune {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File AUTOTUNE_FILE = new File("voltpur-autotune.json");

    private static final Map<String, WorldBase> WORLD_BASES = new ConcurrentHashMap<>();
    private static final List<DecisionRecord> HISTORY = Collections.synchronizedList(new ArrayList<>());
    private static int consecutiveHighCycles = 0;
    private static double lastMspt = 20.0;
    private static double averageMspt = 20.0;

    public static class WorldBase {
        public int baseViewDistance;
        public int baseSimulationDistance;
        public int baseMonsterLimit;

        public WorldBase() {}

        public WorldBase(int view, int sim, int monster) {
            this.baseViewDistance = view;
            this.baseSimulationDistance = sim;
            this.baseMonsterLimit = monster;
        }
    }

    public static class Diagnostic {
        public final String type;
        public final int entityCount;
        public final int monsterCount;
        public final int chunkCount;

        public Diagnostic(String type, int entityCount, int monsterCount, int chunkCount) {
            this.type = type;
            this.entityCount = entityCount;
            this.monsterCount = monsterCount;
            this.chunkCount = chunkCount;
        }
    }

    public static class DecisionRecord {
        public double triggerMspt;
        public String cause;
        public String action;
        public String worldName;
        public String param;
        public int beforeVal;
        public int afterVal;
        public String outcome;
        public double afterMspt;
        public long timestamp;

        public DecisionRecord() {}

        public DecisionRecord(double triggerMspt, String cause, String action, String worldName,
                              String param, int beforeVal, int afterVal, String outcome) {
            this.triggerMspt = triggerMspt;
            this.cause = cause;
            this.action = action;
            this.worldName = worldName;
            this.param = param;
            this.beforeVal = beforeVal;
            this.afterVal = afterVal;
            this.outcome = outcome;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private VoltPurAutotune() {}

    public static void init() {
        loadBaseSettings();
        if (Bukkit.getServer() != null) {
            for (World w : Bukkit.getWorlds()) {
                captureWorldBase(w);
            }
        }
    }

    public static void resetForTesting() {
        WORLD_BASES.clear();
        HISTORY.clear();
        consecutiveHighCycles = 0;
        lastMspt = 20.0;
        averageMspt = 20.0;
    }

    public static void captureWorldBase(World world) {
        if (world == null) return;
        String name = world.getName();
        if (!WORLD_BASES.containsKey(name)) {
            WorldBase base = new WorldBase(
                    world.getViewDistance(),
                    world.getSimulationDistance(),
                    world.getSpawnLimit(SpawnCategory.MONSTER)
            );
            WORLD_BASES.put(name, base);
            saveBaseSettings();
        }
    }

    public static Map<String, WorldBase> getBaseSettings() {
        return Collections.unmodifiableMap(WORLD_BASES);
    }

    public static int getConsecutiveHighCycles() {
        return consecutiveHighCycles;
    }

    public static double getAverageMspt() {
        return averageMspt;
    }

    public static List<DecisionRecord> getHistory() {
        synchronized (HISTORY) {
            return new ArrayList<>(HISTORY);
        }
    }

    public static List<String> getHistoryLines() {
        List<String> lines = new ArrayList<>();
        synchronized (HISTORY) {
            for (DecisionRecord r : HISTORY) {
                lines.add(String.format("[%s] %s %s (%d -> %d) outcome=%s (MSPT: %.1f -> %.1f)",
                        r.worldName, r.param, r.action, r.beforeVal, r.afterVal, r.outcome, r.triggerMspt, r.afterMspt));
            }
        }
        return lines;
    }

    public static Diagnostic diagnose(World world) {
        int monsterCount = 0;
        int entityCount = 0;
        int chunkCount = 0;

        try {
            if (world.getEntities() != null) {
                entityCount = world.getEntities().size();
                for (org.bukkit.entity.Entity e : world.getEntities()) {
                    if (e instanceof Monster) {
                        monsterCount++;
                    }
                }
            }
            if (world.getLoadedChunks() != null) {
                chunkCount = world.getLoadedChunks().length;
            }
        } catch (Throwable t) {
            VoltPurLogger.warn("VoltPur-Autotune", "diagnose", t);
        }

        if (monsterCount > 200) {
            return new Diagnostic("MONSTERS", entityCount, monsterCount, chunkCount);
        } else if (chunkCount > 500) {
            return new Diagnostic("CHUNKS", entityCount, monsterCount, chunkCount);
        } else {
            return new Diagnostic("GENERAL", entityCount, monsterCount, chunkCount);
        }
    }

    public static void tick(double mspt) {
        lastMspt = mspt;
        averageMspt = mspt;

        if (mspt > 40.0) {
            consecutiveHighCycles++;
        } else {
            consecutiveHighCycles = 0;
            return;
        }

        // Hysteresis: require 2 consecutive high cycles before triggering
        if (consecutiveHighCycles < 2) {
            return;
        }

        // Trigger tuning
        try {
            if (Bukkit.getServer() == null) return;
            List<World> worlds = Bukkit.getWorlds();
            if (worlds == null || worlds.isEmpty()) return;

            for (World w : worlds) {
                captureWorldBase(w);
                Diagnostic diag = diagnose(w);
                tuneWorld(w, mspt, diag);
            }
        } catch (Throwable t) {
            VoltPurLogger.warn("VoltPur-Autotune", "tick", t);
        }
    }

    private static void tuneWorld(World world, double mspt, Diagnostic diag) {
        boolean active = VoltPurConfig.isAutotuneActive();
        int curSim = world.getSimulationDistance();
        int curView = world.getViewDistance();

        int minSim = VoltPurConfig.autotuneMinSimulationDistance;
        int minView = VoltPurConfig.autotuneMinViewDistance;

        String action = "SIM_DISTANCE_DECREASE";
        String param = "SIM";
        int oldVal = curSim;
        int newVal = Math.max(minSim, curSim - 1);

        if (curSim <= minSim && curView > minView) {
            action = "VIEW_DISTANCE_DECREASE";
            param = "VIEW";
            oldVal = curView;
            newVal = Math.max(minView, curView - 1);
        }

        if (oldVal == newVal) {
            // Already at safe minimums, cannot reduce further
            return;
        }

        String outcome = active ? "ACTIVE" : "ADVISORY";
        DecisionRecord rec = new DecisionRecord(mspt, diag.type, action, world.getName(), param, oldVal, newVal, outcome);

        if (active) {
            if ("SIM".equals(param)) {
                world.setSimulationDistance(newVal);
            } else if ("VIEW".equals(param)) {
                world.setViewDistance(newVal);
            }
            VoltPurLogger.info("VoltPur-Autotune", "Applied autotune reduction on '" + world.getName() + "': "
                    + param + " (" + oldVal + " -> " + newVal + ") [MSPT " + mspt + "]");
        } else {
            VoltPurLogger.info("VoltPur-Autotune", "Advisory autotune recommended on '" + world.getName() + "': "
                    + param + " (" + oldVal + " -> " + newVal + ") [MSPT " + mspt + "]");
        }

        recordDecision(rec);
    }

    public static void verifyOutcome(World world, DecisionRecord rec, double afterMspt) {
        rec.afterMspt = afterMspt;
        // If MSPT did not improve by at least 15% or is still > 45.0, rollback
        if (afterMspt >= rec.triggerMspt * 0.95 || afterMspt > 45.0) {
            rec.outcome = "ROLLED_BACK";
            if (world != null) {
                if ("SIM".equals(rec.param)) {
                    world.setSimulationDistance(rec.beforeVal);
                } else if ("VIEW".equals(rec.param)) {
                    world.setViewDistance(rec.beforeVal);
                }
            }
            VoltPurLogger.info("VoltPur-Autotune", "Autotune change for " + rec.param + " rolled back: MSPT did not improve (" + afterMspt + " ms)");
        } else {
            rec.outcome = "CONFIRMED";
            VoltPurLogger.info("VoltPur-Autotune", "Autotune change for " + rec.param + " confirmed: MSPT improved to " + afterMspt + " ms");
        }
    }

    private static void recordDecision(DecisionRecord rec) {
        synchronized (HISTORY) {
            if (HISTORY.size() >= 20) {
                HISTORY.remove(0);
            }
            HISTORY.add(rec);
        }
    }

    public static void rollbackWorld(World target) {
        if (target == null) return;
        WorldBase base = WORLD_BASES.get(target.getName());
        if (base != null) {
            target.setViewDistance(base.baseViewDistance);
            target.setSimulationDistance(base.baseSimulationDistance);
            target.setSpawnLimit(SpawnCategory.MONSTER, base.baseMonsterLimit);
            VoltPurLogger.info("VoltPur-Autotune", "Rolled back world '" + target.getName() + "' to base settings.");
        }
    }

    public static void rollback(Object target) {
        if (target instanceof World) {
            rollbackWorld((World) target);
            return;
        }

        try {
            if (Bukkit.getServer() != null) {
                List<World> worlds = Bukkit.getWorlds();
                if (worlds != null) {
                    for (World w : worlds) {
                        rollbackWorld(w);
                    }
                }
            }
        } catch (Throwable t) {
            VoltPurLogger.warn("VoltPur-Autotune", "rollback", t);
        }
    }

    private static void saveBaseSettings() {
        try (FileWriter writer = new FileWriter(AUTOTUNE_FILE)) {
            GSON.toJson(WORLD_BASES, writer);
        } catch (IOException e) {
            VoltPurLogger.warn("VoltPur-Autotune", "saveBaseSettings", e);
        }
    }

    private static void loadBaseSettings() {
        if (!AUTOTUNE_FILE.exists()) return;
        try (FileReader reader = new FileReader(AUTOTUNE_FILE)) {
            Type type = new TypeToken<Map<String, WorldBase>>() {}.getType();
            Map<String, WorldBase> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                WORLD_BASES.putAll(loaded);
            }
        } catch (IOException e) {
            VoltPurLogger.warn("VoltPur-Autotune", "loadBaseSettings", e);
        }
    }
}
