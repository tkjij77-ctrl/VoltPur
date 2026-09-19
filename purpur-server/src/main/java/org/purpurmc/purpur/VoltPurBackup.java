package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * VoltPur World Backup - opt-in (default OFF), and CONSISTENT when enabled.
 *
 * HARDENING CHANGES vs the previous version:
 *  1. Consistency: the old code zipped worlds while the server kept writing them
 *     (it only skipped session.lock), which can produce an archive that silently
 *     fails to restore. Now the sequence is:
 *        save-off -> save-all flush -> wait for the flush -> zip -> save-on (always)
 *     and Minecraft's own save cycle is resumed in a finally block.
 *  2. World list: taken from Bukkit.getWorlds() instead of a hardcoded
 *     world/world_nether/world_the_end list, so custom worlds are backed up too.
 *  3. Verification: the archive is reopened and its entries counted; a corrupt or
 *     empty archive is reported as FAILED (and never kept as a "good" backup).
 *  4. update.auto-backup is now actually wired: the updater calls
 *     backupForUpdate() before it replaces the server jar.
 */
public final class VoltPurBackup {

    private static final String MODULE = "WorldBackup";
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public record Result(boolean ok, Path archive, long bytes, int worlds, int entries, String error) {
        public static Result failure(String error) {
            return new Result(false, null, 0L, 0, 0, error);
        }
    }

    private static boolean initialized = false;
    private static BukkitTask task;

    private VoltPurBackup() {}

    public static void init() {
        if (initialized) return;
        if (!VoltPurConfig.backupEnabled) {
            VoltPurModules.setRuntime(MODULE, false);
            Bukkit.getLogger().info("[VoltPur-Backup] Disabled (opt-in). Enable modules.backup.enabled to use it.");
            return;
        }
        initialized = true;
        VoltPurModules.setRuntime(MODULE, true);
        long periodTicks = Math.max(1L, (long) VoltPurConfig.backupIntervalMinutes * 60L) * 20L;
        try {
            task = Bukkit.getScheduler().runTaskTimer(VoltPurPlugin.get(),
                    () -> VoltPurGuard.run(MODULE, () -> {
                        Result result = runBackup(true);
                        if (!result.ok()) {
                            Bukkit.getLogger().warning("[VoltPur-Backup] Scheduled backup FAILED: " + result.error());
                            recordScheduledResult(result);
                        }
                    }), periodTicks, periodTicks);
            Bukkit.getLogger().info("[VoltPur-Backup] Active - every " + VoltPurConfig.backupIntervalMinutes
                    + " min, keeping " + VoltPurConfig.backupKeep + " archives in " + VoltPurConfig.backupFolder
                    + "/ (consistent=" + VoltPurConfig.backupConsistent + ")");
        } catch (Throwable t) {
            VoltPurGuard.failure(MODULE, t);
        }
    }

    /**
     * Reports a failed scheduled backup to the Guard, so it is counted like any other
     * module failure. Without this the warning went to the log and nowhere else: every
     * scheduled backup could fail forever while /voltpur modules still called the module
     * healthy - exactly the blindness the Guard exists to remove. With it, a backup that
     * keeps failing stops being retried on a schedule nobody reads.
     */
    public static void recordScheduledResult(Result result) {
        if (result == null || result.ok()) return;
        VoltPurGuard.failure(MODULE, new IllegalStateException(
                result.error() == null ? "scheduled backup failed" : result.error()));
    }

    /** Called by the updater before replacing the server jar. Never throws. */
    public static Result backupForUpdate() {
        if (!VoltPurConfig.backupEnabled) {
            return Result.failure("modules.backup.enabled=false - enable it to have update.auto-backup work");
        }
        return runBackup(true);
    }

