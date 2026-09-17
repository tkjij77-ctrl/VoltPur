package org.bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
public class BukkitScheduler {
    public BukkitTask runTask(Plugin plugin, Runnable task) { return new NoopTask(); }
    public int scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period) { return 0; }
    public BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) { return new NoopTask(); }
    public void runTaskAsynchronously(Plugin plugin, Runnable task) { }
    public BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) { return new NoopTask(); }
    public void cancelTask(int id) { }
    static class NoopTask implements BukkitTask {}
}
