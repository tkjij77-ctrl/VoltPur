package org.purpurmc.purpur.plus;

import org.bukkit.Bukkit;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.VoltPurPlugin;
import org.purpurmc.purpur.util.VoltPurLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VoltPur Plus Bootstrap Engine (§6.2, §6.4, §6.5).
 *
 * Scans plugin-plus/ before game initialization, validates manifests and security hashes,
 * instantiates modules in isolated ClassLoaders, executes pre-launch lifecycle hooks,
 * and handles safe Bukkit API synchronization on server ready.
 */
public final class PlusBootstrap {

    public static final String PLUS_FOLDER_NAME = "plugin-plus";
    public static final String ALLOW_LIST_FILE = "plus.allow";

    private static final Map<String, LoadedModule> LOADED_MODULES = new ConcurrentHashMap<>();
    private static final Set<String> ALLOWED_HASHES = Collections.synchronizedSet(new HashSet<>());
    private static boolean bootstrapped = false;

    private PlusBootstrap() {}

    public static class LoadedModule {
        private final PlusManifest manifest;
        private final File jarFile;
        private final VoltPurPlusModule instance;
        private final PlusClassLoader classLoader;

        public LoadedModule(PlusManifest manifest, File jarFile, VoltPurPlusModule instance, PlusClassLoader cl) {
            this.manifest = manifest;
            this.jarFile = jarFile;
            this.instance = instance;
            this.classLoader = cl;
        }

        public PlusManifest getManifest() { return manifest; }
        public File getJarFile() { return jarFile; }
        public VoltPurPlusModule getInstance() { return instance; }
        public PlusClassLoader getClassLoader() { return classLoader; }
    }

    /**
     * Initializes the Plus system and scans/executes modules in plugin-plus/.
     */
    public static synchronized void init() {
        if (bootstrapped) return;
        bootstrapped = true;

        VoltPurLogger.info("Plus", "Initializing VoltPur Plus loader system...");
        File folder = ensureFolder();

        loadAllowList();

        File[] jarFiles = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length == 0) {
            VoltPurLogger.info("Plus", "plugin-plus/ active: no external .jar modules found.");
            VoltPurModules.setStatus("PlusLoader", VoltPurModules.Status.PARTIAL);
            return;
        }

        int loadedCount = 0;
        for (File jar : jarFiles) {
            if (loadModuleJar(jar)) {
                loadedCount++;
            }
        }

        if (loadedCount > 0) {
            VoltPurModules.setStatus("PlusLoader", VoltPurModules.Status.ACTIVE);
            VoltPurLogger.info("Plus", "Successfully loaded and initialized " + loadedCount + " Plus module(s).");
        } else {
            VoltPurModules.setStatus("PlusLoader", VoltPurModules.Status.PARTIAL);
        }

