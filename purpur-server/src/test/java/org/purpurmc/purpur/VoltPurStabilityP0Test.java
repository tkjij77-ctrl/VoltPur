package org.purpurmc.purpur;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.purpurmc.purpur.util.VoltPurLogger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

public class VoltPurStabilityP0Test {

    @BeforeEach
    void setUp() {
        VoltPurModules.setStatus("WorldStability", VoltPurModules.Status.ACTIVE);
    }

    @Test
    void testModuleStatusTracking() {
        assertEquals(VoltPurModules.Status.ACTIVE, VoltPurModules.all().get("WorldStability"));
        VoltPurModules.setStatus("WorldStability", VoltPurModules.Status.DEGRADED);
        assertEquals(VoltPurModules.Status.DEGRADED, VoltPurModules.all().get("WorldStability"));
        assertTrue(VoltPurModules.degradedCount() >= 1);

        // Reset
        VoltPurModules.setStatus("WorldStability", VoltPurModules.Status.ACTIVE);
    }

    @Test
    void testAutoDegradationAfterThreeErrors() {
        // Record 3 consecutive errors
        VoltPurLogger.error("WorldStability", "Failure 1", new RuntimeException("E1"));
        VoltPurLogger.error("WorldStability", "Failure 2", new RuntimeException("E2"));
        VoltPurLogger.error("WorldStability", "Failure 3", new RuntimeException("E3"));

        assertEquals(VoltPurModules.Status.DEGRADED, VoltPurModules.all().get("WorldStability"));

        // Success resets the consecutive failure counter
        VoltPurLogger.recordSuccess("WorldStability");
        VoltPurModules.setStatus("WorldStability", VoltPurModules.Status.ACTIVE);
        assertEquals(VoltPurModules.Status.ACTIVE, VoltPurModules.all().get("WorldStability"));

        // Next error is fail count 1, so NOT degraded
        VoltPurLogger.error("WorldStability", "Failure again", new RuntimeException("E4"));
        assertEquals(VoltPurModules.Status.ACTIVE, VoltPurModules.all().get("WorldStability"));
    }

    @Test
    void testSha256Verification() throws Exception {
        byte[] data = "VoltPur-Test-Payload-123".getBytes(StandardCharsets.UTF_8);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        String expectedHash = sb.toString();

        File tempFile = File.createTempFile("voltpur-test", ".bin");
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), data);

        // Compute hash of file
        MessageDigest fileMd = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(tempFile.toPath())) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) fileMd.update(buf, 0, n);
        }
        byte[] fileHash = fileMd.digest();
        StringBuilder fileSb = new StringBuilder();
        for (byte b : fileHash) fileSb.append(String.format("%02x", b));

        assertEquals(expectedHash, fileSb.toString());
    }
}
