package org.purpurmc.purpur.plus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Manifest representation and parser for voltpur-plus.json (§6.2, §6.4, §6.5).
 */
public final class PlusManifest {

    public static final String MANIFEST_FILE = "voltpur-plus.json";

    private final String id;
    private final String name;
    private final String version;
    private final String mcVersion;
    private final String entrypoint;
    private final String declaredSha256;
    private final String actualSha256;

    public PlusManifest(String id, String name, String version, String mcVersion,
                        String entrypoint, String declaredSha256, String actualSha256) {
        this.id = id;
        this.name = name != null && !name.isBlank() ? name : id;
        this.version = version != null && !version.isBlank() ? version : "1.0.0";
        this.mcVersion = mcVersion != null && !mcVersion.isBlank() ? mcVersion : "*";
        this.entrypoint = entrypoint;
        this.declaredSha256 = declaredSha256 != null ? declaredSha256.trim().toLowerCase() : "";
        this.actualSha256 = actualSha256 != null ? actualSha256.trim().toLowerCase() : "";
    }

    public static PlusManifest fromJar(File jarFile) throws Exception {
        String actualSha256 = computeSha256(jarFile);

        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry(MANIFEST_FILE);
            if (entry == null) {
                throw new IllegalArgumentException("Missing " + MANIFEST_FILE + " in jar: " + jarFile.getName());
            }

            try (InputStream in = jar.getInputStream(entry);
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                if (!json.has("id") || json.get("id").getAsString().isBlank()) {
                    throw new IllegalArgumentException("Manifest must define 'id' field in " + jarFile.getName());
                }
                if (!json.has("entrypoint") || json.get("entrypoint").getAsString().isBlank()) {
                    throw new IllegalArgumentException("Manifest must define 'entrypoint' field in " + jarFile.getName());
                }

                String id = json.get("id").getAsString().trim();
                String name = json.has("name") ? json.get("name").getAsString().trim() : id;
                String version = json.has("version") ? json.get("version").getAsString().trim() : "1.0.0";
                String mcVersion = json.has("mc-version") ? json.get("mc-version").getAsString().trim() : "*";
                String entrypoint = json.get("entrypoint").getAsString().trim();
                String declaredSha256 = json.has("sha256") ? json.get("sha256").getAsString().trim() : "";

                return new PlusManifest(id, name, version, mcVersion, entrypoint, declaredSha256, actualSha256);
            }
        }
    }

    public static String computeSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = java.nio.file.Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString().toLowerCase();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getMcVersion() {
        return mcVersion;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public String getDeclaredSha256() {
        return declaredSha256;
    }

    public String getActualSha256() {
        return actualSha256;
    }

    public boolean isVersionCompatible(String runningMcVersion) {
        if ("*".equals(mcVersion) || mcVersion.isBlank()) return true;
        if (mcVersion.equalsIgnoreCase(runningMcVersion)) return true;
        // Also support partial matches (e.g. "26.2" vs "26.2.0" or "1.21.10")
        if (runningMcVersion.startsWith(mcVersion) || mcVersion.startsWith(runningMcVersion)) return true;
        return false;
    }
}
