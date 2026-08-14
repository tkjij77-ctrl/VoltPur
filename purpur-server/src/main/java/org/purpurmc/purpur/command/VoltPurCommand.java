package org.purpurmc.purpur.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurHardware;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.VoltPurTuning;
import org.purpurmc.purpur.VoltPurBenchmark;
import org.purpurmc.purpur.VoltPurOptimizer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Location;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class VoltPurCommand extends Command {
    // Cached recent successful run IDs (index 0 = newest), used by /vo up list + numeric selection.
    private static final java.util.List<String> CACHED_RUN_IDS = new java.util.ArrayList<>();

    public VoltPurCommand(String name) {
        super(name);
        this.description = "VoltPur main command - help, version, modules, hardware, benchmark, update";
        this.usageMessage = "/voltpur [help|version|modules|status|worlds|hardware|flags|optimize|benchmark|reload|up|up list]";
        this.setPermission(null);
        this.setAliases(java.util.Arrays.asList("vo"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            return Stream.of("help", "version", "modules", "status", "worlds", "hardware", "flags", "optimize", "benchmark", "reload", "up")
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // ---- VoltPur Help: self-documenting reference ----
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")
                || args[0].equalsIgnoreCase("commands")) {
            new VoltPurHelp("voltpur").execute(sender, label, new String[]{});
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("version") || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(Component.text("[VoltPur] VoltPur " + VoltPur.VERSION + " | MC " + VoltPur.MC_VERSION, NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Brand: " + VoltPur.BRAND + " | Real modules: " + VoltPurModules.activeCount() + "/" + VoltPurModules.totalCount() + " ACTIVE", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Use /voltpur modules (honest status) | /voltpur benchmark (real numbers)", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Use /voltpur hardware to check device compatibility", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Use /vo up [buildId] to update via hosting internet", NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("modules")) {
            sender.sendMessage(Component.text("=== [VoltPur] VoltCore Modules (honest status) ===", NamedTextColor.GOLD));
            for (String name : VoltPurModules.all().keySet()) {
                sender.sendMessage(Component.text("  " + VoltPurModules.line(name), NamedTextColor.GRAY));
            }
            sender.sendMessage(Component.text("ACTIVE=" + VoltPurModules.activeCount() + " PARTIAL="
                + (VoltPurModules.totalCount() - VoltPurModules.activeCount()) + " | PLANNED listed honestly.", NamedTextColor.YELLOW));
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text("=== [VoltPur] Stability Status ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Version: " + VoltPur.VERSION, NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Worlds: " + Bukkit.getWorlds().size(), NamedTextColor.AQUA));
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                sender.sendMessage(Component.text("- " + w.getName() + " (" + w.getEnvironment() + ") E:" + w.getEntities().size() + " C:" + w.getLoadedChunks().length, NamedTextColor.GRAY));
            }
            File[] checks = { new File("server.properties"), new File("bukkit.yml"), new File("purpur.yml"), new File("voltpur.yml"), new File("world"), new File("plugins") };
            int ok=0; for(File f:checks) if(f.exists()) ok++;
            sender.sendMessage(Component.text("Files: " + ok + "/" + checks.length + " OK", ok==checks.length?NamedTextColor.GREEN:NamedTextColor.YELLOW));
            double[] tps = Bukkit.getServer().getTPS();
            sender.sendMessage(Component.text("TPS: " + String.format("%.2f, %.2f, %.2f", tps[0], tps[1], tps[2]), NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Status: " + (ok==checks.length && Bukkit.getWorlds().size()>=1 ? "STABLE" : "DEGRADED"), NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("worlds")) {
            sender.sendMessage(Component.text("Worlds (" + Bukkit.getWorlds().size() + "):", NamedTextColor.GOLD));
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                sender.sendMessage(Component.text(w.getName() + " - " + w.getEnvironment() + " - loaded", NamedTextColor.GRAY));
            }
            return true;
        }

        // ---- Hardware: device compatibility + performance ----
        if (args[0].equalsIgnoreCase("hardware")) {
            VoltPurHardware.detect();
            for (String line : VoltPurHardware.hardwareReport()) {
                sender.sendMessage(Component.text(line, NamedTextColor.AQUA));
            }
            sender.sendMessage(Component.text("Report also at logs/voltpur-hardware-report.txt", NamedTextColor.GRAY));
            return true;
        }
        if (args[0].equalsIgnoreCase("flags") || args[0].equalsIgnoreCase("jvm")) {
            VoltPurHardware.detect();
            sender.sendMessage(Component.text("=== [VoltPur] Recommended JVM Flags for THIS machine ===", NamedTextColor.GOLD));
            sender.sendMessage(Component.text(VoltPurHardware.recommendedJvmArgs(), NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("optimize") || args[0].equalsIgnoreCase("tune")) {
            if (!sender.hasPermission("voltpur.admin.tune") && !sender.isOp()) {
                sender.sendMessage(Component.text("No permission - voltpur.admin.tune or OP", NamedTextColor.RED));
                return true;
            }
            VoltPurHardware.detect();
            sender.sendMessage(Component.text("Applying dynamic optimizer (adaptive spigot.yml)...", NamedTextColor.YELLOW));
            VoltPurOptimizer.apply();
            sender.sendMessage(Component.text("Applying hardware-tuned server.properties...", NamedTextColor.YELLOW));
            boolean applied = VoltPurTuning.applyServerProperties();
            VoltPurTuning.writeTuningSheet();
            sender.sendMessage(Component.text(applied ? "Applied! Sheet -> logs/voltpur-tuning.txt (restart to fully take effect)"
                    : "Not applied. Enable modules.hardware.auto-tune in voltpur.yml.", applied ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            return true;
        }

        // ---- Benchmark: the ONLY source of real numbers ----
        if (args[0].equalsIgnoreCase("benchmark") || args[0].equalsIgnoreCase("perf")) {
            sender.sendMessage(Component.text("Running live benchmark...", NamedTextColor.YELLOW));
            for (String line : VoltPurBenchmark.snapshot("manual")) {
                sender.sendMessage(Component.text(line, NamedTextColor.AQUA));
            }
            VoltPurBenchmark.record("manual");
            sender.sendMessage(Component.text("Measured snapshot appended to logs/voltpur-benchmark.txt", NamedTextColor.GRAY));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("voltpur.admin.reload") && !sender.isOp()) {
                sender.sendMessage(Component.text("No permission - requires voltpur.admin.reload or OP", NamedTextColor.RED));
                return true;
            }
            VoltPurConfig.init();
            sender.sendMessage(Component.text("VoltPur config reloaded!", NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("up") || args[0].equalsIgnoreCase("update")) {
            if (!sender.hasPermission("voltpur.admin.update") && !sender.isOp()) {
                sender.sendMessage(Component.text("No permission - voltpur.admin.update", NamedTextColor.RED));
                return true;
            }
            // /vo up list - show recent builds (New / Back, numbered)
            if (args.length > 1 && args[1].equalsIgnoreCase("list")) {
                scheduleAsync(sender, () -> listBuilds(sender));
                return true;
            }
            String buildId = args.length > 1 ? args[1] : null;
            // numeric selection from the last /vo up list
            if (buildId != null && buildId.matches("\\d{1,2}") && !CACHED_RUN_IDS.isEmpty()) {
                int idx = Integer.parseInt(buildId) - 1;
                if (idx >= 0 && idx < CACHED_RUN_IDS.size()) {
                    buildId = CACHED_RUN_IDS.get(idx);
                    sender.sendMessage(Component.text("Selected build #" + (idx + 1) + " -> run " + buildId, NamedTextColor.GREEN));
                }
            }
            final String finalBuildId = buildId;
            sender.sendMessage(Component.text("[VoltPur] VoltPur Updater - Checking for updates...", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text(finalBuildId != null ? "Build ID: " + finalBuildId : "No build ID, using latest successful build", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Downloading via hosting internet to save your data...", NamedTextColor.AQUA));
            scheduleAsync(sender, () -> doUpdate(sender, finalBuildId));
            return true;
        }
        sender.sendMessage(Component.text("Usage: "+usageMessage, NamedTextColor.RED));
        return false;
    }

    /** Runs a task on the Bukkit async scheduler with a real plugin owner. */
    private void scheduleAsync(CommandSender sender, Runnable task) {
        try {
            org.bukkit.plugin.Plugin p = org.purpurmc.purpur.VoltPurPlugin.get();
            if (p == null) {
                sender.sendMessage(Component.text("No plugin available for async scheduling. Add at least one plugin to plugins/ then retry.", NamedTextColor.RED));
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(p, () -> {
                try { task.run(); }
                catch (Exception e) {
                    sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                    e.printStackTrace();
                }
            });
        } catch (Throwable e) {
            sender.sendMessage(Component.text("Scheduling failed: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    /** Lists the most recent successful builds, numbered, split New (newest) / Back (older). */
    private void listBuilds(CommandSender sender) {
        sender.sendMessage(Component.text("Fetching recent builds...", NamedTextColor.YELLOW));
        try {
            String repo = "tkjij77-ctrl/VoltPur";
            String token = VoltPurConfig.githubToken;
            boolean hasToken = token != null && !token.isEmpty();
            String url = "https://api.github.com/repos/" + repo + "/actions/runs?per_page=10&status=success&branch=ver/26.2";
            String json = httpGet(url, hasToken ? token : null);
            CACHED_RUN_IDS.clear();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":\\s*(\\d+)").matcher(json);
            while (m.find()) CACHED_RUN_IDS.add(m.group(1));
            if (CACHED_RUN_IDS.isEmpty()) {
                sender.sendMessage(Component.text("No successful builds found.", NamedTextColor.RED));
                return;
            }
            sender.sendMessage(Component.text("=== Recent VoltPur builds (newest first) ===", NamedTextColor.GOLD));
            for (int i = 0; i < CACHED_RUN_IDS.size(); i++) {
                boolean isNew = i < 5;
                sender.sendMessage(Component.text("  [" + (i + 1) + "] " + (isNew ? "New" : "Back") + " - run " + CACHED_RUN_IDS.get(i),
                        isNew ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
            sender.sendMessage(Component.text("Use /vo up <number> to install that build.", NamedTextColor.AQUA));
        } catch (Exception e) {
            sender.sendMessage(Component.text("Failed to list builds: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private void doUpdate(CommandSender sender, String buildId) {
        try {
            String repo = "tkjij77-ctrl/VoltPur";
            String token = VoltPurConfig.githubToken;
            boolean hasToken = token != null && !token.isEmpty() && !token.equals("");

            String runId = buildId;
            if (runId == null || runId.isEmpty()) {
                sender.sendMessage(Component.text("Fetching latest successful build...", NamedTextColor.YELLOW));
                String runsUrl = "https://api.github.com/repos/" + repo + "/actions/runs?per_page=1&status=success&branch=ver/26.2";
                String runsJson = httpGet(runsUrl, hasToken ? token : null);
                String idMarker = "\"id\":";
                int idx = runsJson.indexOf(idMarker);
                if (idx == -1) throw new Exception("Could not parse latest run ID");
                int start = runsJson.indexOf(":", idx) + 1;
                int end = runsJson.indexOf(",", start);
                runId = runsJson.substring(start, end).trim();
                sender.sendMessage(Component.text("Latest build: " + runId, NamedTextColor.GREEN));
            }

            sender.sendMessage(Component.text("Fetching artifacts for build " + runId + "...", NamedTextColor.YELLOW));
            String artifactsUrl = "https://api.github.com/repos/" + repo + "/actions/runs/" + runId + "/artifacts";
            String artifactsJson = httpGet(artifactsUrl, hasToken ? token : null);
            if (!artifactsJson.contains("\"total_count\": 1") && !artifactsJson.contains("\"total_count\":1")) {
                if (!artifactsJson.contains("\"id\"")) {
                    throw new Exception("No artifacts found for build " + runId + ". Build may have failed or expired. Try latest.");
                }
            }
            int artIdx = artifactsJson.indexOf("\"id\":");
            int artStart = artifactsJson.indexOf(":", artIdx) + 1;
            int artEnd = artifactsJson.indexOf(",", artStart);
            String artifactId = artifactsJson.substring(artStart, artEnd).trim();
            sender.sendMessage(Component.text("Found artifact: " + artifactId, NamedTextColor.GREEN));

            if (!hasToken) {
                sender.sendMessage(Component.text("[WARN] No GitHub token in voltpur.yml, trying public release...", NamedTextColor.YELLOW));
                try {
                    String releaseUrl = "https://github.com/" + repo + "/releases/latest/download/VoltPur-26.2.jar";
                    sender.sendMessage(Component.text("Downloading from release: " + releaseUrl, NamedTextColor.YELLOW));
                    java.nio.file.Path tempJar = Files.createTempFile("voltpur-release-", ".jar");
                    downloadFilePublic(releaseUrl, tempJar);
                    long size = Files.size(tempJar);
                    if (size < 1000000) {
                        throw new Exception("Downloaded file too small (" + size + " bytes) - release may not exist yet. Set github-token.");
                    }
                    sender.sendMessage(Component.text("Downloaded release jar: " + (size/1024/1024) + "MB", NamedTextColor.GREEN));
                    java.nio.file.Path currentJar = java.nio.file.Path.of("server.jar");
                    java.nio.file.Path backupJar = java.nio.file.Path.of("server.jar.old");
                    if (Files.exists(currentJar)) {
                        Files.copy(currentJar, backupJar, StandardCopyOption.REPLACE_EXISTING);
                        sender.sendMessage(Component.text("Backed up to server.jar.old", NamedTextColor.GRAY));
                    }
                    Files.copy(tempJar, java.nio.file.Path.of("server.jar"), StandardCopyOption.REPLACE_EXISTING);
                    sender.sendMessage(Component.text("[OK] Updated server.jar from public release (" + (size/1024/1024) + "MB)", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("Restart to apply: /restart", NamedTextColor.YELLOW));
                    Files.deleteIfExists(tempJar);
                    return;
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Public release download failed: " + e.getMessage(), NamedTextColor.RED));
                    sender.sendMessage(Component.text("Set github-token in voltpur.yml for artifact download.", NamedTextColor.YELLOW));
                    return;
                }
            }

            String downloadUrl = "https://api.github.com/repos/" + repo + "/actions/artifacts/" + artifactId + "/zip";
            sender.sendMessage(Component.text("Downloading artifact via hosting internet...", NamedTextColor.YELLOW));
            java.nio.file.Path tempZip = Files.createTempFile("voltpur-update-", ".zip");
            downloadFile(downloadUrl, tempZip, token);
            long size = Files.size(tempZip);
            sender.sendMessage(Component.text("Downloaded: " + (size/1024/1024) + "MB", NamedTextColor.GREEN));

            sender.sendMessage(Component.text("Extracting server.jar...", NamedTextColor.YELLOW));
            java.nio.file.Path tempDir = Files.createTempDirectory("voltpur-extract-");
            String extractedJar = null;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.equals("server.jar") || name.equals("VoltPur-26.2.jar") || name.equals("VoltPur.jar")) {
                        java.nio.file.Path outPath = tempDir.resolve(name);
                        Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                        if (name.equals("server.jar") || extractedJar == null) {
                            extractedJar = outPath.toString();
                            if (name.equals("server.jar")) extractedJar = outPath.toString();
                        }
                    }
                    zis.closeEntry();
                }
            }
            if (extractedJar == null) {
                try (java.util.stream.Stream<java.nio.file.Path> stream = Files.list(tempDir)) {
                    java.util.List<java.nio.file.Path> jars =
                        stream.filter(p -> p.toString().endsWith(".jar"))
                              .collect(java.util.stream.Collectors.toList());
                    if (!jars.isEmpty()) extractedJar = jars.get(0).toString();
                }
            }
            if (extractedJar == null) throw new Exception("No jar found in artifact zip");

            java.nio.file.Path currentJar = java.nio.file.Path.of("server.jar");
            if (!Files.exists(currentJar)) {
                currentJar = java.nio.file.Path.of("VoltPur-26.2.jar");
                if (!Files.exists(currentJar)) currentJar = java.nio.file.Path.of("purpur-server/build/libs/VoltPur-26.2.jar");
            }
            java.nio.file.Path backupJar = java.nio.file.Path.of("server.jar.old");
            if (Files.exists(currentJar)) {
                Files.copy(currentJar, backupJar, StandardCopyOption.REPLACE_EXISTING);
                sender.sendMessage(Component.text("Backed up old jar to server.jar.old", NamedTextColor.GRAY));
            }
            java.nio.file.Path targetJar = java.nio.file.Path.of("server.jar");
            Files.copy(java.nio.file.Path.of(extractedJar), targetJar, StandardCopyOption.REPLACE_EXISTING);
            long newSize = Files.size(targetJar);
            sender.sendMessage(Component.text("[OK] Updated server.jar (" + (newSize/1024/1024) + "MB) from build " + runId, NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Restart server to apply update", NamedTextColor.YELLOW));
            Files.deleteIfExists(tempZip);

        } catch (Exception e) {
            sender.sendMessage(Component.text("[FAIL] Update failed: " + e.getMessage(), NamedTextColor.RED));
            e.printStackTrace();
            Bukkit.getLogger().warning("[VoltPur] Update failed: " + e.getMessage());
        }
    }

    private String httpGet(String urlStr, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "VoltPur-Updater/1.0");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "token " + token);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        if (code != 200) {
            InputStream err = conn.getErrorStream();
            throw new Exception("HTTP " + code + " for " + urlStr + " - " + (err != null ? new String(err.readAllBytes()) : ""));
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes());
        }
    }

    private void downloadFilePublic(String urlStr, java.nio.file.Path dest) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "VoltPur-Updater/1.0");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code != 200) throw new Exception("Public download failed HTTP " + code);
        try (java.io.InputStream is = conn.getInputStream(); java.io.OutputStream os = java.nio.file.Files.newOutputStream(dest)) {
            is.transferTo(os);
        }
    }

    private void downloadFile(String urlStr, java.nio.file.Path dest, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "VoltPur-Updater/1.0");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "token " + token);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code == 302 || code == 301) {
            String loc = conn.getHeaderField("Location");
            if (loc != null) {
                URL redirectUrl = new URL(loc);
                HttpURLConnection conn2 = (HttpURLConnection) redirectUrl.openConnection();
                conn2.setRequestMethod("GET");
                conn2.setConnectTimeout(15000);
                conn2.setReadTimeout(120000);
                conn = conn2;
                code = conn.getResponseCode();
            }
        }
        if (code != 200) throw new Exception("Download failed HTTP " + code);
        try (InputStream is = conn.getInputStream(); OutputStream os = Files.newOutputStream(dest)) {
            is.transferTo(os);
        }
    }
}
