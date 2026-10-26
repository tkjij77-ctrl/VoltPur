package org.purpurmc.purpur.plus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.plus.modules.HopperPlusModule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class VoltPurPlusTest {

    private Path tempWorkDir;
    private File plusFolder;

    @BeforeEach
    void setUp() throws IOException {
        tempWorkDir = Files.createTempDirectory("voltpur-plus-test");
        plusFolder = new File(PlusBootstrap.PLUS_FOLDER_NAME);
        plusFolder.mkdirs();
        PlusBootstrap.resetForTesting();
    }

    @AfterEach
    void tearDown() throws IOException {
        PlusBootstrap.resetForTesting();
        if (plusFolder.exists()) {
            File[] files = plusFolder.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            plusFolder.delete();
        }
        if (Files.exists(tempWorkDir)) {
            try (var stream = Files.walk(tempWorkDir)) {
                stream.sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                        });
            }
        }
    }

    private File createTestJar(String fileName, String manifestJson, Class<?>... classesToInclude) throws IOException {
        File jarFile = new File(plusFolder, fileName);
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            // Write manifest entry
            jos.putNextEntry(new JarEntry(PlusManifest.MANIFEST_FILE));
            jos.write(manifestJson.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();

            // Include classes
            for (Class<?> clazz : classesToInclude) {
                String classPath = clazz.getName().replace('.', '/') + ".class";
                try (InputStream is = clazz.getClassLoader().getResourceAsStream(classPath)) {
                    if (is != null) {
                        jos.putNextEntry(new JarEntry(classPath));
                        jos.write(is.readAllBytes());
                        jos.closeEntry();
                    }
                }
            }
        }
        return jarFile;
    }

    @Test
    void testManifestParsingValid() throws Exception {
        String manifest = "{\n" +
                "  \"id\": \"test-module\",\n" +
                "  \"name\": \"Test Plus Module\",\n" +
                "  \"version\": \"1.2.0\",\n" +
                "  \"mc-version\": \"1.21.10\",\n" +
                "  \"entrypoint\": \"org.purpurmc.purpur.plus.modules.HopperPlusModule\"\n" +
                "}";

        File jar = createTestJar("test-module.jar", manifest, HopperPlusModule.class);
        PlusManifest parsed = PlusManifest.fromJar(jar);

        assertEquals("test-module", parsed.getId());
        assertEquals("Test Plus Module", parsed.getName());
        assertEquals("1.2.0", parsed.getVersion());
        assertEquals("1.21.10", parsed.getMcVersion());
        assertEquals("org.purpurmc.purpur.plus.modules.HopperPlusModule", parsed.getEntrypoint());
        assertNotNull(parsed.getActualSha256());
        assertEquals(64, parsed.getActualSha256().length());
        assertTrue(parsed.isVersionCompatible("1.21.10"));
        assertFalse(parsed.isVersionCompatible("1.20.4"));
    }

    @Test
    void testManifestMissingIdThrows() throws IOException {
        String manifest = "{\n" +
                "  \"entrypoint\": \"org.purpurmc.purpur.plus.modules.HopperPlusModule\"\n" +
                "}";

        File jar = createTestJar("invalid-module.jar", manifest);
        assertThrows(IllegalArgumentException.class, () -> PlusManifest.fromJar(jar));
    }

    @Test
    void testPlusBootstrapLoadsAndExecutesHopperModule() throws IOException {
        String manifest = "{\n" +
                "  \"id\": \"hopper-plus\",\n" +
                "  \"name\": \"Hopper Plus Optimization\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"mc-version\": \"*\",\n" +
                "  \"entrypoint\": \"org.purpurmc.purpur.plus.modules.HopperPlusModule\"\n" +
                "}";

        File jar = createTestJar("hopper-plus.jar", manifest, HopperPlusModule.class);

        PlusBootstrap.init();

        assertEquals(1, PlusBootstrap.getLoadedModules().size());
        PlusBootstrap.LoadedModule loaded = PlusBootstrap.getLoadedModule("hopper-plus");
        assertNotNull(loaded);
        assertEquals("Hopper Plus Optimization", loaded.getManifest().getName());

        VoltPurPlusModule instance = loaded.getInstance();
        assertNotNull(instance, "Module instance must not be null");

        assertEquals(VoltPurModules.Status.ACTIVE, VoltPurModules.all().get("PlusLoader"));
    }

    @Test
    void testPlusBootstrapSecurityCheckRejectsUnapprovedHash() throws Exception {
        String manifest = "{\n" +
                "  \"id\": \"unapproved-module\",\n" +
                "  \"entrypoint\": \"org.purpurmc.purpur.plus.modules.HopperPlusModule\"\n" +
                "}";

        File jar = createTestJar("unapproved.jar", manifest, HopperPlusModule.class);

        // Write plus.allow with a different dummy hash
        File allowFile = new File(plusFolder, PlusBootstrap.ALLOW_LIST_FILE);
        String dummyHash = "0000000000000000000000000000000000000000000000000000000000000000\n";
        Files.writeString(allowFile.toPath(), dummyHash);

        PlusBootstrap.init();

        // Must be rejected because hash is not in plus.allow
        assertNull(PlusBootstrap.getLoadedModule("unapproved-module"));
        assertEquals(0, PlusBootstrap.getLoadedModules().size());
    }

    @Test
    void testPlusBootstrapCrashIsolationDoesNotHaltSystem() throws IOException {
        // Module entrypoint points to a class that is not a valid VoltPurPlusModule
        String manifest = "{\n" +
                "  \"id\": \"failing-module\",\n" +
                "  \"entrypoint\": \"java.lang.String\"\n" +
                "}";

        createTestJar("failing.jar", manifest);

        assertDoesNotThrow(() -> PlusBootstrap.init(),
                "PlusBootstrap must isolate errors and not crash server bootstrap");
        assertNull(PlusBootstrap.getLoadedModule("failing-module"));
    }
}
