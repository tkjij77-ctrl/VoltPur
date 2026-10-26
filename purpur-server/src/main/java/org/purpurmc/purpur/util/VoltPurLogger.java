package org.purpurmc.purpur.util;

import org.bukkit.Bukkit;
import org.purpurmc.purpur.VoltPurModules;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * VoltPur unified error logging & stability policy (§3.1).
 *
 * Rules:
 * - No empty catch blocks.
 * - Standardized log format: [VoltPur-<Module>] <what failed>: <message>
 * - Anti-spam rate limiting: identical error messages are logged at most once per 5 minutes.
 * - Auto-degradation: 3 consecutive failures mark the module DEGRADED in VoltPurModules.
 * - Safe logging fallback when Bukkit server is initializing or offline.
 */
public final class VoltPurLogger {
    private static final long COOLDOWN_MS = 5 * 60 * 1000L; // 5 minutes anti-spam
    private static final ConcurrentHashMap<String, Long> LAST_LOGGED = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> FAIL_COUNTERS = new ConcurrentHashMap<>();
    private static final Logger FALLBACK_LOGGER = Logger.getLogger("VoltPur");

    private VoltPurLogger() {}

    /**
     * Logs an error and tracks consecutive failures for automatic degradation.
     */
    public static void error(String module, String whatFailed, Throwable t) {
        String msg = extractMessage(t);
        logRateLimited(module, whatFailed, msg);

        int fails = FAIL_COUNTERS.computeIfAbsent(module, k -> new AtomicInteger(0)).incrementAndGet();
        if (fails >= 3) {
            VoltPurModules.setStatus(module, VoltPurModules.Status.DEGRADED);
            logSevere("[VoltPur-" + module + "] Module automatically marked DEGRADED after " + fails + " consecutive errors.");
        }
    }

    /**
     * Logs a warning without triggering module degradation.
     */
    public static void warn(String module, String whatFailed, Throwable t) {
        String msg = extractMessage(t);
        logRateLimited(module, whatFailed, msg);
    }

    /**
     * Logs a plain warning message with rate limiting.
     */
    public static void warn(String module, String whatFailed, String message) {
        logRateLimited(module, whatFailed, message);
    }

    /**
     * Logs an info message safely whether Bukkit is initialized or not.
     */
    public static void info(String module, String message) {
        String fullMessage = "[VoltPur-" + module + "] " + message;
        try {
            if (Bukkit.getServer() != null) {
                Bukkit.getLogger().info(fullMessage);
            } else {
                FALLBACK_LOGGER.info(fullMessage);
            }
        } catch (Throwable ignored) {
            FALLBACK_LOGGER.info(fullMessage);
        }
    }

    /**
     * Resets the consecutive failure counter upon a successful execution.
     */
    public static void recordSuccess(String module) {
        AtomicInteger counter = FAIL_COUNTERS.get(module);
        if (counter != null && counter.get() > 0) {
            counter.set(0);
        }
    }

    private static String extractMessage(Throwable t) {
        if (t == null) return "unknown error";
        String msg = t.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }

    private static void logRateLimited(String module, String whatFailed, String message) {
        String key = module + ":" + whatFailed;
        long now = System.currentTimeMillis();
        Long last = LAST_LOGGED.get(key);
        if (last == null || (now - last) >= COOLDOWN_MS) {
            LAST_LOGGED.put(key, now);
            String fullMessage = "[VoltPur-" + module + "] " + whatFailed + ": " + message;
            try {
                if (Bukkit.getServer() != null) {
                    Bukkit.getLogger().warning(fullMessage);
                } else {
                    FALLBACK_LOGGER.warning(fullMessage);
                }
            } catch (Throwable ignored) {
                FALLBACK_LOGGER.warning(fullMessage);
            }
        }
    }

    private static void logSevere(String message) {
        try {
            if (Bukkit.getServer() != null) {
                Bukkit.getLogger().severe(message);
            } else {
                FALLBACK_LOGGER.severe(message);
            }
        } catch (Throwable ignored) {
            FALLBACK_LOGGER.severe(message);
        }
    }
}
