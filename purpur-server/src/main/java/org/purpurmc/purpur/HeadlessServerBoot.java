package org.purpurmc.purpur;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.purpurmc.purpur.ai.AiBudget;
import org.purpurmc.purpur.ai.AiClient;
import org.purpurmc.purpur.ai.AiRouter;
import org.purpurmc.purpur.plus.PlusBootstrap;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * HeadlessServerBoot - Entrypoint for headless CI verification and standalone validation.
 * Initializes all VoltPur subsystems:
 *   - Configuration (VoltPurConfig)
 *   - Modules registry (VoltPurModules)
 *   - Hardware detection & auto-tune (VoltPurHardware, VoltPurTuning)
 *   - Adaptive Autotune controller (VoltPurAutotune)
 *   - External optimizations & pregen (VoltPurIntegrations)
 *   - Plus plugin loader (PlusBootstrap)
 *   - AI layer (AiRouter, AiBudget, AiClient)
 */
public final class HeadlessServerBoot {

    private static final CountDownLatch STOP_LATCH = new CountDownLatch(1);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        // Initialize standalone Bukkit server stub for logging & metric services
        initServerStub();

        System.out.println("==================================================");
        System.out.println("⚡ VoltPur Headless Server Boot");
        System.out.println("==================================================");

        // 1. Config
        VoltPurConfig.init();

        // 2. Hardware detection & Tuning
        VoltPurHardware.detect();
        VoltPurHardware.reportToLogAndFile(false);
        VoltPurTuning.onServerStart();

        // 3. Autotune Adaptive Controller (§4)
        VoltPurAutotune.init();

        // 4. Integrations & Pregen (§4)
        VoltPurIntegrations.detectExternalOptimizations();
        VoltPurIntegrations.tickPregen();

        // 5. Plus Module System (§6)
        PlusBootstrap.init();

        // 6. Practical AI Layer (§5)
        AiRouter.RouteInfo testRoute = AiRouter.resolveRoute();
        System.out.println("[VoltPur-AI] Initialized practical AI layer with provider: " + testRoute.provider.name() + " (model: " + testRoute.model + ")");
        System.out.println("[VoltPur-AI] Monthly budget: $" + String.format("%.2f USD", VoltPurConfig.aiMonthlyBudgetUsd)
                + " (spent: $" + String.format("%.4f", AiBudget.getSpentUsd()) + ", paid confirmed: " + VoltPurConfig.aiPaidConfirmed + ")");

        // 7. Modules summary
        System.out.println("[VoltPur] Active modules: " + VoltPurModules.activeCount()
                + " | Partial: " + VoltPurModules.partialCount()
                + " | Planned: " + VoltPurModules.plannedCount()
                + " | Total: " + VoltPurModules.totalCount());

        long elapsed = System.currentTimeMillis() - startTime;
        double elapsedSec = elapsed / 1000.0;

        // Register clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[VoltPur] Initiating graceful shutdown...");
            PlusBootstrap.unloadAll();
            AiClient.shutdown();
            System.out.println("[VoltPur] Graceful shutdown completed cleanly.");
            STOP_LATCH.countDown();
        }, "VoltPur-Shutdown-Hook"));

        System.out.println("Done (" + String.format("%.3fs", elapsedSec) + ")! For help, type \"help\"");

        try {
            STOP_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void initServerStub() {
        try {
            Logger logger = Logger.getLogger("Minecraft");
            Server proxy = (Server) Proxy.newProxyInstance(
                    Server.class.getClassLoader(),
                    new Class<?>[]{Server.class},
                    (p, method, mArgs) -> {
                        String name = method.getName();
                        if ("getLogger".equals(name)) return logger;
                        if ("getName".equals(name)) return "VoltPur-Headless";
                        if ("getVersion".equals(name)) return "26.2";
                        if ("getWorlds".equals(name)) return Collections.emptyList();
                        if ("getOnlinePlayers".equals(name)) return Collections.emptyList();
                        if ("getMaxPlayers".equals(name)) return 20;
                        if ("getTPS".equals(name)) return new double[]{20.0, 20.0, 20.0};
                        if ("getTickTimes".equals(name)) return new long[]{20_000_000L};
                        if ("getServer".equals(name)) return p;
                        Class<?> returnType = method.getReturnType();
                        if (returnType == boolean.class) return false;
                        if (returnType == int.class) return 0;
                        if (returnType == long.class) return 0L;
                        if (returnType == double.class) return 0.0;
                        return null;
                    }
            );
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, proxy);
        } catch (Throwable t) {
            System.err.println("[VoltPur] Warning: could not initialize Bukkit server stub: " + t.getMessage());
        }
    }
}
