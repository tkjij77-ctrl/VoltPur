package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class VoltPurAutotuneTest {

    private Server server;
    private World world;
    private final File autotuneJsonFile = new File("voltpur-autotune.json");

    @BeforeEach
    void setUp() {
        VoltPurAutotune.resetForTesting();
        VoltPurConfig.optimizerEnabled = true;
        VoltPurConfig.autotuneMode = "active";
        VoltPurConfig.autotuneMinViewDistance = 4;
        VoltPurConfig.autotuneMinSimulationDistance = 4;

        server = mock(Server.class);
        world = mock(World.class);

        when(server.getLogger()).thenReturn(Logger.getLogger("VoltPurTest"));
        when(server.getWorlds()).thenReturn(List.of(world));
        when(world.getName()).thenReturn("world_test");
        when(world.getViewDistance()).thenReturn(10);
        when(world.getSimulationDistance()).thenReturn(8);
        when(world.getSpawnLimit(SpawnCategory.MONSTER)).thenReturn(70);
        when(world.getEntities()).thenReturn(new ArrayList<>());
        when(world.getLoadedChunks()).thenReturn(new org.bukkit.Chunk[0]);

        VoltPurTestHelper.setMockServer(server);
    }

    @AfterEach
    void tearDown() {
        VoltPurTestHelper.clearMockServer();
        if (autotuneJsonFile.exists()) {
            autotuneJsonFile.delete();
        }
    }

    @Test
    void testBaseSettingsCaptureAndPersistence() {
        VoltPurAutotune.captureWorldBase(world);
        var baseMap = VoltPurAutotune.getBaseSettings();
        assertTrue(baseMap.containsKey("world_test"));
        VoltPurAutotune.WorldBase base = baseMap.get("world_test");
        assertEquals(10, base.baseViewDistance);
        assertEquals(8, base.baseSimulationDistance);
        assertEquals(70, base.baseMonsterLimit);

        // File should exist
        assertTrue(autotuneJsonFile.exists());
    }

    @Test
    void testHysteresisTwoCycleThreshold() {
        // Cycle 1: MSPT = 45 ms (> 40.0) -> consecutive cycles = 1, NO changes applied
        VoltPurAutotune.tick(45.0);
        assertEquals(1, VoltPurAutotune.getConsecutiveHighCycles());
        verify(world, never()).setViewDistance(anyInt());
        verify(world, never()).setSimulationDistance(anyInt());

        // Cycle 2: MSPT = 45 ms -> consecutive cycles = 2 -> trigger reduction!
        VoltPurAutotune.tick(45.0);
        assertEquals(2, VoltPurAutotune.getConsecutiveHighCycles());
        // Sim distance or view distance should be reduced
        verify(world, atLeastOnce()).setSimulationDistance(7);
        assertEquals(1, VoltPurAutotune.getHistory().size());
    }

    @Test
    void testHysteresisResetOnNormalMspt() {
        // Cycle 1: MSPT = 45 ms
        VoltPurAutotune.tick(45.0);
        assertEquals(1, VoltPurAutotune.getConsecutiveHighCycles());

        // Cycle 2: Normal MSPT = 32 ms -> resets consecutive cycles to 0
        VoltPurAutotune.tick(32.0);
        assertEquals(0, VoltPurAutotune.getConsecutiveHighCycles());

        // Cycle 3: MSPT = 45 ms again -> starts at 1 cycle, NO reduction
        VoltPurAutotune.tick(45.0);
        assertEquals(1, VoltPurAutotune.getConsecutiveHighCycles());
        verify(world, never()).setViewDistance(anyInt());
        verify(world, never()).setSimulationDistance(anyInt());
    }

    @Test
    void testRootCauseClassifier() {
        // 1. Monster heavy world
        List<org.bukkit.entity.Entity> entities = new ArrayList<>();
        for (int i = 0; i < 350; i++) {
            Monster m = mock(Monster.class);
            entities.add(m);
        }
        when(world.getEntities()).thenReturn(entities);
        VoltPurAutotune.Diagnostic diagEntities = VoltPurAutotune.diagnose(world);
        assertEquals("MONSTERS", diagEntities.type);

        // 2. Chunk heavy world
        when(world.getEntities()).thenReturn(new ArrayList<>());
        when(world.getLoadedChunks()).thenReturn(new org.bukkit.Chunk[650]);
        VoltPurAutotune.Diagnostic diagChunks = VoltPurAutotune.diagnose(world);
        assertEquals("CHUNKS", diagChunks.type);

        // 3. Balanced / General load
        when(world.getLoadedChunks()).thenReturn(new org.bukkit.Chunk[50]);
        VoltPurAutotune.Diagnostic diagBalanced = VoltPurAutotune.diagnose(world);
        assertEquals("GENERAL", diagBalanced.type);
    }

    @Test
    void testAdvisorModeDoesNotMutateWorld() {
        VoltPurConfig.autotuneMode = "advisor";
        assertFalse(VoltPurConfig.isAutotuneActive());

        // Run 2 high MSPT cycles
        VoltPurAutotune.tick(50.0);
        VoltPurAutotune.tick(50.0);

        // In advisor mode, no world settings must be mutated
        verify(world, never()).setViewDistance(anyInt());
        verify(world, never()).setSimulationDistance(anyInt());
        verify(world, never()).setSpawnLimit(any(), anyInt());

        // History records ADVISORY
        List<VoltPurAutotune.DecisionRecord> history = VoltPurAutotune.getHistory();
        assertEquals(1, history.size());
        assertEquals("ADVISORY", history.get(0).outcome);
    }

    @Test
    void testSafeBoundsEnforced() {
        // Base view distance is 10, min allowed is 4
        when(world.getViewDistance()).thenReturn(4);
        when(world.getSimulationDistance()).thenReturn(4);

        // Trigger reductions
        VoltPurAutotune.tick(55.0);
        VoltPurAutotune.tick(55.0);

        // View distance and sim distance should not fall below 4
        verify(world, never()).setViewDistance(intThat(v -> v < 4));
        verify(world, never()).setSimulationDistance(intThat(v -> v < 4));
    }

    @Test
    void testVerificationRollbackWhenNoImprovement() {
        // Setup a decision record
        VoltPurAutotune.captureWorldBase(world);
        VoltPurAutotune.DecisionRecord rec = new VoltPurAutotune.DecisionRecord(
                48.0, "CHUNKS", "SIM_DISTANCE_DECREASE", "world_test", "SIM", 8, 7, "ACTIVE"
        );

        // Simulate 60s verification where MSPT stayed bad (50.0 ms)
        VoltPurAutotune.verifyOutcome(world, rec, 50.0);

        assertEquals("ROLLED_BACK", rec.outcome);
        assertEquals(50.0, rec.afterMspt);
        // Setting was restored back to 8
        verify(world).setSimulationDistance(8);
    }

    @Test
    void testVerificationConfirmedWhenMsptImproves() {
        // Setup a decision record
        VoltPurAutotune.captureWorldBase(world);
        VoltPurAutotune.DecisionRecord rec = new VoltPurAutotune.DecisionRecord(
                48.0, "CHUNKS", "SIM_DISTANCE_DECREASE", "world_test", "SIM", 8, 7, "ACTIVE"
        );

        // Simulate 60s verification where MSPT improved to 28.0 ms
        VoltPurAutotune.verifyOutcome(world, rec, 28.0);

        assertEquals("CONFIRMED", rec.outcome);
        assertEquals(28.0, rec.afterMspt);
        // World should NOT be rolled back
        verify(world, never()).setSimulationDistance(8);
    }

    @Test
    void testCircularHistoryCappedAt20() {
        VoltPurConfig.autotuneMode = "advisor";
        for (int i = 0; i < 30; i++) {
            VoltPurAutotune.tick(50.0);
            VoltPurAutotune.tick(50.0);
        }
        List<VoltPurAutotune.DecisionRecord> history = VoltPurAutotune.getHistory();
        assertTrue(history.size() <= 20, "History size must not exceed 20");
    }

    @Test
    void testManualRollbackRestoresBase() {
        VoltPurAutotune.captureWorldBase(world);

        // World values changed
        when(world.getViewDistance()).thenReturn(5);
        when(world.getSimulationDistance()).thenReturn(4);
        when(world.getSpawnLimit(SpawnCategory.MONSTER)).thenReturn(25);

        // Rollback
        VoltPurAutotune.rollback(null);

        // Verifies settings restored to 10, 8, 70
        verify(world).setViewDistance(10);
        verify(world).setSimulationDistance(8);
        verify(world).setSpawnLimit(SpawnCategory.MONSTER, 70);
    }
}
