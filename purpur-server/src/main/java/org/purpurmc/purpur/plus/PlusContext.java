package org.purpurmc.purpur.plus;

import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.util.VoltPurLogger;

import java.io.File;

/**
 * Context provided to VoltPur Plus Modules during lifecycle events (§6.3).
 */
public final class PlusContext {

    private final PlusManifest manifest;
    private final File jarFile;

    public PlusContext(PlusManifest manifest, File jarFile) {
        this.manifest = manifest;
        this.jarFile = jarFile;
    }

    public String getId() {
        return manifest.getId();
    }

    public String getName() {
        return manifest.getName();
    }

    public String getVersion() {
        return manifest.getVersion();
    }

    public String getTargetMcVersion() {
        return manifest.getMcVersion();
    }

    public File getJarFile() {
        return jarFile;
    }

    public String getRunningMcVersion() {
        return VoltPur.MC_VERSION;
    }

    public String getVoltPurVersion() {
        return VoltPur.VERSION;
    }

    public void info(String message) {
        VoltPurLogger.info("Plus-" + manifest.getId(), message);
    }

    public void warn(String message) {
        VoltPurLogger.warn("Plus-" + manifest.getId(), "Warning", message);
    }

    public void error(String message, Throwable throwable) {
        VoltPurLogger.error("Plus-" + manifest.getId(), message, throwable);
    }
}
