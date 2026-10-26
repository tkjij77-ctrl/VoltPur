package org.purpurmc.purpur;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class VoltPurIntegrationsTest {

    private Path tempDir;
    private File originalPluginPro;
    private File originalPlugins;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("voltpur-integration-test");
        VoltPurIntegrations.resetForTesting();
        VoltPurModules.setStatus("MemoryOptimization", VoltPurModules.Status.PLANNED);
        VoltPurModules.setStatus("ChunkLoading", VoltPurModules.Status.PLANNED);
    }

    @AfterEach
    void tearDown() throws IOException {
        VoltPurConfig.pregenEnabled = false;
        VoltPurIntegrations.resetForTesting();
        if (Files.exists(tempDir)) {
            try (var stream = Files.walk(tempDir)) {
                stream.sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
            }
        }
    }

    @Test
    void testDetectIntegrationsWithoutPluginsLeavesPlanned() {
        VoltPurIntegrations.detectExternalOptimizations();
        // Without jars in plugin-pro or plugins, should remain PLANNED
        assertEquals(VoltPurModules.Status.PLANNED, VoltPurModules.all().get("MemoryOptimization"));
        assertEquals(VoltPurModules.Status.PLANNED, VoltPurModules.all().get("ChunkLoading"));
    }

    @Test
    void testDetectFerriteCoreMarksActive() throws IOException {
        File pluginPro = new File("plugin-pro");
        boolean created = pluginPro.mkdirs();
        File dummyJar = new File(pluginPro, "ferritecore-fabric-paper.jar");
        dummyJar.createNewFile();

        try {
            VoltPurIntegrations.detectExternalOptimizations();
            assertEquals(VoltPurModules.Status.ACTIVE, VoltPurModules.all().get("MemoryOptimization"),
                    "FerriteCore detection should update MemoryOptimization to ACTIVE");
        } finally {
            dummyJar.delete();
            if (created) pluginPro.delete();
        }
    }

    @Test
    void testDetectC2MEMarksActive() throws IOException {
        File pluginPro = new File("plugin-pro");
        boolean created = pluginPro.mkdirs();
        File dummyJar = new File(pluginPro, "c2me-paper-optimizer.jar");
        dummyJar.createNewFile();

        try {
            VoltPurIntegrations.detectExternalOptimizations();
            assertEquals(VoltPurModules.Status.ACTIVE, VoltPurModules.all().get("ChunkLoading"),
                    "C2ME detection should update ChunkLoading to ACTIVE");
        } finally {
            dummyJar.delete();
            if (created) pluginPro.delete();
        }
    }

    @Test
    void testPregenGuardConditions() {
        VoltPurConfig.pregenEnabled = false;
        VoltPurIntegrations.tickPregen();
        assertEquals(0, VoltPurIntegrations.getChunksGenerated(), "Pregen should not run when disabled");

        VoltPurConfig.pregenEnabled = true;
        VoltPurConfig.pregenMaxPlayers = 0;
        // In unit test environment, world may be null or offline, tickPregen handles gracefully without exception
        assertDoesNotThrow(VoltPurIntegrations::tickPregen);
    }
}
