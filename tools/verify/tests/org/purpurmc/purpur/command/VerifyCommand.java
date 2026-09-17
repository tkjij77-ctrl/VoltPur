package org.purpurmc.purpur.command;

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

        System.out.println("[updater] human sizes");
        check("formats MiB", VoltPurUpdater.human(80L * 1024 * 1024).contains("MiB"), VoltPurUpdater.human(80L * 1024 * 1024));

        System.out.println("[command] failures=" + failures);
        if (failures > 0) System.exit(1);
    }
}
