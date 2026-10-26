package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * VoltPur World Backup - opt-in (default OFF).
 *
 * Periodically zips the world folders into modules.backup.folder and keeps only
 * the newest N archives. Runs on a daemon thread (pure file IO), so it works
 * without any plugin. Configure in voltpur.yml under modules.backup.*.
 */
public final class VoltPurBackup {
    private static boolean initialized = false;

    private VoltPurBackup() {}

    public static void init() {
        if (initialized) return;
        if (!VoltPurConfig.backupEnabled) return;
        initialized = true;
        final long intervalMs = VoltPurConfig.backupIntervalMinutes * 60_000L;
        Bukkit.getLogger().info("[VoltPur-Backup] Active - every " + VoltPurConfig.backupIntervalMinutes
                + " min, keeping " + VoltPurConfig.backupKeep + " archives in " + VoltPurConfig.backupFolder + "/");
        Thread t = new Thread(() -> {
            while (true) {
                try { Thread.sleep(intervalMs); } catch (InterruptedException e) { return; }
                try { runBackup(); } catch (Throwable e) {
                    Bukkit.getLogger().warning("[VoltPur-Backup] Backup failed: " + e.getMessage());
                }
            }
        }, "VoltPur-Backup");
        t.setDaemon(true);
        t.start();
    }

    /** Zips the world folders once. Returns the archive file, or null if nothing was backed up. */
    public static File runBackup() throws IOException {
        File dir = new File(VoltPurConfig.backupFolder);
        if (!dir.exists()) dir.mkdirs();
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
        File archive = new File(dir, "world-backup-" + stamp + ".zip");
        String[] worlds = {"world", "world_nether", "world_the_end"};
        int added = 0;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive))) {
            for (String w : worlds) {
                File wf = new File(w);
                if (wf.isDirectory()) { zipDir(wf, wf.getName(), zos); added++; }
            }
        }
        if (added == 0) {
            archive.delete();
            Bukkit.getLogger().info("[VoltPur-Backup] No world folders found - skipped.");
            return null;
        }
        Bukkit.getLogger().info("[VoltPur-Backup] Created " + archive.getName() + " (" + (archive.length() / 1024 / 1024) + "MB)");
        prune(dir);
        return archive;
    }

    private static void zipDir(File dir, String base, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        byte[] buf = new byte[8192];
        for (File f : files) {
            String entry = base + "/" + f.getName();
            if (f.isDirectory()) { zipDir(f, entry, zos); continue; }
            if (f.getName().equals("session.lock")) continue; // held by the server
            try (InputStream in = Files.newInputStream(f.toPath())) {
                zos.putNextEntry(new ZipEntry(entry));
                int r;
                while ((r = in.read(buf)) > 0) zos.write(buf, 0, r);
                zos.closeEntry();
            } catch (IOException ignored) { /* file changed mid-backup - skip it */ }
        }
    }

    private static void prune(File dir) {
        File[] archives = dir.listFiles((d, name) -> name.startsWith("world-backup-") && name.endsWith(".zip"));
        if (archives == null || archives.length <= VoltPurConfig.backupKeep) return;
        List<File> list = new ArrayList<>(Arrays.asList(archives));
        list.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified())); // oldest first
        int toRemove = list.size() - VoltPurConfig.backupKeep;
        for (int i = 0; i < toRemove; i++) {
            if (list.get(i).delete()) Bukkit.getLogger().info("[VoltPur-Backup] Pruned old backup: " + list.get(i).getName());
        }
    }
}
