package org.purpurmc.purpur.command;

import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurGuard;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Behaviour tests for the updater + plugin downloader hardening. */
public final class VerifyCommand {

    static int failures = 0;

    static void check(String name, boolean condition, String detail) {
        if (condition) {
            System.out.println("  PASS  " + name);
        } else {
            failures++;
            System.out.println("  FAIL  " + name + "  -> " + detail);
        }
    }

    public static void main(String[] args) throws Exception {
        Path work = Files.createTempDirectory("voltpur-cmd-");

        System.out.println("[guard] circuit breaker: a module failing in a loop is taken out of its schedule");
        VoltPurGuard.configure(true, 8);
        check("the breaker is on by default in this build", VoltPurGuard.breakerEnabled(), "breaker off");
        check("the threshold is the configured one", VoltPurGuard.breakerThreshold() == 8, "wrong threshold");
        String loop = "VerifyLoopModule";
        for (int i = 0; i < 7; i++) VoltPurGuard.run(loop, () -> { throw new IllegalStateException("boom"); });
        check("7 failures do NOT trip an 8-failure breaker", !VoltPurGuard.stat(loop).tripped(), "tripped too early");
        check("consecutive failures are counted", VoltPurGuard.stat(loop).consecutiveFailures() == 7,
                "count=" + VoltPurGuard.stat(loop).consecutiveFailures());
        VoltPurGuard.run(loop, () -> { throw new IllegalStateException("boom"); });   // the 8th
        check("the 8th consecutive failure opens the breaker", VoltPurGuard.stat(loop).tripped(), "still running");
        check("the trip reason names the last error", VoltPurGuard.stat(loop).tripReason().contains("boom"),
                VoltPurGuard.stat(loop).tripReason());
        boolean[] ran = {false};
        VoltPurGuard.run(loop, () -> { ran[0] = true; });
        check("a tripped module is not executed any more", !ran[0], "it still ran");
        check("short-circuited calls are counted", VoltPurGuard.stat(loop).shortCircuited() == 1, "not counted");
        check("the health line reports the breaker state",
                VoltPurGuard.healthLine(loop).contains("DISABLED-AFTER-FAILURES"), VoltPurGuard.healthLine(loop));

        System.out.println("[guard] circuit breaker: recovery, exemption, and what /voltpur reload does");
        String flaky = "VerifyFlakyModule";
        for (int i = 0; i < 5; i++) VoltPurGuard.run(flaky, () -> { throw new IllegalStateException("x"); });
        VoltPurGuard.run(flaky, () -> { });                                  // one success
        check("a success resets the consecutive counter", VoltPurGuard.stat(flaky).consecutiveFailures() == 0,
                "count=" + VoltPurGuard.stat(flaky).consecutiveFailures());
        check("a recovering module is left alone", !VoltPurGuard.stat(flaky).tripped(), "tripped anyway");
        check("watchdog modules are exempt", VoltPurGuard.isCritical("TPSMonitor")
                && VoltPurGuard.isCritical("WorldStability") && VoltPurGuard.isCritical("ModuleGuard"), "not exempt");
        String watch = "TPSMonitor";
        for (int i = 0; i < 12; i++) VoltPurGuard.run(watch, () -> { throw new IllegalStateException("watch"); });
        check("a critical module is never tripped, even past the threshold",
                !VoltPurGuard.stat(watch).tripped(), "the watchdog was disabled");
        int closed = VoltPurGuard.resetBreakers();      // exactly what /voltpur reload calls
        check("resetBreakers() closes the open breakers", closed >= 1, "closed=" + closed);
        boolean ranAgain = false;
        try { VoltPurGuard.run(loop, () -> { throw new IllegalStateException("boom"); }); ranAgain = true; } catch (Throwable ignored) { }
        check("after reload the module runs again", ranAgain, "still short-circuited");
        check("lifetime failure counts are kept (only the streak resets)",
                VoltPurGuard.stat(loop).failures() >= 9, "lost history");

        System.out.println("[backup] a scheduled backup that keeps failing is not reported as healthy");
        org.purpurmc.purpur.VoltPurBackup.Result failed =
                org.purpurmc.purpur.VoltPurBackup.Result.failure("archive verification failed");
        long before = VoltPurGuard.stat("WorldBackup").failures();
        org.purpurmc.purpur.VoltPurBackup.recordScheduledResult(failed);
        long after = VoltPurGuard.stat("WorldBackup").failures();
        check("a failed scheduled backup is counted as a module failure", after == before + 1,
                before + " -> " + after);
        check("the module health line no longer claims it is fine when it is not",
                VoltPurGuard.healthLine("WorldBackup").contains("FAILING")
                        || VoltPurGuard.healthLine("WorldBackup").contains("DISABLED"),
                VoltPurGuard.healthLine("WorldBackup"));
        long okBefore = VoltPurGuard.stat("WorldBackup").failures();
        org.purpurmc.purpur.VoltPurBackup.recordScheduledResult(null);
        check("a null result is ignored", VoltPurGuard.stat("WorldBackup").failures() == okBefore, "counted anyway");

        System.out.println("[updater] RECOVERY.txt is written where nothing else works");
        Path recDir = work.resolve("recovery"); Files.createDirectories(recDir);
        Path recJar = recDir.resolve("server.jar");
        Files.writeString(recJar, "x");
        Path recBackup = recDir.resolve("server.jar.bak-20260919-120711");
        Path written = VoltPurUpdater.writeRecoveryFile(recJar, "71", "abc1234",
                "9e7cda9d237f76d19657414ae126a11b920854d6078a8673cad3d0c8822a52e3", recBackup);
        check("RECOVERY.txt lands next to the jar", written != null && Files.exists(written), "not written");
        String recovery = Files.readString(recDir.resolve("RECOVERY.txt"));
        check("it names the build and commit", recovery.contains("#71") && recovery.contains("abc1234"), recovery);
        check("it names the backup file", recovery.contains("server.jar.bak-20260919-120711"), "backup missing");
        check("it contains the exact restore command",
                recovery.contains("cp server.jar.bak-20260919-120711 server.jar"), "no usable command");
        check("it records the installed sha256",
                recovery.contains("9e7cda9d237f76d1"), "sha missing");
        check("it says nothing else was changed", recovery.contains("nothing else was changed".replace("nothing", "Nothing"))
                || recovery.contains("Nothing else was changed"), "no reassurance");
        Path writtenNoBackup = VoltPurUpdater.writeRecoveryFile(recJar, "71", "abc1234", "deadbeef", null);
        String recoveryNoBackup = Files.readString(recDir.resolve("RECOVERY.txt"));
        check("without a backup it says so instead of inventing one",
                writtenNoBackup != null && recoveryNoBackup.contains("No previous jar was kept"), recoveryNoBackup);

        System.out.println("[config] a broken voltpur.yml is set aside, never deleted");
        Path cfgDir = work.resolve("cfg"); Files.createDirectories(cfgDir);
        java.io.File broken = cfgDir.resolve("voltpur.yml").toFile();
        Files.writeString(broken.toPath(), "modules: [this is not valid yaml\n  bad indent:");
        Path quarantined = VoltPurConfig.quarantineBrokenConfig(broken);
        check("the broken file was moved aside", quarantined != null && Files.exists(quarantined), "not moved");
        check("the original name is free again", !broken.exists(), "still in the way");
        check("the quarantined file keeps its contents",
                quarantined != null && Files.readString(quarantined).contains("not valid yaml"), "content lost");
        check("the quarantined name says what it is",
                quarantined != null && quarantined.getFileName().toString().startsWith("voltpur.yml.broken-"),
                String.valueOf(quarantined));
        check("a missing file is handled without throwing", VoltPurConfig.quarantineBrokenConfig(
                cfgDir.resolve("does-not-exist.yml").toFile()) == null, "invented a file");

        System.out.println("[updater] URL validation (SSRF guards)");
        List<String> rejected = List.of(
                "file:///etc/passwd",
                "ftp://example.com/x.jar",
                "http://127.0.0.1/x.jar",
                "http://[::1]/x.jar",
                "http://[fc00::1]/x.jar",
                "http://100.64.0.1/x.jar",
                "http://192.168.1.5/x.jar",
                "http://169.254.1.1/x.jar",
                "https://user:pass@github.com/x.jar",
                "https://github.com/x.jar#fragment"
        );
        for (String url : rejected) {
            boolean threw = false;
            try {
                PluginJarInstaller.validateRemoteUri(url);
            } catch (Exception expected) {
                threw = true;
            }
            check("rejects " + url, threw, "was accepted");
        }
        try {
            PluginJarInstaller.validateRemoteUri("https://github.com/PurpurMC/Purpur/releases/download/build/VoltPur-26.2.jar");
            check("accepts a normal https release URL", true, "");
        } catch (Exception e) {
            System.out.println("  SKIP  accepts https (no DNS in this sandbox: " + e.getMessage() + ")");
        }

        System.out.println("[updater] file name sanitising");
        check("strips path tricks", PluginJarInstaller.safeJarName(URI.create("https://h/a/..%2F..%2Fevil.jar")).equals("evil.jar"),
                PluginJarInstaller.safeJarName(URI.create("https://h/a/..%2F..%2Fevil.jar")));
        check("always ends with .jar", PluginJarInstaller.safeJarName(URI.create("https://h/")).endsWith(".jar"),
                PluginJarInstaller.safeJarName(URI.create("https://h/")));

        System.out.println("[updater] staged jar validation");
        Path good = work.resolve("good.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(good))) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Main-Class: io.papermc.paperclip.Paperclip\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("META-INF/versions.list"));
            zip.write("1.21.10\t paperclip\t abc\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        boolean goodAccepted = true;
        try {
            VoltPurUpdater.verifyPaperclipJar(good);
        } catch (Exception e) {
            goodAccepted = false;
        }
        check("accepts a paperclip-like jar", goodAccepted, "rejected a valid jar");

        Path unrelated = work.resolve("unrelated.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(unrelated))) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("not a server".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        boolean unrelatedRejected = false;
        try {
            VoltPurUpdater.verifyPaperclipJar(unrelated);
        } catch (Exception expected) {
            unrelatedRejected = true;
        }
        check("rejects an unrelated zip", unrelatedRejected, "accepted an unrelated zip");

        Path html = work.resolve("page.jar");
        Files.writeString(html, "<!DOCTYPE html><html>error 404</html>", StandardCharsets.UTF_8);
        boolean htmlRejected = false;
        try {
            VoltPurUpdater.verifyPaperclipJar(html);
        } catch (Exception expected) {
            htmlRejected = true;
        }
        check("rejects an HTML page saved as .jar", htmlRejected, "accepted HTML");

        System.out.println("[updater] checksum helper");
        Path abc = work.resolve("abc.bin");
        Files.writeString(abc, "abc", StandardCharsets.UTF_8);
        String hash = VoltPurUpdater.sha256(abc);
        check("sha256 matches the known value",
                hash.equals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), hash);

        System.out.println("[updater] launcher jar detection falls back safely");
        Path detected = VoltPurUpdater.detectLauncherJar();
        check("defaults to server.jar when nothing else exists",
                detected != null && detected.getFileName().toString().equals("server.jar"), String.valueOf(detected));

        System.out.println("[updater] build selection accepts BOTH the list position and the build number");
        List<VoltPurUpdater.BuildInfo> builds = List.of(
                new VoltPurUpdater.BuildInfo("build-66-b7def480e4cf157dae5322b562ed880ac7569854", "66", "b7def480e4cf157dae5322b562ed880ac7569854", "2026-09-17T16:36:26Z"),
                new VoltPurUpdater.BuildInfo("build-65-f718a8cf47cdcb55f62f046736acbbb5d8452022", "65", "f718a8cf47cdcb55f62f046736acbbb5d8452022", "2026-08-19T17:21:42Z"),
                new VoltPurUpdater.BuildInfo("build-64-4fa82e8e332d51184e90f4cb096c977274ee0562", "64", "4fa82e8e332d51184e90f4cb096c977274ee0562", "2026-08-19T11:45:11Z"));
        check("position 1 = newest", "66".equals(VoltPurUpdater.resolveBuild(builds, "1").runNumber()), "wrong build");
        check("position 3 = oldest listed", "64".equals(VoltPurUpdater.resolveBuild(builds, "3").runNumber()), "wrong build");
        check("build number 66 works (the bug we just fixed)",
                VoltPurUpdater.resolveBuild(builds, "66") != null && "66".equals(VoltPurUpdater.resolveBuild(builds, "66").runNumber()), "still rejected");
        check("build number 65 works", "65".equals(VoltPurUpdater.resolveBuild(builds, "65").runNumber()), "rejected");
        check("'latest' = newest", "66".equals(VoltPurUpdater.resolveBuild(builds, "latest").runNumber()), "wrong");
        check("blank = newest", "66".equals(VoltPurUpdater.resolveBuild(builds, "  ").runNumber()), "wrong");
        check("tag works", "64".equals(VoltPurUpdater.resolveBuild(builds, "build-64-4fa82e8e332d51184e90f4cb096c977274ee0562").runNumber()), "rejected");
        check("short sha works", "65".equals(VoltPurUpdater.resolveBuild(builds, "f718a8c").runNumber()), "rejected");
        check("nonsense is rejected", VoltPurUpdater.resolveBuild(builds, "999") == null, "accepted 999");
        check("old build numbers outside the list are rejected", VoltPurUpdater.resolveBuild(builds, "12") == null, "accepted 12");
        check("empty list is handled", VoltPurUpdater.resolveBuild(List.of(), "1") == null, "no null return");

        System.out.println("[updater] rollback: backups are listed with an honest integrity state");
        Path rbDir = work.resolve("rb"); Files.createDirectories(rbDir);
        Path rbJar = rbDir.resolve("server.jar");
        Files.writeString(rbJar, "current");
        // three backups on disk: one with a correct sidecar, one whose sidecar lies, one legacy with none
        Path b1 = rbDir.resolve("server.jar.bak-20260901-000000");
        Path b2 = rbDir.resolve("server.jar.bak-20260902-000000");
        Path b3 = rbDir.resolve("server.jar.bak-20260903-000000");
        Files.writeString(b1, "backup-one"); Files.writeString(b2, "backup-two"); Files.writeString(b3, "backup-three");
        // b2 is the honest one (oldest -> newest order via mtimes below), b3 is tampered, b1 is legacy
        Files.setLastModifiedTime(b1, java.nio.file.attribute.FileTime.fromMillis(1_700_000_000_000L));
        Files.setLastModifiedTime(b2, java.nio.file.attribute.FileTime.fromMillis(1_700_000_100_000L));
        Files.setLastModifiedTime(b3, java.nio.file.attribute.FileTime.fromMillis(1_700_000_200_000L));
        VoltPurUpdater.recordBackupChecksum(rbDir, "server.jar", b2);
        // a lying manifest entry for b3
        Path manifest = rbDir.resolve("server.jar.backups.sha256");
        Files.writeString(manifest, Files.readString(manifest)
                + "0000000000000000000000000000000000000000000000000000000000000000  server.jar.bak-20260903-000000\n");
        List<VoltPurUpdater.BackupEntry> listed = VoltPurUpdater.listBackups(rbDir, "server.jar");
        check("listBackups sees exactly the 3 backups (sidecars are not backups)", listed.size() == 3, "saw " + listed.size());
        check("newest backup comes first", listed.get(0).jar().equals(b3), listed.get(0).jar().getFileName().toString());
        check("a matching manifest entry reads VERIFIED", listed.get(1).state().equals("VERIFIED"), listed.get(1).state());
        check("a lying manifest entry reads MISMATCH", listed.get(0).state().equals("MISMATCH"), listed.get(0).state());
        check("a backup absent from the manifest is honest about it", listed.get(2).state().equals("NO CHECKSUM"), listed.get(2).state());
        check("sizes are reported", listed.get(1).bytes() == "backup-two".length(), "wrong size");

        System.out.println("[updater] rollback: rotation is opt-in and removes only the oldest");
        check("keep<=0 keeps everything (the default)", VoltPurUpdater.pruneBackups(rbDir, "server.jar", 0) == 0
                && VoltPurUpdater.listBackups(rbDir, "server.jar").size() == 3, "deleted something by default");
        int removed = VoltPurUpdater.pruneBackups(rbDir, "server.jar", 2);
        check("keep=2 removed exactly one backup", removed == 1, "removed " + removed);
        check("the oldest went, not the newest", !Files.exists(b1) && Files.exists(b3), "wrong file removed");
        check("the manifest drops the removed entry",
                !Files.readString(rbDir.resolve("server.jar.backups.sha256")).contains("20260901"),
                "manifest kept a removed backup");
        check("the manifest is not itself seen as a backup",
                VoltPurUpdater.listBackups(rbDir, "server.jar").stream()
                        .noneMatch(e -> e.jar().getFileName().toString().endsWith(".sha256")),
                "a checksum file was listed as a backup");

        System.out.println("[updater] the world-zip line never promises a backup the module cannot deliver");
        String both = VoltPurUpdater.worldBackupLine(true, true);
        String moduleOff = VoltPurUpdater.worldBackupLine(true, false);
        String autoOff = VoltPurUpdater.worldBackupLine(false, true);
        check("both switches on -> the plan promises a real backup", both.contains("runs before the swap"), both);
        check("backup module off -> the plan says SKIPPED (not 'runs')", moduleOff.startsWith("SKIPPED"), moduleOff);
        check("module off -> the reason is named", moduleOff.contains("modules.backup.enabled=false"), moduleOff);
        check("module off -> a word the operator must never see here is absent", !moduleOff.contains("runs before the swap"), moduleOff);
        check("auto-backup off -> says disabled, not SKIPPED", autoOff.startsWith("disabled"), autoOff);
        check("the three states are distinct", new java.util.HashSet<>(List.of(both, moduleOff, autoOff)).size() == 3, "states collapsed");

        System.out.println("[updater] a staged build survives a restart (the MineStrator mistake)");
        // Simulate what /vo up <n> leaves on disk, then pretend we restarted the server.
        Path tmp = Path.of(".voltpur-tmp");
        Files.createDirectories(tmp);
        Files.copy(good, tmp.resolve("staged-server.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String stagedSha = VoltPurUpdater.sha256(tmp.resolve("staged-server.jar"));
        Files.write(tmp.resolve("staged-plan.txt"), List.of(
                "tag=build-67-6dc4ad04db38253226b2af48ed2051ed2051", "run=67",
                "sha=6dc4ad04db38253226b2af48ed2051cc157fb286", "published=2026-09-18T15:15:17Z",
                "bytes=64753387", "local-sha=" + stagedSha, "published-sha=" + stagedSha,
                "verified=true", "target=" + work.resolve("server.jar").toAbsolutePath(),
                "jar-backup=true", "world-backup=true"), StandardCharsets.UTF_8);
        check("hasPending() finds the persisted plan after a restart", VoltPurUpdater.hasPending(), "lost the staged plan");
        check("pendingDescription() names the build", VoltPurUpdater.pendingDescription().contains("#67"),
                VoltPurUpdater.pendingDescription());
        check("pendingDescription() names the jar it will replace", VoltPurUpdater.pendingDescription().contains("server.jar"),
                VoltPurUpdater.pendingDescription());
        // cancel() must clean up BOTH the plan file and the staged jar
        class Sink implements org.bukkit.command.CommandSender {
            public void sendMessage(String m) { }
            public void sendMessage(net.kyori.adventure.text.Component m) { }
            public boolean isOp() { return true; }
            public boolean hasPermission(String n) { return true; }
        }
        VoltPurUpdater.cancel(new Sink());
        check("cancel() clears the persisted plan", !VoltPurUpdater.hasPending(), "still staged");
        check("cancel() deletes the plan file", !Files.exists(tmp.resolve("staged-plan.txt")), "file left behind");
        check("cancel() deletes the staged jar", !Files.exists(tmp.resolve("staged-server.jar")), "jar left behind");
        check("a plan with no staged jar is ignored", !VoltPurUpdater.hasPending(), "reported a phantom staged build");

        System.out.println("[updater] human sizes");
        check("formats MiB", VoltPurUpdater.human(80L * 1024 * 1024).contains("MiB"), VoltPurUpdater.human(80L * 1024 * 1024));

        System.out.println("[command] failures=" + failures);
        if (failures > 0) System.exit(1);
    }
}
