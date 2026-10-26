package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.lang.reflect.Field;

public final class VoltPurTestHelper {

    private VoltPurTestHelper() {}

    public static void setMockServer(Server server) {
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set mock server", e);
        }
    }

    public static void clearMockServer() {
        setMockServer(null);
    }
}
