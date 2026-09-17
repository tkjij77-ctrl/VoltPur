package org.bukkit.command;
public interface CommandSender {
    void sendMessage(String message);
    void sendMessage(net.kyori.adventure.text.Component message);
    boolean isOp();
    boolean hasPermission(String name);
}
