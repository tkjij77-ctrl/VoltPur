package org.purpurmc.purpur.command;

import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurModules;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.Location;

import java.util.Collections;
import java.util.List;

/**
 * VoltPur Help - a complete, friendly reference for every VoltPur command.
 * This makes the software self-documenting: admins get a full menu of what the
 * fork offers and how to use it, all in one place.
 */
public class VoltPurHelp extends Command {

    public VoltPurHelp(String name) {
        super(name);
        this.description = "VoltPur command reference";
        this.usageMessage = "/voltpur help";
        this.setPermission(null);
        this.setAliases(List.of("help", "?" ));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        sender.sendMessage("§6━━━━━━━━━━ §e⚡ VoltPur §6━━━━━━━━━━");
        sender.sendMessage("§7Command Reference §8• §7v" + VoltPur.VERSION);
        sender.sendMessage("§7Real modules: §a" + VoltPurModules.activeCount() + "§7/§e" + VoltPurModules.totalCount() + "§7 ACTIVE (honest status)");
        sender.sendMessage("");
        sender.sendMessage("§b/voltpur version §7- version & MC info");
        sender.sendMessage("§b/voltpur modules §7- honest module status (ACTIVE/PARTIAL/PLANNED)");
        sender.sendMessage("§b/voltpur status §7- stability: worlds, files, TPS");
        sender.sendMessage("§b/voltpur worlds §7- list loaded worlds");
        sender.sendMessage("§b/voltpur hardware §7- device compatibility + recommended JVM flags");
        sender.sendMessage("§b/voltpur flags §7- recommended JVM start command for THIS machine");
        sender.sendMessage("§b/voltpur optimize §7- apply hardware-tuned server.properties (OP)");
        sender.sendMessage("§b/voltpur benchmark §7- live measured TPS/MSPT/entities/chunks/heap/hoppers");
        sender.sendMessage("§b/voltpur reload §7- reload voltpur.yml (OP)");
        sender.sendMessage("§b/vo in <plugin-url> <plugins|plugin-pro> §7- securely download a plugin JAR (OP; restart required)");
        sender.sendMessage("§b/vo up §7- update server.jar from latest build (OP)");
        sender.sendMessage("§b/vo up <buildId> §7- update to a specific build");
        sender.sendMessage("§b/padmin §7- start PAdmin WebUI (localhost:25567)");
        sender.sendMessage("");
        sender.sendMessage("§7Config: §evoltpur.yml§7 (modules.hardware.*, modules.performance.*)");
        sender.sendMessage("§7Reports: §elogs/voltpur-hardware-report.txt§7, §elogs/voltpur-benchmark.txt§7, §elogs/voltpur-tuning.txt§7");
        return true;
    }
}
