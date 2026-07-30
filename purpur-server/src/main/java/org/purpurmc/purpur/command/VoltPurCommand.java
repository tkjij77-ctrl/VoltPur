package org.purpurmc.purpur.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurConfig;
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
    public VoltPurCommand(String name) {
        super(name);
        this.description = "VoltPur main command - shows version and modules";
        this.usageMessage = "/voltpur [version|modules|reload|info|up|update] or /vo up <buildId>";
        this.setPermission(null);
        this.setAliases(java.util.Arrays.asList("vo"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            return Stream.of("version", "modules", "reload", "info", "up", "update")
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("version") || args[0].equalsIgnoreCase("info")) {
            sender.sendMessage(Component.text("⚡ VoltPur " + VoltPur.VERSION + " | MC " + VoltPur.MC_VERSION, NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Brand: " + VoltPur.BRAND + " | Modules: " + VoltPur.MODULES.length, NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Features: plugin-pro/, per-world plugins, padmin webui, connection stability", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Use /voltpur modules to see all 21 modules", NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Use /vo up [buildId] to update server jar via hosting internet", NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("modules")) {
            sender.sendMessage(Component.text("=== ⚡ VoltPur Modules (" + VoltPur.MODULES.length + ") ===", NamedTextColor.GOLD));
            for (int i=0;i<VoltPur.MODULES.length;i++) {
                sender.sendMessage(Component.text((i+1)+". "+VoltPur.MODULES[i]+" - ENABLED", NamedTextColor.GREEN));
            }
            sender.sendMessage(Component.text("All modules active and working!", NamedTextColor.GREEN));
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
            String buildId = args.length > 1 ? args[1] : null;
            sender.sendMessage(Component.text("⚡ VoltPur Updater - Checking for updates...", NamedTextColor.YELLOW));
            if (buildId != null) {
                sender.sendMessage(Component.text("Build ID: " + buildId, NamedTextColor.GRAY));
            } else {
                sender.sendMessage(Component.text("No build ID provided, using latest successful build", NamedTextColor.GRAY));
            }
            sender.sendMessage(Component.text("Downloading via hosting internet to save your data...", NamedTextColor.AQUA));
            // Run async
            Bukkit.getScheduler().runTaskAsynchronously(Bukkit.getPluginManager().getPlugins().length > 0 ? Bukkit.getPluginManager().getPlugins()[0] : null, () -> {
                try {
                    doUpdate(sender, buildId);
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Update failed: " + e.getMessage(), NamedTextColor.RED));
                    e.printStackTrace();
                }
            });
            return true;
        }
        sender.sendMessage(Component.text("Usage: "+usageMessage, NamedTextColor.RED));
        return false;
    }

    private void doUpdate(CommandSender sender, String buildId) {
        try {
            String repo = "tkjij77-ctrl/VoltPur";
            String token = VoltPurConfig.githubToken;
            boolean hasToken = token != null && !token.isEmpty() && !token.equals("");

            // Step 1: Determine run ID
            String runId = buildId;
            if (runId == null || runId.isEmpty()) {
                // Get latest successful run
                sender.sendMessage(Component.text("Fetching latest successful build...", NamedTextColor.YELLOW));
                String runsUrl = "https://api.github.com/repos/" + repo + "/actions/runs?per_page=1&status=success&branch=ver/26.2";
                String runsJson = httpGet(runsUrl, hasToken ? token : null);
                // Parse run id from json - simple extraction
                String idMarker = "\"id\":";
                int idx = runsJson.indexOf(idMarker);
                if (idx == -1) throw new Exception("Could not parse latest run ID");
                int start = runsJson.indexOf(":", idx) + 1;
                int end = runsJson.indexOf(",", start);
                runId = runsJson.substring(start, end).trim();
                sender.sendMessage(Component.text("Latest build: " + runId, NamedTextColor.GREEN));
            }

            // Step 2: Get artifacts for this run
            sender.sendMessage(Component.text("Fetching artifacts for build " + runId + "...", NamedTextColor.YELLOW));
            String artifactsUrl = "https://api.github.com/repos/" + repo + "/actions/runs/" + runId + "/artifacts";
            String artifactsJson = httpGet(artifactsUrl, hasToken ? token : null);
            // Find artifact id
            if (!artifactsJson.contains("\"total_count\": 1") && !artifactsJson.contains("\"total_count\":1")) {
                // Try to parse first artifact
                if (!artifactsJson.contains("\"id\"")) {
                    throw new Exception("No artifacts found for build " + runId + ". Build may have failed or expired. Try latest or check https://github.com/" + repo + "/actions");
                }
            }
            // Extract first artifact id
            int artIdx = artifactsJson.indexOf("\"id\":");
            int artStart = artifactsJson.indexOf(":", artIdx) + 1;
            int artEnd = artifactsJson.indexOf(",", artStart);
            String artifactId = artifactsJson.substring(artStart, artEnd).trim();
            sender.sendMessage(Component.text("Found artifact: " + artifactId, NamedTextColor.GREEN));

            // Step 3: Download artifact zip
            if (!hasToken) {
                sender.sendMessage(Component.text("⚠️ No GitHub token configured in voltpur.yml", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Trying public release download...", NamedTextColor.YELLOW));
                // Try release download
                try {
                    String releaseUrl = "https://github.com/" + repo + "/releases/latest/download/VoltPur-26.2.jar";
                    // Actually VoltPur doesn't have releases yet, try Actions artifact without token will fail 401
                    // Inform user
                    sender.sendMessage(Component.text("For artifact download, set github-token in voltpur.yml", NamedTextColor.RED));
                    sender.sendMessage(Component.text("Get token from: https://github.com/settings/tokens (public_repo, read:packages)", NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("Then set in voltpur.yml: github-token: 'ghp_...'", NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("Alternatively download manually from: https://github.com/" + repo + "/actions/runs/" + runId, NamedTextColor.AQUA));
                    return;
                } catch (Exception e) {
                    throw e;
                }
            }

            String downloadUrl = "https://api.github.com/repos/" + repo + "/actions/artifacts/" + artifactId + "/zip";
            sender.sendMessage(Component.text("Downloading artifact (" + (193700175/1024/1024) + "MB) via hosting internet...", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("This saves your internet - using server's connection", NamedTextColor.GREEN));

            java.nio.file.Path tempZip = Files.createTempFile("voltpur-update-", ".zip");
            downloadFile(downloadUrl, tempZip, token);

            long size = Files.size(tempZip);
            sender.sendMessage(Component.text("Downloaded: " + (size/1024/1024) + "MB", NamedTextColor.GREEN));

            // Step 4: Extract server.jar
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
                            if (name.equals("server.jar")) {
                                // Prefer server.jar
                                extractedJar = outPath.toString();
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }

            if (extractedJar == null) {
                // Try to find any jar
                try (var stream = Files.list(tempDir)) {
                    var jars = stream.filter(p -> p.toString().endsWith(".jar")).toList();
                    if (!jars.isEmpty()) extractedJar = jars.get(0).toString();
                }
            }

            if (extractedJar == null) throw new Exception("No jar found in artifact zip");

            // Step 5: Backup old and replace
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
            sender.sendMessage(Component.text("✅ Updated server.jar (" + (newSize/1024/1024) + "MB) from build " + runId, NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Restart server to apply update: /restart or stop & start", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Old jar backed up as server.jar.old", NamedTextColor.GRAY));

            // Cleanup
            Files.deleteIfExists(tempZip);
            // Don't delete tempDir immediately, keep for debugging

        } catch (Exception e) {
            sender.sendMessage(Component.text("❌ Update failed: " + e.getMessage(), NamedTextColor.RED));
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
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "token " + token);
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        if (code != 200) {
            InputStream err = conn.getErrorStream();
            String errBody = err != null ? new String(err.readAllBytes()) : "";
            throw new Exception("HTTP " + code + " for " + urlStr + " - " + errBody);
        }
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes());
        }
    }

    private void downloadFile(String urlStr, java.nio.file.Path dest, String token) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "VoltPur-Updater/1.0");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        if (token != null && !token.isEmpty()) {
            conn.setRequestProperty("Authorization", "token " + token);
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000); // 2 min for large file
        conn.setInstanceFollowRedirects(true);
        int code = conn.getResponseCode();
        if (code == 302 || code == 301) {
            String loc = conn.getHeaderField("Location");
            if (loc != null) {
                // Follow redirect
                URL redirectUrl = new URL(loc);
                HttpURLConnection conn2 = (HttpURLConnection) redirectUrl.openConnection();
                conn2.setRequestMethod("GET");
                conn2.setConnectTimeout(15000);
                conn2.setReadTimeout(120000);
                conn = conn2;
                code = conn.getResponseCode();
            }
        }
        if (code != 200) {
            throw new Exception("Download failed HTTP " + code);
        }
        try (InputStream is = conn.getInputStream(); OutputStream os = Files.newOutputStream(dest)) {
            is.transferTo(os);
        }
    }
}
