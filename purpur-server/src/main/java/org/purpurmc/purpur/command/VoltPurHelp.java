package org.purpurmc.purpur.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurModules;

import java.util.Collections;
import java.util.List;

/**
 * /voltpur help - a complete, honest command reference.
 *
 * It states which flags are opt-in and what the current safety posture is, so an
 * admin never has to guess whether VoltPur is allowed to touch their files.
 */
public class VoltPurHelp extends Command {

    public VoltPurHelp(String name) {
        super(name);
        this.description = "VoltPur command reference";
        this.usageMessage = "/voltpur help";
        this.setPermission(null);
        this.setAliases(List.of("help", "?"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        sender.sendMessage("§6━━━━━━━━━━ §e⚡ VoltPur §6━━━━━━━━━━");
        sender.sendMessage("§7v" + VoltPur.VERSION + " §8• §7MC " + VoltPur.MC_VERSION
                + " §8• §7modules: §a" + VoltPurModules.activeCount() + " §7implemented / §e"
                + VoltPurModules.partialCount() + " §7partial / §c" + VoltPurModules.plannedCount() + " §7planned");
        sender.sendMessage("");
        sender.sendMessage("§b/voltpur version §7- version, installed build stamp");
        sender.sendMessage("§b/voltpur modules §7- implementation status + LIVE health (runs/fails per module)");
        sender.sendMessage("§b/voltpur status §7- TPS, MSPT, worlds, files, installed build");
        sender.sendMessage("§b/voltpur worlds §7- loaded worlds with entity/chunk counts");
        sender.sendMessage("§b/voltpur hardware §7- real CPU/RAM/quota + JVM flags for THIS machine");
        sender.sendMessage("§b/voltpur flags §7- ready-to-paste JVM start command");
        sender.sendMessage("§b/voltpur benchmark §7- measured TPS/MSPT/entities/chunks/heap (OP, 30s cooldown)");
        sender.sendMessage("§b/voltpur optimize §7- writes tuned server.properties/spigot.yml (OP, needs auto-tune=true)");
        sender.sendMessage("§b/voltpur reload §7- reload voltpur.yml (OP)");
        sender.sendMessage("");
        sender.sendMessage("§6Updates §7(verified + reversible):");
        sender.sendMessage("§b/vo up list §7- recent builds (cached to disk, survives restarts)");
        sender.sendMessage("§b/vo up <number> §7- download + verify (SHA-256) and show the plan");
        sender.sendMessage("§b/vo up confirm §7- apply the staged build  §8|  §b/vo up cancel §7- abort");
        sender.sendMessage("§b/vo rollback §7- restore the previous server jar");
        sender.sendMessage("");
        sender.sendMessage("§b/vo in <url> <plugins|plugin-pro> §7- download a plugin jar (OP, restart to load)");
        sender.sendMessage("§b/padmin start|stop|status §7- local WebUI (loopback + Basic Auth, read-only)");
        sender.sendMessage("");
        sender.sendMessage("§7Safety posture: §fitem-limiter=" + onOff(VoltPurConfig.itemLimiterEnabled)
                + " §foptimizer=" + onOff(VoltPurConfig.optimizerEnabled)
                + " §fpadmin=" + onOff(VoltPurConfig.padminEnabled)
                + " §fdestructive-reinstall=" + onOff(VoltPurConfig.updateCleanReinstall)
                + " §fchecksum=" + (VoltPurConfig.updateRequireChecksum ? "required" : "optional"));
        sender.sendMessage("§7Policy: §fPOLICY.md §7• Security: §fSECURITY.md §7• Reports: §flogs/voltpur-*.txt");
        return true;
    }

    private static String onOff(boolean value) {
        return value ? "§aON" : "§coff";
    }
}
