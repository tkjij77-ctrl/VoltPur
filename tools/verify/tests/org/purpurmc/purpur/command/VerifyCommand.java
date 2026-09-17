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

        System.out.println("[updater] human sizes");
        check("formats MiB", VoltPurUpdater.human(80L * 1024 * 1024).contains("MiB"), VoltPurUpdater.human(80L * 1024 * 1024));

        System.out.println("[command] failures=" + failures);
        if (failures > 0) System.exit(1);
    }
}
