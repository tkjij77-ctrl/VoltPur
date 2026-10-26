package org.purpurmc.purpur.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AtomicConfigWriterTest {

    private Path testDir;

    @BeforeEach
    void setUp() throws IOException {
        testDir = Files.createTempDirectory("voltpur-atomic-test");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testDir)) {
            try (var stream = Files.walk(testDir)) {
                stream.sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
            }
        }
    }

    @Test
    void testWritePropertiesPreservesCommentsAndFormatting() throws IOException {
        Path propFile = testDir.resolve("server.properties");
        List<String> originalLines = List.of(
                "# Minecraft server properties",
                "# Edited by Admin",
                "",
                "view-distance=10",
                "# Simulation distance is crucial",
                "simulation-distance=8",
                "motd=A Minecraft Server"
        );
        Files.write(propFile, originalLines, StandardCharsets.UTF_8);

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("view-distance", "6");
        updates.put("simulation-distance", "5");
        updates.put("max-players", "100"); // new key

        boolean success = AtomicConfigWriter.writeProperties(propFile, updates);
        assertTrue(success);

        // Verify .bak file exists
        Path bakFile = testDir.resolve("server.properties.bak");
        assertTrue(Files.exists(bakFile), "Backup file server.properties.bak must exist");

        // Verify no leftover .tmp files
        File[] tmpFiles = testDir.toFile().listFiles((dir, name) -> name.contains(".tmp."));
        assertNotNull(tmpFiles);
        assertEquals(0, tmpFiles.length, "Temporary files must be cleaned up");

        // Read modified lines
        List<String> modifiedLines = Files.readAllLines(propFile, StandardCharsets.UTF_8);

        // Assert comments were NOT erased
        assertTrue(modifiedLines.contains("# Minecraft server properties"));
        assertTrue(modifiedLines.contains("# Edited by Admin"));
        assertTrue(modifiedLines.contains("# Simulation distance is crucial"));

        // Assert values updated
        assertTrue(modifiedLines.contains("view-distance=6"));
        assertTrue(modifiedLines.contains("simulation-distance=5"));
        assertTrue(modifiedLines.contains("motd=A Minecraft Server"));
        assertTrue(modifiedLines.contains("max-players=100"));
    }

    @Test
    void testWriteYamlPreservesCommentsAndIndentation() throws IOException {
        Path yamlFile = testDir.resolve("spigot.yml");
        List<String> originalLines = List.of(
                "# This is the main configuration file for Spigot.",
                "# Modify settings below with care.",
                "",
                "settings:",
                "  debug: false",
                "world-settings:",
                "  default:",
                "    verbose: false # inline comment",
                "    merge-radius:",
                "      item: 2.5",
                "      exp: 3.0",
                "    ticks-per:",
                "      hopper-check: 1",
                "      hopper-transfer: 8"
        );
        Files.write(yamlFile, originalLines, StandardCharsets.UTF_8);

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("world-settings.default.merge-radius.item", "4.0");
        updates.put("world-settings.default.merge-radius.exp", "6.0");
        updates.put("world-settings.default.ticks-per.hopper-check", "8");

        boolean success = AtomicConfigWriter.writeYaml(yamlFile, updates);
        assertTrue(success);

        // Verify .bak file exists
        Path bakFile = testDir.resolve("spigot.yml.bak");
        assertTrue(Files.exists(bakFile), "Backup file spigot.yml.bak must exist");

        List<String> modifiedLines = Files.readAllLines(yamlFile, StandardCharsets.UTF_8);

        // Verify comments preserved
        assertTrue(modifiedLines.contains("# This is the main configuration file for Spigot."));
        assertTrue(modifiedLines.contains("# Modify settings below with care."));

        // Verify values updated and indented properly
        boolean foundItem = false;
        boolean foundExp = false;
        boolean foundHopperCheck = false;

        for (String l : modifiedLines) {
            if (l.trim().equals("item: 4.0")) {
                assertEquals("      item: 4.0", l, "Indentation must match exactly 6 spaces");
                foundItem = true;
            }
            if (l.trim().equals("exp: 6.0")) {
                assertEquals("      exp: 6.0", l, "Indentation must match exactly 6 spaces");
                foundExp = true;
            }
            if (l.trim().equals("hopper-check: 8")) {
                assertEquals("      hopper-check: 8", l, "Indentation must match exactly 6 spaces");
                foundHopperCheck = true;
            }
        }

        assertTrue(foundItem, "item: 4.0 must be present");
        assertTrue(foundExp, "exp: 6.0 must be present");
        assertTrue(foundHopperCheck, "hopper-check: 8 must be present");
    }

    @Test
    void testWriteYamlInsertsMissingSections() throws IOException {
        Path yamlFile = testDir.resolve("minimal.yml");
        List<String> originalLines = List.of(
                "# Minimal configuration",
                "world-settings:",
                "  default:",
                "    verbose: false"
        );
        Files.write(yamlFile, originalLines, StandardCharsets.UTF_8);

        Map<String, String> updates = new LinkedHashMap<>();
        updates.put("world-settings.default.merge-radius.item", "4.0");

        boolean success = AtomicConfigWriter.writeYaml(yamlFile, updates);
        assertTrue(success);

        List<String> modifiedLines = Files.readAllLines(yamlFile, StandardCharsets.UTF_8);
        assertTrue(modifiedLines.contains("# Minimal configuration"));

        boolean hasItem = modifiedLines.stream().anyMatch(l -> l.contains("item: 4.0"));
        assertTrue(hasItem, "Missing item: 4.0 should have been inserted under merge-radius");
    }
}
