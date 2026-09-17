package org.bukkit.command;
import org.bukkit.Location;
import java.util.List;
public abstract class Command {
    protected String description = "";
    protected String usageMessage = "";
    private String permission;
    private List<String> aliases = List.of();
    public Command(String name) {}
    public abstract boolean execute(CommandSender sender, String commandLabel, String[] args);
    public abstract List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) throws IllegalArgumentException;
    public void setPermission(String permission) { this.permission = permission; }
    public String getPermission() { return permission; }
    public void setAliases(List<String> aliases) { this.aliases = aliases; }
    public List<String> getAliases() { return aliases; }
    public boolean testPermission(CommandSender sender) { return true; }
}