        // Register post-launch hook for server ready (§6.3)
        VoltPurPlugin.whenAvailable(() -> {
            for (LoadedModule module : LOADED_MODULES.values()) {
                try {
                    module.getInstance().onServerReady();
                } catch (Throwable t) {
                    VoltPurLogger.error("Plus-" + module.getManifest().getId(),
                            "Error in onServerReady() for module " + module.getManifest().getId(), t);
                }
            }
        });
    }

    private static boolean loadModuleJar(File jarFile) {
        PlusManifest manifest;
        try {
            manifest = PlusManifest.fromJar(jarFile);
        } catch (Exception e) {
            VoltPurLogger.warn("Plus", "Skipping invalid jar " + jarFile.getName(), e.getMessage());
            return false;
        }

        // 1. Security validation (§6.5 Rule 1)
        if (!isSecurityAllowed(manifest)) {
            VoltPurLogger.warn("Plus", "Security Check", String.format(
                    "Module '%s' (%s) rejected: SHA256 %s is not permitted in %s.",
                    manifest.getId(), jarFile.getName(), manifest.getActualSha256(), ALLOW_LIST_FILE));
            return false;
        }

        // 2. Version compatibility validation (§6.5 Rule 2)
        if (!manifest.isVersionCompatible(VoltPur.MC_VERSION)) {
            VoltPurLogger.warn("Plus", "Version Incompatible", String.format(
                    "Module '%s' requires Minecraft %s, but server is running %s. Skipped.",
                    manifest.getId(), manifest.getMcVersion(), VoltPur.MC_VERSION));
            return false;
        }

        // 3. Isolated classloading & crash isolation (§6.5 Rule 3)
        try {
            URL[] urls = new URL[]{jarFile.toURI().toURL()};
            PlusClassLoader classLoader = new PlusClassLoader(urls, PlusBootstrap.class.getClassLoader());

            Class<?> entryClass = Class.forName(manifest.getEntrypoint(), true, classLoader);
            if (!VoltPurPlusModule.class.isAssignableFrom(entryClass)) {
                VoltPurLogger.warn("Plus", "Invalid Entrypoint",
                        "Class " + manifest.getEntrypoint() + " does not implement VoltPurPlusModule in " + jarFile.getName());
                classLoader.close();
                return false;
            }

            VoltPurPlusModule instance = (VoltPurPlusModule) entryClass.getDeclaredConstructor().newInstance();
            PlusContext context = new PlusContext(manifest, jarFile);

            // Execute pre-launch
            instance.onPreLaunch(context);

            LOADED_MODULES.put(manifest.getId(), new LoadedModule(manifest, jarFile, instance, classLoader));
            VoltPurLogger.info("Plus", String.format("Loaded Plus Module: %s v%s [%s]",
                    manifest.getName(), manifest.getVersion(), manifest.getId()));
            return true;
        } catch (Throwable t) {
            // Crash isolation: broken module disables itself gracefully without crashing the server
            VoltPurLogger.error("Plus-" + manifest.getId(), "Module crashed during pre-launch and was safely disabled", t);
            return false;
        }
    }

    private static boolean isSecurityAllowed(PlusManifest manifest) {
        // If declared sha256 doesn't match actual hash, always reject
        if (!manifest.getDeclaredSha256().isEmpty() &&
            !manifest.getDeclaredSha256().equalsIgnoreCase(manifest.getActualSha256())) {
            return false;
        }

        // If allow list is configured, hash must be present
        if (!ALLOWED_HASHES.isEmpty()) {
            return ALLOWED_HASHES.contains(manifest.getActualSha256().toLowerCase());
        }

        // Default open in development/local unless plus.allow is present
        return true;
    }

    private static void loadAllowList() {
        ALLOWED_HASHES.clear();
        File allowFile = new File(PLUS_FOLDER_NAME, ALLOW_LIST_FILE);
        if (allowFile.exists()) {
            try {
                for (String line : Files.readAllLines(allowFile.toPath(), StandardCharsets.UTF_8)) {
                    String trimmed = line.trim().toLowerCase();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        // Accept SHA-256 hashes (64 hex characters)
                        int commentIndex = trimmed.indexOf('#');
                        if (commentIndex > 0) trimmed = trimmed.substring(0, commentIndex).trim();
                        if (trimmed.length() == 64) {
                            ALLOWED_HASHES.add(trimmed);
                        }
                    }
                }
                VoltPurLogger.info("Plus", "Loaded " + ALLOWED_HASHES.size() + " allowed SHA-256 hash(es) from " + ALLOW_LIST_FILE);
            } catch (IOException e) {
                VoltPurLogger.warn("Plus", "Failed reading " + ALLOW_LIST_FILE, e);
            }
        }
    }

    public static File ensureFolder() {
        File folder = new File(PLUS_FOLDER_NAME);
        if (!folder.exists()) {
            folder.mkdirs();
            File readme = new File(folder, "README.txt");
            if (!readme.exists()) {
                try (FileWriter fw = new FileWriter(readme)) {
                    fw.write("⚡ VoltPur plugin-plus/ — Low-Level Game Modules (§6)\n");
                    fw.write("====================================================\n\n");
                    fw.write("Modules in this folder execute before the game engine initializes.\n");
                    fw.write("Each module must be a .jar file containing 'voltpur-plus.json'.\n\n");
                    fw.write("Security Policy:\n");
                    fw.write("To enforce a strict allowlist, create 'plus.allow' in this folder\n");
                    fw.write("and list approved SHA-256 hashes (one per line).\n");
                } catch (IOException ignored) {}
            }
        }
        return folder;
    }

    public static Map<String, LoadedModule> getLoadedModules() {
        return Collections.unmodifiableMap(LOADED_MODULES);
    }

    public static LoadedModule getLoadedModule(String id) {
        return LOADED_MODULES.get(id);
    }

    public static synchronized void unloadAll() {
        for (LoadedModule m : LOADED_MODULES.values()) {
            try {
                m.getInstance().onServerShutdown();
            } catch (Throwable t) {
                VoltPurLogger.warn("VoltPur-Plus", "shutdown-" + m.getManifest().getId(), t);
            }
            try {
                m.getClassLoader().close();
            } catch (Throwable ignored) {}
        }
        LOADED_MODULES.clear();
        ALLOWED_HASHES.clear();
        bootstrapped = false;
    }

    public static void resetForTesting() {
        unloadAll();
    }
}
