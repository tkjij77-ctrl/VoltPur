package org.purpurmc.purpur.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurBenchmark;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurGuard;
import org.purpurmc.purpur.VoltPurHardware;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.VoltPurPlugin;
import org.purpurmc.purpur.VoltPurTuning;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * /voltpur (alias /vo) - diagnostics, safe tuning and the hardened updater.
 *
 * Hardening notes for this file:
 *  - The heavy benchmark cannot be spammed: permission + 30s cooldown.
 *  - Update flow is two-phase (stage -> confirm) and runs through VoltPurUpdater,
 *    which verifies checksums and never wipes the server by default.
 *  - Async work is owned by the internal plugin (never a third-party plugin, never
 *    a null owner) and every failure is recorded by VoltPurGuard instead of being
 *    swallowed.
 */
public class VoltPurCommand extends Command {

    private static final long BENCHMARK_COOLDOWN_MS = 30_000L;
    private static final AtomicBoolean PLUGIN_INSTALL_RUNNING = new AtomicBoolean(false);
    private static final AtomicLong LAST_BENCHMARK = new AtomicLong(0L);

    public VoltPurCommand(String name) {
        super(name);
        this.description = "VoltPur - diagnostics, safe tuning, verified updates";
        this.usageMessage = "/voltpur <help|version|modules|status|worlds|hardware|flags|benchmark|optimize|reload|in|up|rollback>";
        this.setPermission(null); // per-subcommand checks below (see POLICY.md)
        this.setAliases(List.of("vo"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            return Stream.of("help", "version", "modules", "status", "worlds", "hardware", "flags",
                            "benchmark", "optimize", "reload", "in", "up", "rollback")
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("up") || args[0].equalsIgnoreCase("update"))) {
            return Stream.of("list", "confirm", "cancel", "latest")
                    .filter(option -> option.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("in")) {
            return Stream.of("plugins", "plugin-pro")
                    .filter(option -> option.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        final String sub = args.length == 0 ? "help" : args[0].toLowerCase();

        switch (sub) {
            case "help", "?", "commands" -> {
                new VoltPurHelp("voltpur").execute(sender, label, new String[]{});
                return true;
            }
            case "version", "info" -> {
                sender.sendMessage(Component.text("[VoltPur] " + VoltPur.VERSION + " | MC " + VoltPur.MC_VERSION, NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Implemented modules: " + VoltPurModules.activeCount()
                        + " ACTIVE | " + VoltPurModules.partialCount() + " PARTIAL | "
                        + VoltPurModules.plannedCount() + " PLANNED (honest registry)", NamedTextColor.YELLOW));
                for (String line : installedStampLines()) {
                    sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
                }
                sender.sendMessage(Component.text("/voltpur modules shows live health per module.", NamedTextColor.AQUA));
                return true;
            }
            case "modules" -> {
                printModules(sender);
                return true;
            }
            case "status" -> {
                printStatus(sender);
                return true;
            }
            case "worlds" -> {
                sender.sendMessage(Component.text("Worlds (" + Bukkit.getWorlds().size() + "):", NamedTextColor.GOLD));
                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    sender.sendMessage(Component.text("  " + world.getName() + " - " + world.getEnvironment()
                            + " - E:" + world.getEntities().size() + " C:" + world.getLoadedChunks().length, NamedTextColor.GRAY));
                }
                return true;
            }
            case "hardware" -> {
                VoltPurHardware.detect();
                for (String line : VoltPurHardware.hardwareReport()) {
                    sender.sendMessage(Component.text(line, NamedTextColor.AQUA));
                }
                sender.sendMessage(Component.text("Report file: logs/voltpur-hardware-report.txt", NamedTextColor.GRAY));
                return true;
            }
            case "flags", "jvm" -> {
                VoltPurHardware.detect();
                sender.sendMessage(Component.text("=== Recommended JVM flags for THIS machine ===", NamedTextColor.GOLD));
                sender.sendMessage(Component.text(VoltPurHardware.recommendedJvmArgs(), NamedTextColor.GREEN));
                if (VoltPurHardware.isContainer()) {
                    sender.sendMessage(Component.text("Container quota detected: "
                            + VoltPurHardware.getEffectiveRamMB() + " MB RAM, " + VoltPurHardware.getEffectiveCores()
                            + " cores (recommendations respect the quota, not the host machine).", NamedTextColor.GRAY));
                }
                return true;
            }
            case "benchmark", "perf" -> {
                return runBenchmark(sender);
            }
            case "optimize", "tune" -> {
                if (!allowed(sender, "voltpur.admin.tune")) {
                    sender.sendMessage(Component.text("No permission (voltpur.admin.tune or OP).", NamedTextColor.RED));
                    return true;
                }
                if (!VoltPurConfig.hardwareAutoTune) {
                    sender.sendMessage(Component.text("Refusing to change server files: modules.hardware.auto-tune=false.", NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("Set it to true in voltpur.yml and restart, then run /voltpur optimize.", NamedTextColor.GRAY));
                    return true;
                }
                sender.sendMessage(Component.text("Writing hardware-tuned settings (backups are taken first)...", NamedTextColor.YELLOW));
                boolean applied = VoltPurTuning.applyServerProperties();
                boolean hopper = VoltPurTuning.applyOptimizations();
                VoltPurTuning.writeTuningSheet();
                sender.sendMessage(Component.text("server.properties updated: " + applied
                        + " | spigot.yml hopper-check updated: " + hopper, NamedTextColor.GREEN));
                sender.sendMessage(Component.text("These files are read at startup - restart to take effect.", NamedTextColor.YELLOW));
                return true;
            }
            case "reload" -> {
                if (!allowed(sender, "voltpur.admin.reload")) {
                    sender.sendMessage(Component.text("No permission (voltpur.admin.reload or OP).", NamedTextColor.RED));
                    return true;
                }
                VoltPurConfig.init();
                sender.sendMessage(Component.text("voltpur.yml reloaded. Modules that read config at startup need a restart.", NamedTextColor.GREEN));
                return true;
            }
            case "in", "install" -> {
                return pluginInstall(sender, args);
            }
            case "up", "update" -> {
                return update(sender, args);
            }
            case "rollback" -> {
                if (!allowed(sender, "voltpur.admin.update")) {
                    sender.sendMessage(Component.text("No permission (voltpur.admin.update or OP).", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("[VoltPur] Restoring the previous server jar...", NamedTextColor.YELLOW));
                runAsync("rollback", () -> VoltPurUpdater.rollback(sender));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Usage: " + usageMessage, NamedTextColor.RED));
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ subcommands

    private void printModules(CommandSender sender) {
        sender.sendMessage(Component.text("=== VoltPur modules (implementation + LIVE health) ===", NamedTextColor.GOLD));
        for (String name : VoltPurModules.all().keySet()) {
            sender.sendMessage(Component.text("  " + VoltPurModules.line(name), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("      " + VoltPurGuard.healthLine(name), NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(Component.text("Implemented: " + VoltPurModules.activeCount() + " ACTIVE | "
                + VoltPurModules.partialCount() + " PARTIAL | " + VoltPurModules.plannedCount() + " PLANNED", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Safe defaults right now: item-limiter=" + onOff(VoltPurConfig.itemLimiterEnabled)
                + " optimizer=" + onOff(VoltPurConfig.optimizerEnabled)
                + " padmin=" + onOff(VoltPurConfig.padminEnabled)
                + " destructive-reinstall=" + onOff(VoltPurConfig.updateCleanReinstall)
                + " update-checksum=" + (VoltPurConfig.updateRequireChecksum ? "required" : "optional"), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Health comes from VoltPurGuard: runs / skipped / fails / last run.", NamedTextColor.GRAY));
    }

    private void printStatus(CommandSender sender) {
        sender.sendMessage(Component.text("=== VoltPur status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Version: " + VoltPur.VERSION + " | MC " + VoltPur.MC_VERSION, NamedTextColor.YELLOW));
        double[] tps = Bukkit.getServer().getTPS();
        sender.sendMessage(Component.text(String.format("TPS: %.2f / %.2f / %.2f | MSPT avg: %.2f ms",
                tps[0], tps[1], tps[2], Bukkit.getServer().getAverageTickTime()), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Worlds: " + Bukkit.getWorlds().size(), NamedTextColor.AQUA));
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            sender.sendMessage(Component.text("  - " + world.getName() + " (" + world.getEnvironment() + ")", NamedTextColor.GRAY));
        }
        File[] checks = {
                new File("server.properties"), new File("bukkit.yml"), new File("purpur.yml"),
                new File("voltpur.yml"), new File("plugins"), new File("world")
        };
        int present = 0;
        for (File file : checks) if (file.exists()) present++;
        sender.sendMessage(Component.text("Files: " + present + "/" + checks.length + " present", NamedTextColor.AQUA));
        for (String line : installedStampLines()) {
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    private List<String> installedStampLines() {
        try {
            Path stamp = Path.of("voltpur-installed.txt");
            if (!Files.isRegularFile(stamp)) {
                return List.of("Installed build: not tracked (no voltpur-installed.txt - updated manually?)");
            }
            String build = "?", sha = "?", verified = "?";
            for (String line : Files.readAllLines(stamp, StandardCharsets.UTF_8)) {
                if (line.startsWith("build=")) build = line.substring(6).trim();
                else if (line.startsWith("commit=")) sha = line.substring(7).trim();
                else if (line.startsWith("checksum-verified=")) verified = line.substring(18).trim();
            }
            String shortSha = sha.length() >= 7 ? sha.substring(0, 7) : sha;
            return List.of("Installed build: #" + build + " (commit " + shortSha + ", checksum verified: " + verified + ")");
        } catch (Exception e) {
            return List.of("Installed build: unreadable (" + e.getMessage() + ")");
        }
    }

    private boolean runBenchmark(CommandSender sender) {
        if (!allowed(sender, "voltpur.admin.benchmark")) {
            sender.sendMessage(Component.text("No permission (voltpur.admin.benchmark or OP).", NamedTextColor.RED));
            return true;
        }
        long now = System.currentTimeMillis();
        long last = LAST_BENCHMARK.get();
        if (now - last < BENCHMARK_COOLDOWN_MS) {
            long wait = (BENCHMARK_COOLDOWN_MS - (now - last)) / 1000L + 1L;
            sender.sendMessage(Component.text("Benchmark is rate limited - try again in " + wait + "s "
                    + "(it scans loaded chunks and can be expensive).", NamedTextColor.YELLOW));
            return true;
        }
        LAST_BENCHMARK.set(now);
        sender.sendMessage(Component.text("Measuring live performance...", NamedTextColor.YELLOW));
        for (String line : VoltPurBenchmark.snapshot("manual")) {
            sender.sendMessage(Component.text(line, NamedTextColor.AQUA));
        }
        VoltPurBenchmark.record("manual");
        sender.sendMessage(Component.text("Snapshot appended to logs/voltpur-benchmark.txt", NamedTextColor.GRAY));
        return true;
    }

    private boolean pluginInstall(CommandSender sender, String[] args) {
        if (!allowed(sender, "voltpur.admin.install")) {
            sender.sendMessage(Component.text("No permission (voltpur.admin.install or OP).", NamedTextColor.RED));
            return true;
        }
        if (args.length != 3 || !(args[2].equals("plugins") || args[2].equals("plugin-pro"))) {
            sender.sendMessage(Component.text("Usage: /vo in <plugin-url> <plugins|plugin-pro>", NamedTextColor.RED));
            return true;
        }
        if (!PLUGIN_INSTALL_RUNNING.compareAndSet(false, true)) {
            sender.sendMessage(Component.text("A plugin download is already running.", NamedTextColor.YELLOW));
            return true;
        }
        final String url = args[1];
        final String target = args[2];
        sender.sendMessage(Component.text("[VoltPur] Downloading plugin to " + target + "/ (validated jar download)...", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("[VoltPur] Note: a plugin runs with full server privileges - this validates the file type, not its trustworthiness.", NamedTextColor.GRAY));
        runAsync("plugin-install", () -> {
            try {
                PluginJarInstaller.Result result = PluginJarInstaller.install(url, target);
                sender.sendMessage(Component.text("[VoltPur] Saved " + result.path().getFileName()
                        + " (" + (result.bytes() / 1024) + " KiB) in " + target + "/.", NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Restart the server to load it (not loaded automatically).", NamedTextColor.GOLD));
            } catch (Exception failure) {
                // Never log the raw URL: signed download links carry tokens in the query string.
                String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                sender.sendMessage(Component.text("[VoltPur] Install failed for " + target + "/: " + reason, NamedTextColor.RED));
                Bukkit.getLogger().warning("[VoltPur] Plugin install into " + target + "/ failed: " + reason);
            } finally {
                PLUGIN_INSTALL_RUNNING.set(false);
            }
        });
        return true;
    }

    private boolean update(CommandSender sender, String[] args) {
        if (!allowed(sender, "voltpur.admin.update")) {
            sender.sendMessage(Component.text("No permission (voltpur.admin.update or OP).", NamedTextColor.RED));
            return true;
        }
        String action = args.length > 1 ? args[1] : "";

        if (action.equalsIgnoreCase("confirm")) {
            if (!VoltPurUpdater.hasPending()) {
                sender.sendMessage(Component.text("Nothing staged. Use /vo up <number> first.", NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("[VoltPur] Applying staged update: " + VoltPurUpdater.pendingDescription(), NamedTextColor.YELLOW));
            runAsync("update-confirm", () -> VoltPurUpdater.confirm(sender));
            return true;
        }
        if (action.equalsIgnoreCase("cancel")) {
            VoltPurUpdater.cancel(sender);
            return true;
        }
        if (action.equalsIgnoreCase("list")) {
            runAsync("update-list", () -> VoltPurUpdater.listBuilds(sender));
            return true;
        }
        if (action.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /vo up list | /vo up <number> | /vo up confirm | /vo up cancel | /vo rollback", NamedTextColor.RED));
            if (VoltPurUpdater.hasPending()) {
                sender.sendMessage(Component.text("A build is staged: " + VoltPurUpdater.pendingDescription()
                        + " - run /vo up confirm to apply it.", NamedTextColor.AQUA));
            }
            return true;
        }
        if (action.equalsIgnoreCase("latest") || action.matches("\\d{1,3}")) {
            sender.sendMessage(Component.text("[VoltPur] Staging update (download + checksum verification)...", NamedTextColor.YELLOW));
            runAsync("update-stage", () -> VoltPurUpdater.prepare(sender, action.equalsIgnoreCase("latest") ? null : action));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /vo up list | /vo up <number> | /vo up confirm | /vo up cancel", NamedTextColor.RED));
        return true;
    }

    // ------------------------------------------------------------------ plumbing

    private static String onOff(boolean value) {
        return value ? "ON" : "off";
    }

    /**
     * OP or an explicit permission node. The console is OP by definition, so this
     * single check covers players, the console and command blocks - and it can no
     * longer be bypassed by a sender type we forgot about.
     */
    private static boolean allowed(CommandSender sender, String permission) {
        return sender.isOp() || sender.hasPermission(permission);
    }

    /**
     * Runs work off the main thread. The owner is VoltPur's internal plugin (never
     * null, never a third-party plugin). If the scheduler is unavailable for any
     * reason we fall back to a daemon worker, which is safe because every payload
     * here performs network + file IO only - never world access.
     */
    private void runAsync(String label, Runnable task) {
        Runnable guarded = () -> VoltPurGuard.run("Async:" + label, task);
        try {
            Bukkit.getScheduler().runTaskAsynchronously(VoltPurPlugin.get(), guarded);
            return;
        } catch (Throwable schedulerUnavailable) {
            Bukkit.getLogger().warning("[VoltPur] Async scheduler unavailable (" + schedulerUnavailable.getMessage()
                    + ") - using a worker thread for " + label);
        }
        Thread worker = new Thread(guarded, "VoltPur-" + label);
        worker.setDaemon(true);
        worker.start();
    }
}
