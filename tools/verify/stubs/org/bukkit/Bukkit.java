package org.bukkit;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
public class Bukkit {
    private static final Logger LOGGER = Logger.getLogger("VoltPurVerify");
    private static final BukkitScheduler SCHEDULER = new BukkitScheduler();
    private static final Server SERVER = new Server();
    public static Logger getLogger() { return LOGGER; }
    public static BukkitScheduler getScheduler() { return SCHEDULER; }
    public static Server getServer() { return SERVER; }
    /** Mirrors Paper's Bukkit.getMinecraftVersion() - the value the real server reports. */
    public static String getMinecraftVersion() { return "26.2"; }
    public static List<World> getWorlds() { return List.of(); }
    public static Collection<Player> getOnlinePlayers() { return List.of(); }
    public static void broadcast(net.kyori.adventure.text.Component message) { }
    public static CommandSender getConsoleSender() { return new Console(); }
    public static boolean dispatchCommand(CommandSender sender, String commandLine) { return true; }
    public static World getWorld(String name) { return null; }
    private static class Console implements CommandSender {
        @Override public void sendMessage(String message) { }
        @Override public void sendMessage(net.kyori.adventure.text.Component message) { }
        @Override public boolean isOp() { return true; }
        @Override public boolean hasPermission(String name) { return true; }
    }
}
