package org.purpurmc.purpur.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Location;

public class VoltPurCommand extends Command {
    public VoltPurCommand(String name) {
        super(name);
        this.description = "VoltPur main command - shows version and modules";
        this.usageMessage = "/voltpur [version|modules|reload|info]";
        this.setPermission(null); // Allow everyone to use version/modules, permission checked inside for reload
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            return Stream.of("version", "modules", "reload", "info")
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
            return true;
        }
        if (args[0].equalsIgnoreCase("modules")) {
            sender.sendMessage(Component.text("=== ⚡ VoltPur Modules (" + VoltPur.MODULES.length + ") ===", NamedTextColor.GOLD));
            for (int i=0;i<VoltPur.MODULES.length;i++) {
                sender.sendMessage(Component.text((i+1)+". "+VoltPur.MODULES[i]+" - §aENABLED", NamedTextColor.GREEN));
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
        sender.sendMessage(Component.text("Usage: "+usageMessage, NamedTextColor.RED));
        return false;
    }
}
