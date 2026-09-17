package org.bukkit.plugin;
public abstract class PluginBase implements Plugin {
    @Override public String getName() { return "Stub"; }
    @Override public abstract boolean isEnabled();
}