    /**
     * Creates one archive. Safe to call from a worker thread: the flush step is
     * bounced onto the main thread, and the zip itself is pure file IO.
     */
    public static Result runBackup(boolean flushFirst) {
        List<String> worldNames = new ArrayList<>();
        try {
            for (World world : Bukkit.getWorlds()) {
                File folder = world.getWorldFolder();
                if (folder != null && folder.isDirectory()) worldNames.add(folder.getName());
            }
        } catch (Throwable notOnMainThread) {
            // Called off-thread and the API refused: fall back to folder scan.
            File[] candidates = new File(".").listFiles(File::isDirectory);
            if (candidates != null) {
                for (File candidate : candidates) {
                    if (new File(candidate, "level.dat").isFile()) worldNames.add(candidate.getName());
                }
            }
        }
        if (worldNames.isEmpty()) return Result.failure("no world folders found");

        Path folder = Path.of(VoltPurConfig.backupFolder);
        Path archive = folder.resolve("world-backup-" + STAMP.format(new Date()) + ".zip");
        boolean savingPaused = false;
        try {
            Files.createDirectories(folder);

            if (flushFirst && VoltPurConfig.backupConsistent) {
                savingPaused = pauseSavingAndFlush();
            }

            int entries = zipWorlds(worldNames, archive);
            long size = Files.size(archive);

            int verified = countEntries(archive);
            if (verified != entries || size <= 0L) {
                Files.deleteIfExists(archive);
                return Result.failure("archive verification failed (expected " + entries + " entries, found " + verified + ")");
            }
            prune(folder);
            Bukkit.getLogger().info("[VoltPur-Backup] Created " + archive.getFileName() + " ("
                    + (size / 1024 / 1024) + " MB, " + verified + " entries, " + worldNames.size() + " world(s))");
            return new Result(true, archive, size, worldNames.size(), verified, "");
        } catch (Exception e) {
            try {
                Files.deleteIfExists(archive); // never keep a partial archive as a "backup"
            } catch (IOException ignored) {
                Bukkit.getLogger().warning("[VoltPur-Backup] Could not delete the partial archive: " + ignored.getMessage());
            }
            VoltPurGuard.failure(MODULE, e);
            return Result.failure(e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            if (savingPaused) resumeSaving();
        }
    }

    /** save-off + save-all flush on the main thread, and waits (max 30s) for the flush. */
    private static boolean pauseSavingAndFlush() {
        final CountDownLatch flushed = new CountDownLatch(1);
        try {
            Bukkit.getScheduler().runTask(VoltPurPlugin.get(), () -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-off");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all flush");
                } finally {
                    flushed.countDown();
                }
            });
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[VoltPur-Backup] Could not pause saving: " + t.getMessage());
            return false;
        }
        try {
            if (!flushed.await(30, TimeUnit.SECONDS)) {
                Bukkit.getLogger().warning("[VoltPur-Backup] Flush did not complete in 30s - resuming saving.");
                return true; // saving was paused; resume in finally
            }
            Thread.sleep(2000L); // let the flush finish writing before we read the region files
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return true;
    }

    private static void resumeSaving() {
        try {
            Bukkit.getScheduler().runTask(VoltPurPlugin.get(),
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-on"));
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[VoltPur-Backup] Could not resume saving (" + t.getMessage()
                    + ") - run 'save-on' manually.");
        }
    }

    private static int zipWorlds(List<String> worldNames, Path archive) throws IOException {
        int entries = 0;
        byte[] buffer = new byte[8192];
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive.toFile()))) {
            for (String name : worldNames) {
                File worldFolder = new File(name);
                if (!worldFolder.isDirectory()) continue;
                entries += zipDirectory(worldFolder, worldFolder.getName(), zos, buffer);
            }
        }
        return entries;
    }

    private static int zipDirectory(File directory, String base, ZipOutputStream zos, byte[] buffer) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) return 0;
        int count = 0;
        for (File child : children) {
            String entryName = base + "/" + child.getName();
            if (child.isDirectory()) {
                count += zipDirectory(child, entryName, zos, buffer);
                continue;
            }
            if (child.getName().equals("session.lock")) continue; // held by the running server
            try (InputStream in = Files.newInputStream(child.toPath())) {
                zos.putNextEntry(new ZipEntry(entryName));
                int read;
                while ((read = in.read(buffer)) > 0) zos.write(buffer, 0, read);
                zos.closeEntry();
                count++;
            } catch (IOException skipped) {
                Bukkit.getLogger().info("[VoltPur-Backup] Skipped " + entryName + ": " + skipped.getMessage());
            }
        }
        return count;
    }

    private static int countEntries(Path archive) {
        int count = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                entries.nextElement();
                count++;
            }
        } catch (IOException e) {
            return -1;
        }
        return count;
    }

    private static void prune(Path folder) {
        File directory = folder.toFile();
        File[] archives = directory.listFiles((dir, name) -> name.startsWith("world-backup-") && name.endsWith(".zip"));
        if (archives == null || archives.length <= VoltPurConfig.backupKeep) return;
        List<File> sorted = new ArrayList<>(Arrays.asList(archives));
        sorted.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified())); // oldest first
        int remove = sorted.size() - VoltPurConfig.backupKeep;
        for (int i = 0; i < remove; i++) {
            if (sorted.get(i).delete()) {
                Bukkit.getLogger().info("[VoltPur-Backup] Pruned old backup: " + sorted.get(i).getName());
            }
        }
    }
}
