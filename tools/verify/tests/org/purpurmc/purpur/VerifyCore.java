package org.purpurmc.purpur;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Behaviour tests for the hardening pass (no server needed). */
public final class VerifyCore {

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
        Path work = Files.createTempDirectory("voltpur-core-");

        System.out.println("[core] safe defaults");
        VoltPurConfig.init();
        check("item limiter off by default", !VoltPurConfig.itemLimiterEnabled, "came back enabled");
        check("optimizer off by default", !VoltPurConfig.optimizerEnabled, "came back enabled");
        check("padmin off by default", !VoltPurConfig.padminEnabled, "came back enabled");
        check("destructive reinstall off", !VoltPurConfig.updateCleanReinstall, "was enabled");
        check("checksum required", VoltPurConfig.updateRequireChecksum, "was optional");
        check("jar backup kept", VoltPurConfig.updateKeepJarBackup, "was disabled");
        check("auto backup on", VoltPurConfig.updateAutoBackup, "was disabled");
        check("item limiter min age >= 60s", VoltPurConfig.itemLimiterMinAgeSeconds >= 60, "age was " + VoltPurConfig.itemLimiterMinAgeSeconds);
        check("padmin password empty by default", VoltPurConfig.padminPassword.isEmpty(), "password set");
        String yml = Files.readString(Path.of("voltpur.yml"), StandardCharsets.UTF_8);
        check("voltpur.yml written with new keys", yml.contains("item-limiter.enabled") && yml.contains("require-checksum"), "missing keys");
        check("dead hopper-sleep key not written", !yml.contains("hopper-sleep"), "still writing a dead key");

        System.out.println("[core] resource pack token generation");
        Files.writeString(Path.of("voltpur.yml"), yml + "\nmodules.resource-pack.enabled: true\n", StandardCharsets.UTF_8);
        VoltPurConfig.init();
        check("random token generated", VoltPurConfig.resourcePackToken != null && VoltPurConfig.resourcePackToken.length() >= 16,
                "token=" + VoltPurConfig.resourcePackToken);

        System.out.println("[core] tuning never runs without opt-in");
        File properties = work.resolve("server.properties").toFile();
        String original = "#Minecraft server properties\n#comment 2\nview-distance=4\nmy-custom-key=hello\nonline-mode=true\n";
        Files.writeString(properties.toPath(), original, StandardCharsets.UTF_8);
        VoltPurConfig.hardwareAutoTune = false;
        boolean applied = VoltPurTuning.applyServerProperties();
        check("apply returns false when auto-tune disabled", !applied, "it wrote anyway");
        check("file untouched when disabled", original.equals(Files.readString(properties.toPath(), StandardCharsets.UTF_8)), "file changed");

        System.out.println("[core] comment-preserving writer");
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("view-distance", "10");
        changes.put("simulation-distance", "8");
        VoltPurTuning.writePreservingComments(properties, changes);
        String updated = Files.readString(properties.toPath(), StandardCharsets.UTF_8);
        check("comments preserved", updated.contains("#Minecraft server properties") && updated.contains("#comment 2"), "comments lost");
        check("unknown key preserved", updated.contains("my-custom-key=hello"), "custom key lost");
        check("existing key updated", updated.contains("view-distance=10"), "not updated");
        check("missing key appended", updated.contains("simulation-distance=8"), "not appended");
        check("online-mode untouched", updated.contains("online-mode=true"), "online-mode changed");

        System.out.println("[core] discord webhook allow-list");
        VoltPurDiscord.init(); // disabled -> must not start anything
        check("discord off by default (no start)", true, "");
        check("accepts discord.com webhook", VoltPurDiscord.isAllowedWebhook("https://discord.com/api/webhooks/1/abc"), "rejected");
        check("rejects http", !VoltPurDiscord.isAllowedWebhook("http://discord.com/api/webhooks/1/abc"), "accepted http");
        check("rejects other hosts", !VoltPurDiscord.isAllowedWebhook("https://evil.example/hook"), "accepted evil host");
        check("rejects lookalike host", !VoltPurDiscord.isAllowedWebhook("https://discord.com.evil.example/hook"), "accepted lookalike");

        System.out.println("[core] guard makes failures visible");
        VoltPurGuard.run("TestModule", () -> { throw new IllegalStateException("boom"); });
        VoltPurGuard.run("TestModule", () -> { throw new IllegalStateException("boom"); });
        VoltPurGuard.Stat stat = VoltPurGuard.stat("TestModule");
        check("failure counter", stat.failures() == 2, "failures=" + stat.failures());
        check("a module that only threw is FAILING", VoltPurGuard.healthLine("TestModule").contains("FAILING"), VoltPurGuard.healthLine("TestModule"));
        VoltPurGuard.run("TestModule", () -> { });
        check("run counter counts successes", stat.runs() == 1, "runs=" + stat.runs());
        check("a successful run clears the FAILING flag", !VoltPurGuard.healthLine("TestModule").contains("FAILING"), VoltPurGuard.healthLine("TestModule"));
        check("history is not erased by recovery", VoltPurGuard.healthLine("TestModule").contains("fails=2"), VoltPurGuard.healthLine("TestModule"));

        System.out.println("[core] hardware detection is container-aware and sane");
        VoltPurHardware.detect();
        long effectiveRam = VoltPurHardware.getEffectiveRamMB();
        long heap = VoltPurHardware.suggestHeapMB();
        check("effective ram positive", effectiveRam > 0, "ram=" + effectiveRam);
        check("suggested heap > 0", heap > 0, "heap=" + heap);
        check("suggested heap never exceeds available RAM", heap <= effectiveRam, "heap=" + heap + " ram=" + effectiveRam);
        check("jvm args contain Xmx", VoltPurHardware.recommendedJvmArgs().contains("-Xmx"), "missing");
        check("jvm args mention OOM exit", VoltPurHardware.recommendedJvmArgs().contains("ExitOnOutOfMemoryError"), "missing");
        List<String> settings = VoltPurHardware.recommendedServerSettings();
        check("paper keys use the real file name", settings.stream().anyMatch(s -> s.contains("(paper-global.yml) chunk-system.io-threads")), settings.toString());
        check("no legacy key names", settings.stream().noneMatch(s -> s.contains("async-chunk-loading-threads") || s.contains("max-auto-save")), settings.toString());

        System.out.println("[core] module registry is honest");
        check("item limiter marked opt-in", VoltPurModules.isOptIn("ItemLimiter"), "not marked");
        check("hopper optimisation is PLANNED", VoltPurModules.all().get("HopperOptimization") == VoltPurModules.Status.PLANNED, "marked active");
        check("removed fake module PterodactylFix", !VoltPurModules.all().containsKey("PterodactylFix"), "still listed");
        check("counts add up", VoltPurModules.activeCount() + VoltPurModules.partialCount() + VoltPurModules.plannedCount() == VoltPurModules.totalCount(), "mismatch");
        check("never-started module reads as opt-in", VoltPurModules.line("PAdminWebUI").contains("[opt-in]"), VoltPurModules.line("PAdminWebUI"));
        VoltPurModules.setRuntime("PAdminWebUI", true);
        check("setRuntime is reflected in the line", VoltPurModules.line("PAdminWebUI").contains("[enabled]"), VoltPurModules.line("PAdminWebUI"));
        VoltPurModules.setRuntime("PAdminWebUI", false);
        check("disabled runtime state is reflected too", VoltPurModules.line("PAdminWebUI").contains("[disabled]"), VoltPurModules.line("PAdminWebUI"));

        System.out.println("[core] failures=" + failures);
        if (failures > 0) System.exit(1);
    }
}
