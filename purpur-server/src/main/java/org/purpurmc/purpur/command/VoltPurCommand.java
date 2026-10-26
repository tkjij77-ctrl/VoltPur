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
import org.purpurmc.purpur.VoltPurAutotune;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Location;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.purpurmc.purpur.util.VoltPurLogger;

public class VoltPurCommand extends Command {
    // Cached recent successful run IDs (index 0 = newest), used by /vo up list + numeric selection.
    private static final java.util.List<String> CACHED_RUN_IDS = new java.util.ArrayList<>();
    private static final AtomicBoolean PLUGIN_INSTALL_RUNNING = new AtomicBoolean(false);

    public VoltPurCommand(String name) {
        super(name);
        this.description = "VoltPur main command - help, diagnostics, plugin install, update";
        this.usageMessage = "/voltpur [help|version|modules|status|worlds|hardware|flags|optimize|autotune|benchmark|reload|in|up]";
        this.setPermission(null);
        this.setAliases(java.util.Arrays.asList("vo"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            return Stream.of("help", "version", "modules", "status", "worlds", "hardware", "flags", "optimize", "autotune", "benchmark", "reload", "in", "up")
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("autotune")) {
            return Stream.of("status", "enable", "disable", "history", "rollback")
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("in")) {
            return Stream.of("plugins", "plugin-pro")
                .filter(s -> s.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("up") || args[0].equalsIgnoreCase("update"))) {
            if (args.length == 2) {
                java.util.List<String> suggestions = new java.util.ArrayList<>();
                suggestions.add("list");
                for (int i = 1; i <= Math.max(1, CACHED_RUN_IDS.size()); i++) suggestions.add(String.valueOf(i));
                return suggestions.stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
            java.util.List<String> flags = java.util.Arrays.asList("--confirm", "--clean", "--full-wipe", "--no-backup");
            String last = args[args.length - 1].toLowerCase();
            return flags.stream().filter(s -> s.startsWith(last)).collect(Collectors.toList());
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
                + VoltPurModules.partialCount() + " PLANNED=" + VoltPurModules.plannedCount()
                + (VoltPurModules.degradedCount() > 0 ? " DEGRADED=" + VoltPurModules.degradedCount() : ""), NamedTextColor.YELLOW));
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

        // ---- Autotune: closed-loop adaptive controller (§4.1) ----
        if (args[0].equalsIgnoreCase("autotune")) {
            if (!sender.hasPermission("voltpur.admin.autotune") && !sender.hasPermission("voltpur.admin.tune") && !sender.isOp()) {
                sender.sendMessage(Component.text("No permission - requires voltpur.admin.autotune or OP", NamedTextColor.RED));
                return true;
            }
            if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
                sender.sendMessage(Component.text("=== [VoltPur] Autotune Status (§4.1) ===", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Mode: " + VoltPurConfig.autotuneMode.toUpperCase()
                        + (VoltPurConfig.optimizerEnabled ? " (Optimizer ON)" : " (Optimizer OFF)"), NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Interval: " + VoltPurConfig.autotuneIntervalSeconds + "s | Consecutive High Cycles: "
                        + VoltPurAutotune.getConsecutiveHighCycles() + "/2", NamedTextColor.AQUA));
                sender.sendMessage(Component.text(String.format("Current MSPT: %.2f ms | Safe Min View: %d | Safe Min Sim: %d",
                        VoltPurAutotune.getAverageMspt(), VoltPurConfig.autotuneMinViewDistance, VoltPurConfig.autotuneMinSimulationDistance), NamedTextColor.AQUA));
                Map<String, VoltPurAutotune.WorldBase> baseMap = VoltPurAutotune.getBaseSettings();
                for (org.bukkit.World w : Bukkit.getWorlds()) {
                    VoltPurAutotune.WorldBase base = baseMap.get(w.getName());
                    int baseView = base != null ? base.baseViewDistance : w.getViewDistance();
                    int baseSim = base != null ? base.baseSimulationDistance : w.getSimulationDistance();
                    int baseMon = base != null ? base.baseMonsterLimit : w.getSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER);
                    sender.sendMessage(Component.text(String.format("  World %s: View=%d (base %d), Sim=%d (base %d), Monsters=%d (base %d)",
                            w.getName(), w.getViewDistance(), baseView, w.getSimulationDistance(), baseSim,
                            w.getSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER), baseMon), NamedTextColor.GRAY));
                }
                sender.sendMessage(Component.text("Decisions recorded: " + VoltPurAutotune.getHistory().size() + " | Use /voltpur autotune history", NamedTextColor.GRAY));
                return true;
            }
            if (args[1].equalsIgnoreCase("enable")) {
                VoltPurConfig.autotuneMode = "active";
                VoltPurAutotune.init();
                sender.sendMessage(Component.text("[VoltPur] Autotune enabled in ACTIVE mode (automatic live adjustments enabled).", NamedTextColor.GREEN));
                return true;
            }
            if (args[1].equalsIgnoreCase("disable")) {
                VoltPurConfig.autotuneMode = "advisor";
                sender.sendMessage(Component.text("[VoltPur] Autotune set to ADVISOR mode (recommendations logged, no automated live adjustments).", NamedTextColor.YELLOW));
                return true;
            }
            if (args[1].equalsIgnoreCase("history")) {
                sender.sendMessage(Component.text("=== [VoltPur] Autotune Decision History (Last 20) ===", NamedTextColor.GOLD));
                List<String> lines = VoltPurAutotune.getHistoryLines();
                for (String line : lines) {
                    sender.sendMessage(Component.text(line));
                }
                return true;
            }
            if (args[1].equalsIgnoreCase("rollback")) {
                sender.sendMessage(Component.text("[VoltPur] Rolling back autotune to base values...", NamedTextColor.YELLOW));
                VoltPurAutotune.rollback(sender);
                return true;
            }
            sender.sendMessage(Component.text("Usage: /voltpur autotune [status|enable|disable|history|rollback]", NamedTextColor.RED));
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
        if (args[0].equalsIgnoreCase("in") || args[0].equalsIgnoreCase("install")) {
            if (!sender.hasPermission("voltpur.admin.install") && !sender.isOp()) {
                sender.sendMessage(Component.text("[VoltPur] No permission - requires voltpur.admin.install or OP", NamedTextColor.RED));
                return true;
            }
            if (args.length != 3 || !(args[2].equals("plugins") || args[2].equals("plugin-pro"))) {
                sender.sendMessage(Component.text("Usage: /vo in <plugin-url> <plugins|plugin-pro>", NamedTextColor.RED));
                return true;
            }
            if (!PLUGIN_INSTALL_RUNNING.compareAndSet(false, true)) {
                sender.sendMessage(Component.text("[VoltPur] A plugin download is already running. Please wait.", NamedTextColor.YELLOW));
                return true;
            }
            String pluginUrl = args[1];
            String target = args[2];
            sender.sendMessage(Component.text("[VoltPur] Downloading plugin securely to " + target + "/...", NamedTextColor.YELLOW));
            scheduleAsync(sender, () -> {
                try {
                    PluginJarInstaller.Result result = PluginJarInstaller.install(pluginUrl, target);
                    sender.sendMessage(Component.text("[VoltPur] Installed " + result.path().getFileName() + " (" + (result.bytes() / 1024) + " KiB) in " + target + "/.", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("[VoltPur] Plugin was not loaded automatically. Restart the server to load it.", NamedTextColor.GOLD));
                    Bukkit.getLogger().info("[VoltPur] Plugin installer saved " + result.path().getFileName() + " to " + target + "/; restart required.");
                } catch (Exception e) {
                    sender.sendMessage(Component.text("[VoltPur] Plugin download failed: " + e.getMessage(), NamedTextColor.RED));
                    Bukkit.getLogger().warning("[VoltPur] Plugin installer failed: " + e.getMessage());
                } finally {
                    PLUGIN_INSTALL_RUNNING.set(false);
                }
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("up") || args[0].equalsIgnoreCase("update")) {
            if (!sender.hasPermission("voltpur.admin.update") && !sender.isOp()) {
                sender.sendMessage(Component.text("No permission - voltpur.admin.update", NamedTextColor.RED));
                return true;
            }
            // /vo up list - show recent builds (New / Back, numbered 1-5+)
            if (args.length > 1 && args[1].equalsIgnoreCase("list")) {
                scheduleAsync(sender, () -> listBuilds(sender));
                return true;
            }
            // /vo up <number> [--confirm] [--clean] [--full-wipe] [--no-backup]
            String buildId = args.length > 1 ? args[1] : null;
            if (buildId == null || !buildId.matches("\\d{1,2}")) {
                sender.sendMessage(Component.text("Usage: /vo up list (show builds) | /vo up <1-5> --confirm", NamedTextColor.RED));
                return true;
            }
            if (CACHED_RUN_IDS.isEmpty()) {
                sender.sendMessage(Component.text("No builds cached. Run /vo up list first.", NamedTextColor.YELLOW));
                return true;
            }
            int idx = Integer.parseInt(buildId) - 1;
            if (idx < 0 || idx >= CACHED_RUN_IDS.size()) {
                sender.sendMessage(Component.text("Invalid number. Use /vo up list to see available builds.", NamedTextColor.RED));
                return true;
            }

            boolean confirmed = false;
            boolean clean = false;
            boolean fullWipe = false;
            boolean noBackup = false;
            for (int i = 2; i < args.length; i++) {
                String a = args[i].toLowerCase();
                if (a.equals("--confirm")) confirmed = true;
                else if (a.equals("--clean")) clean = true;
                else if (a.equals("--full-wipe")) fullWipe = true;
                else if (a.equals("--no-backup")) noBackup = true;
            }

            if (!confirmed) {
                sender.sendMessage(Component.text("=== [VoltPur Updater - Confirmation Required] ===", NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Target: build #" + (idx + 1) + " (GitHub Run " + CACHED_RUN_IDS.get(idx) + ")", NamedTextColor.AQUA));
                sender.sendMessage(Component.text("To apply this update, add --confirm to your command:", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("  /vo up " + (idx + 1) + " --confirm", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("    -> Standard update (replaces server.jar with pre-backup; keeps plugins, configs & worlds)", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /vo up " + (idx + 1) + " --clean --confirm", NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("    -> Updates jar + cleanly resets server configs (plugins & worlds kept)", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("  /vo up " + (idx + 1) + " --full-wipe --confirm", NamedTextColor.RED));
                sender.sendMessage(Component.text("    -> Wipes configs & plugins (worlds & eula kept - destructive!)", NamedTextColor.GRAY));
                return true;
            }

            buildId = CACHED_RUN_IDS.get(idx);
            final String finalBuildId = buildId;
            final boolean finalClean = clean;
            final boolean finalFullWipe = fullWipe;
            final boolean finalNoBackup = noBackup;
            sender.sendMessage(Component.text("Selected build #" + (idx + 1) + " -> run " + buildId, NamedTextColor.GREEN));
            sender.sendMessage(Component.text("[VoltPur] Starting update process...", NamedTextColor.YELLOW));
            scheduleAsync(sender, () -> doUpdate(sender, finalBuildId, finalClean, finalFullWipe, finalNoBackup));
            return true;
        }
        sender.sendMessage(Component.text("Usage: "+usageMessage, NamedTextColor.RED));
        return false;
    }

    /**
     * Runs a task OFF the main thread. Works with or without plugins.
     *
     * IMPORTANT: Paper's schedulers (AsyncScheduler AND the Bukkit scheduler)
     * REQUIRE a non-null owning plugin - passing null throws
     * "Plugin may not be null". VoltPur runs inside the server (not as a plugin),
     * and a server with 0 plugins has no owner available, so we must fall back to
     * a plain worker thread. This is safe here because the only tasks passed in
     * (listBuilds / doUpdate) do network + file IO + messaging and never touch
     * the Bukkit world API.
     */
    private void scheduleAsync(CommandSender sender, Runnable task) {
        final Runnable safe = () -> {
            try { task.run(); }
            catch (Exception e) {
                sender.sendMessage(Component.text("Error: " + e.getMessage(), NamedTextColor.RED));
                e.printStackTrace();
            }
        };
        // An ENABLED plugin can own a proper async task (never pass null).
        org.bukkit.plugin.Plugin p = org.purpurmc.purpur.VoltPurPlugin.get();
        if (p != null) {
            try {
                Bukkit.getAsyncScheduler().runNow(p, ignored -> safe.run());
                return;
            } catch (Throwable ignored) {
                try {
                    Bukkit.getScheduler().runTaskAsynchronously(p, safe);
                    return;
                } catch (Throwable ignored2) { /* fall through to a plain thread */ }
            }
        }
        // No plugin available (VoltPur inside the server, 0 plugins): plain worker
        // thread. Safe: the task only does network + file IO, no world access.
        new Thread(safe, "VoltPur-Async").start();
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
            // Parse ONLY workflow-run IDs. Every run object exposes its id inside
            // ".../actions/runs/<id>" URLs; matching that (and de-duplicating) avoids
            // catching unrelated "id" fields (actor id, repository id) that polluted
            // the old list with bogus, un-installable numbers.
            java.util.LinkedHashSet<String> runIds = new java.util.LinkedHashSet<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/actions/runs/(\\d+)").matcher(json);
            while (m.find()) runIds.add(m.group(1));
            CACHED_RUN_IDS.addAll(runIds);
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

    private void doUpdate(CommandSender sender, String buildId, boolean clean, boolean fullWipe, boolean noBackup) {
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

            // Resolve the SELECTED build's identity (run number + commit) so we install
            // EXACTLY the chosen build and can confirm it after boot.
            String headSha = "";
            String runNumber = "";
            try {
                String runDetail = httpGet("https://api.github.com/repos/" + repo + "/actions/runs/" + runId, hasToken ? token : null);
                headSha = extractJson(runDetail, "head_sha");
                runNumber = extractJsonNumber(runDetail, "run_number");
            } catch (Exception e) {
                VoltPurLogger.warn("Updater", "Could not fetch run details", e);
            }
            final String shortSha = headSha.length() >= 7 ? headSha.substring(0, 7) : headSha;

            // 1. Automatic Pre-update Backup (§7.2)
            if (!noBackup) {
                createPreUpdateBackup(sender);
            }

            if (!hasToken) {
                sender.sendMessage(Component.text("[WARN] No GitHub token - downloading build from public release...", NamedTextColor.YELLOW));
                try {
                    java.nio.file.Path tempJar = Files.createTempFile("voltpur-release-", ".jar");
                    boolean gotExact = false;
                    String exactUrl = "";
                    if (!runNumber.isEmpty() && !headSha.isEmpty()) {
                        String tag = "build-" + runNumber + "-" + headSha;
                        exactUrl = "https://github.com/" + repo + "/releases/download/" + tag + "/VoltPur-26.2.jar";
                        sender.sendMessage(Component.text("Target: build #" + runNumber + " (commit " + shortSha + "), tag " + tag, NamedTextColor.AQUA));
                        sender.sendMessage(Component.text("Downloading: " + exactUrl, NamedTextColor.YELLOW));
                        try {
                            downloadFilePublic(exactUrl, tempJar);
                            gotExact = true;
                        } catch (Exception exTag) {
                            sender.sendMessage(Component.text("[WARN] Exact-build release asset not found (" + exTag.getMessage() + "). Falling back to latest.", NamedTextColor.YELLOW));
                        }
                    }
                    String downloadedUrl = gotExact ? exactUrl : "https://github.com/" + repo + "/releases/latest/download/VoltPur-26.2.jar";
                    if (!gotExact) {
                        sender.sendMessage(Component.text("Downloading latest release: " + downloadedUrl, NamedTextColor.YELLOW));
                        downloadFilePublic(downloadedUrl, tempJar);
                    }
                    long size = Files.size(tempJar);
                    if (size < 1000000) {
                        Files.deleteIfExists(tempJar);
                        throw new Exception("Downloaded file too small (" + size + " bytes) - release may not exist yet. Set github-token.");
                    }
                    sender.sendMessage(Component.text("Downloaded jar: " + (size/1024/1024) + "MB" + (gotExact ? " (exact build #" + runNumber + ")" : " (latest)"), NamedTextColor.GREEN));

                    // 2. SHA-256 Checksum Verification (§7.3)
                    String shaUrl = downloadedUrl + ".sha256";
                    String expectedSha = null;
                    try {
                        java.nio.file.Path tempSha = Files.createTempFile("voltpur-sha-", ".sha256");
                        downloadFilePublic(shaUrl, tempSha);
                        expectedSha = Files.readString(tempSha);
                        Files.deleteIfExists(tempSha);
                    } catch (Exception exSha) {
                        sender.sendMessage(Component.text("[WARN] Could not download .sha256 checksum asset: " + exSha.getMessage(), NamedTextColor.YELLOW));
                    }
                    if (expectedSha != null && !expectedSha.trim().isEmpty()) {
                        verifySha256(tempJar, expectedSha, sender);
                    } else {
                        sender.sendMessage(Component.text("[WARN] Proceeding without remote SHA-256 verification (no .sha256 asset found on release).", NamedTextColor.YELLOW));
                    }

                    // 3. Selective wiping based on explicit flags (§7.2)
                    if (fullWipe) {
                        cleanReinstallKeepingWorlds(sender, true);
                    } else if (clean) {
                        cleanReinstallKeepingWorlds(sender, false);
                    } else {
                        sender.sendMessage(Component.text("[VoltPur] Standard update: server configs, plugins, and worlds kept intact.", NamedTextColor.GREEN));
                    }

                    Files.copy(tempJar, java.nio.file.Path.of("server.jar"), StandardCopyOption.REPLACE_EXISTING);
                    writeInstalledStamp(runNumber, runId, headSha, gotExact);
                    sender.sendMessage(Component.text("[OK] Updated to " + (gotExact ? "build #" + runNumber + " (commit " + shortSha + ")" : "latest release") + ".", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text("Restart from your panel (or /restart) to apply. After boot, the VoltPur banner confirms the installed build.", NamedTextColor.YELLOW));
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
            String expectedSha = null;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.endsWith(".sha256")) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] b = new byte[1024];
                        int len;
                        while ((len = zis.read(b)) > 0) baos.write(b, 0, len);
                        expectedSha = baos.toString(StandardCharsets.UTF_8);
                    }
                    if (name.equals("server.jar") || name.equals("VoltPur-26.2.jar") || name.equals("VoltPur.jar")) {
                        java.nio.file.Path outPath = tempDir.resolve(name);
                        Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                        if (name.equals("server.jar") || extractedJar == null) {
                            extractedJar = outPath.toString();
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

            // SHA-256 Checksum Verification
            if (expectedSha != null && !expectedSha.trim().isEmpty()) {
                verifySha256(java.nio.file.Path.of(extractedJar), expectedSha, sender);
            }

            // Selective wiping
            if (fullWipe) {
                cleanReinstallKeepingWorlds(sender, true);
            } else if (clean) {
                cleanReinstallKeepingWorlds(sender, false);
            } else {
                sender.sendMessage(Component.text("[VoltPur] Standard update: server configs, plugins, and worlds kept intact.", NamedTextColor.GREEN));
            }

            java.nio.file.Path targetJar = java.nio.file.Path.of("server.jar");
            Files.copy(java.nio.file.Path.of(extractedJar), targetJar, StandardCopyOption.REPLACE_EXISTING);
            long newSize = Files.size(targetJar);
            writeInstalledStamp(runNumber, runId, headSha, true);
            sender.sendMessage(Component.text("[OK] Updated to build #" + (runNumber.isEmpty() ? runId : runNumber) + " (commit " + shortSha + ", " + (newSize/1024/1024) + "MB).", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Restart from your panel (or /restart) to apply. After boot, the VoltPur banner confirms the installed build.", NamedTextColor.YELLOW));
            Files.deleteIfExists(tempZip);

        } catch (Exception e) {
            sender.sendMessage(Component.text("[FAIL] Update failed: " + e.getMessage(), NamedTextColor.RED));
            VoltPurLogger.error("Updater", "Update failed", e);
        }
    }

    /**
     * Automatic Pre-update Backup (§7.2):
     * Zips server configs, plugins, and root files (excluding worlds, logs, caches)
     * into backups/pre-update-<timestamp>.zip before any update or wipe.
     */
    private void createPreUpdateBackup(CommandSender sender) {
        try {
            java.io.File dir = new java.io.File("backups");
            if (!dir.exists()) dir.mkdirs();
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            java.io.File archive = new java.io.File(dir, "pre-update-" + stamp + ".zip");
            sender.sendMessage(Component.text("[VoltPur] Creating pre-update backup: " + archive.getName() + "...", NamedTextColor.YELLOW));

            int count = 0;
            byte[] buf = new byte[8192];
            try (ZipOutputStream zos = new ZipOutputStream(new java.io.FileOutputStream(archive))) {
                java.io.File root = new java.io.File(".").getAbsoluteFile();
                java.io.File[] files = root.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        String name = f.getName();
                        if (isWorldFolder(f) || name.equalsIgnoreCase("backups")
                                || name.equalsIgnoreCase("logs") || name.equalsIgnoreCase("cache")
                                || name.equalsIgnoreCase(".cache") || name.equalsIgnoreCase("libraries")
                                || name.startsWith("voltpur-update-") || name.startsWith("voltpur-release-")) {
                            continue;
                        }
                        if (f.isDirectory()) {
                            count += zipFolderRec(f, name, zos, buf);
                        } else if (f.isFile() && !name.equals("session.lock")) {
                            zipFileEntry(f, name, zos, buf);
                            count++;
                        }
                    }
                }
            }
            long mb = archive.length() / (1024 * 1024);
            sender.sendMessage(Component.text("[VoltPur] Pre-update backup created: " + archive.getPath() + " (" + mb + "MB, " + count + " files).", NamedTextColor.GREEN));
            Bukkit.getLogger().info("[VoltPur] Pre-update backup created: " + archive.getPath() + " (" + archive.length() + " bytes)");
        } catch (Exception e) {
            sender.sendMessage(Component.text("[VoltPur] Warning: Pre-update backup failed: " + e.getMessage(), NamedTextColor.YELLOW));
            VoltPurLogger.warn("Updater", "Pre-update backup failed", e);
        }
    }

    private int zipFolderRec(java.io.File folder, String base, ZipOutputStream zos, byte[] buf) {
        int c = 0;
        java.io.File[] children = folder.listFiles();
        if (children == null) return c;
        for (java.io.File child : children) {
            String path = base + "/" + child.getName();
            if (child.isDirectory()) {
                if (!isWorldFolder(child) && !child.getName().equalsIgnoreCase("cache")) {
                    c += zipFolderRec(child, path, zos, buf);
                }
            } else if (child.isFile() && !child.getName().equals("session.lock")) {
                zipFileEntry(child, path, zos, buf);
                c++;
            }
        }
        return c;
    }

    private void zipFileEntry(java.io.File file, String entryName, ZipOutputStream zos, byte[] buf) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            zos.putNextEntry(new ZipEntry(entryName));
            int r;
            while ((r = in.read(buf)) > 0) zos.write(buf, 0, r);
            zos.closeEntry();
        } catch (IOException ignored) {}
    }

    /** Computes SHA-256 hash of a file (§7.3). */
    private String computeSha256(java.nio.file.Path path) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(path)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) md.update(buf, 0, n);
        }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Verifies SHA-256 hash of a file against expected hash (§7.3). */
    private void verifySha256(java.nio.file.Path file, String expectedSha, CommandSender sender) throws Exception {
        if (expectedSha == null || expectedSha.trim().isEmpty()) {
            sender.sendMessage(Component.text("[VoltPur] Warning: No SHA-256 checksum available from release metadata.", NamedTextColor.YELLOW));
            return;
        }
        String cleanExpected = expectedSha.trim().split("\\s+")[0].toLowerCase();
        String computed = computeSha256(file).toLowerCase();
        if (!computed.equals(cleanExpected)) {
            throw new SecurityException("SHA-256 checksum mismatch! Expected: " + cleanExpected + " but got: " + computed + ". Aborting update.");
        }
        sender.sendMessage(Component.text("[OK] SHA-256 checksum verified (" + computed.substring(0, 12) + "...)", NamedTextColor.GREEN));
        Bukkit.getLogger().info("[VoltPur] Verified SHA-256 for downloaded jar: " + computed);
    }

    /**
     * CLEAN REINSTALL (§7.2):
     * Worlds, eula.txt, server.jar, voltpur.yml, and backups are ALWAYS kept.
     * plugins/ and plugin-pro/ are preserved unless wipePlugins (--full-wipe) is explicitly true.
     */
    private void cleanReinstallKeepingWorlds(CommandSender sender, boolean wipePlugins) {
        sender.sendMessage(Component.text("Clean reinstall: wiping server files (worlds, eula.txt, and " + (wipePlugins ? "" : "plugins/") + " are KEPT)...", NamedTextColor.YELLOW));
        java.io.File root = new java.io.File(".").getAbsoluteFile();
        java.io.File[] entries = root.listFiles();
        if (entries == null) {
            sender.sendMessage(Component.text("Could not list server root - skipping wipe.", NamedTextColor.RED));
            return;
        }
        int deleted = 0, kept = 0;
        java.util.List<String> keptWorlds = new java.util.ArrayList<>();
        for (java.io.File f : entries) {
            String name = f.getName();
            // Never delete: the server.jar slot, eula.txt, install stamp, voltpur.yml, backups/
            if (name.equals("server.jar") || name.equalsIgnoreCase("eula.txt")
                    || name.equalsIgnoreCase("voltpur-installed.txt")
                    || name.equalsIgnoreCase("voltpur.yml")
                    || name.equalsIgnoreCase("backups")) {
                kept++;
                continue;
            }
            if (isWorldFolder(f)) {
                kept++;
                keptWorlds.add(name);
                continue;
            }
            if (!wipePlugins && (name.equalsIgnoreCase("plugins") || name.equalsIgnoreCase("plugin-pro"))) {
                kept++;
                continue;
            }
            if (deleteRecursively(f)) deleted++;
        }
        sender.sendMessage(Component.text("Wipe complete: removed " + deleted + " item(s), kept " + kept
                + " (worlds: " + (keptWorlds.isEmpty() ? "none" : String.join(", ", keptWorlds)) + " + eula/jar/config" + (wipePlugins ? "" : "/plugins") + ").", NamedTextColor.GREEN));
    }

    /**
     * True if the folder is (or looks like) a Minecraft world, so the clean reinstall
     * NEVER deletes player worlds. We match by:
     *   - known Bukkit world names (world / world_nether / world_the_end),
     *   - a level.dat directly inside,
     *   - a dimension/region/data marker sub-folder (incl. nested level.dat).
     * The name-based match guarantees nether/end survive even if empty at wipe time.
     */
    private boolean isWorldFolder(java.io.File f) {
        if (f == null || !f.isDirectory()) return false;
        String n = f.getName().toLowerCase();
        if (n.equals("world") || n.equals("world_nether") || n.equals("world_the_end")) return true;
        if (new java.io.File(f, "level.dat").exists()) return true;                 // overworld
        java.io.File[] subs = f.listFiles(java.io.File::isDirectory);               // world_nether/DIM-1, world_the_end/DIM1
        if (subs != null) {
            for (java.io.File s : subs) {
                String sn = s.getName().toLowerCase();
                if (sn.equals("region") || sn.equals("dim-1") || sn.equals("dim1")
                        || sn.equals("dimensions") || sn.equals("playerdata") || sn.equals("entities")) return true;
                if (new java.io.File(s, "level.dat").exists()) return true;
            }
        }
        return false;
    }

    /** Extracts a JSON string value ("key":"value"). */
    private String extractJson(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    /** Extracts a JSON numeric value ("key":123). */
    private String extractJsonNumber(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Writes the install fingerprint to voltpur-installed.txt in the server root so
     * the VoltPur banner can CONFIRM, after the next boot, exactly which build is running.
     */
    private void writeInstalledStamp(String buildNumber, String runId, String sha, boolean exact) {
        try (java.io.FileWriter fw = new java.io.FileWriter("voltpur-installed.txt")) {
            fw.write("build=" + (buildNumber == null || buildNumber.isEmpty() ? "unknown" : buildNumber) + "\n");
            fw.write("run=" + (runId == null ? "" : runId) + "\n");
            fw.write("commit=" + (sha == null ? "" : sha) + "\n");
            fw.write("exact=" + exact + "\n");
            fw.write("installed-at=" + new java.util.Date() + "\n");
        } catch (Exception e) {
            VoltPurLogger.warn("Updater", "Could not write install stamp", e);
        }
    }

    /** Recursively deletes a file or directory. Returns true if the top entry was removed. */
    private boolean deleteRecursively(java.io.File f) {
        try {
            if (f.isDirectory()) {
                java.io.File[] kids = f.listFiles();
                if (kids != null) for (java.io.File k : kids) deleteRecursively(k);
            }
            return f.delete();
        } catch (Exception e) {
            return false;
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
