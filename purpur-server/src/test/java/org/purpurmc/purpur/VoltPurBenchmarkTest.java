package org.purpurmc.purpur;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
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

public class VoltPurBenchmarkTest {

    private Server server;
    private World world;
    private final Path benchmarksDir = Path.of("logs", "voltpur-benchmarks");

    @BeforeEach
    void setUp() {
        VoltPurConfig.autotuneMode = "active";
        server = mock(Server.class);
        world = mock(World.class);

        when(server.getLogger()).thenReturn(Logger.getLogger("VoltPurBenchTest"));
        when(server.getTPS()).thenReturn(new double[]{19.95, 19.80, 19.50});
        when(server.getTickTimes()).thenReturn(new long[]{25_000_000L});
        when(server.getWorlds()).thenReturn(List.of(world));

        when(world.getName()).thenReturn("world");
        when(world.getEntities()).thenReturn(new ArrayList<>());
        when(world.getLoadedChunks()).thenReturn(new org.bukkit.Chunk[0]);

        VoltPurTestHelper.setMockServer(server);
    }

    @AfterEach
    void tearDown() {
        VoltPurTestHelper.clearMockServer();
        try {
            if (Files.exists(benchmarksDir)) {
                try (var stream = Files.walk(benchmarksDir)) {
                    stream.sorted((a, b) -> b.compareTo(a))
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                            });
                }
            }
        } catch (Exception ignored) {}
    }

    @Test
    void testBenchmarkGeneratesValidJson() throws Exception {
        VoltPurBenchmark.record("unit-test");

        assertTrue(Files.exists(benchmarksDir), "Benchmarks directory should exist");
        File[] jsonFiles = benchmarksDir.toFile().listFiles((dir, name) -> name.startsWith("benchmark-") && name.endsWith(".json"));
        assertNotNull(jsonFiles);
        assertTrue(jsonFiles.length >= 1, "At least one JSON benchmark file must be generated");

        File latestJson = jsonFiles[0];
        String content = Files.readString(latestJson.toPath());
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();

        assertEquals("unit-test", json.get("tag").getAsString());
        assertEquals("active", json.get("autotuneMode").getAsString());
        assertTrue(json.has("timestamp"));
        assertTrue(json.has("epochMillis"));
        assertTrue(json.has("mspt"));
        assertEquals(25.0, json.get("mspt").getAsDouble(), 0.01);

        assertTrue(json.has("tps"));
        JsonArray tpsArr = json.getAsJsonArray("tps");
        assertEquals(3, tpsArr.size());
        assertEquals(19.95, tpsArr.get(0).getAsDouble(), 0.01);

        assertTrue(json.has("totalEntities"));
        assertTrue(json.has("totalChunks"));
        assertTrue(json.has("heapUsedMB"));
        assertTrue(json.has("heapMaxMB"));
        assertTrue(json.has("worlds"));
    }
}
