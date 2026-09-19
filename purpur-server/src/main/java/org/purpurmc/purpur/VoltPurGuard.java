package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * VoltPur Guard - the honest execution wrapper every scheduled module must use.
 *
 * WHY THIS EXISTS
 * ---------------
 * Before this class, scheduled work looked like this:
 *
 *     try { performCleanup(); } catch (Exception ignored) {}
 *
 * A module could die on the first tick and keep being reported as "ACTIVE"
 * forever, because nobody recorded the failure and nothing was logged. The
 * module registry was therefore honest about intent and blind about reality.
 *
 * Guard turns failure into DATA:
 *   - counts runs / failures / skips per module,
 *   - remembers the last error (class + message) and when it happened,
 *   - logs the FIRST failure at WARNING (with stack), then rate-limits repeats
 *     to at most one per 60s per module so a broken loop cannot flood the log,
 *   - exposes everything through /voltpur modules, the PAdmin snapshot and
 *     logs/voltpur-modules.json.
 *
 * CIRCUIT BREAKER (why counting was not enough)
 * ---------------------------------------------
 * Counting failures made a dead module visible, but it stayed dead IN THE LOOP:
 * a module that throws on every tick kept being called forever, costing CPU and
 * writing a warning every 60s until the operator noticed. VoltPur is supposed to
 * keep the server running, so a module that fails repeatedly is now taken out of
 * its schedule by itself, loudly and reversibly:
 *
 *   - N consecutive failures (default 8) open the breaker for that module,
 *   - further calls return immediately and are counted as "short-circuited",
 *   - one clear log line explains what happened and how to re-enable,
 *   - any success resets the counter, so a flaky-but-recovering module is left alone,
 *   - CRITICAL modules are never taken out of the loop (see CRITICAL below),
 *   - /voltpur reload closes every breaker.
 *
 * A module that cannot run is a smaller problem than a module that runs and
 * fails forever - and both are smaller than a silent one.
 *
 * Rule for contributors: never wrap VoltPur work in an empty catch. Use
 * VoltPurGuard.run("ModuleName", ...) instead.
 */
public final class VoltPurGuard {

    /** Per-module counters. Volatile fields keep this readable from any thread. */
    public static final class Stat {
        private final AtomicLong runs = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong skipped = new AtomicLong();
        private final AtomicLong consecutive = new AtomicLong();
        private final AtomicLong shortCircuited = new AtomicLong();
        private volatile long lastRunAt = 0L;
        private volatile long trippedAt = 0L;
        private volatile String tripReason = "";
        private volatile long lastErrorAt = 0L;
        private volatile long lastLogAt = 0L;
        private volatile String lastError = "";

        public long runs() { return runs.get(); }
        public long failures() { return failures.get(); }
        public long skipped() { return skipped.get(); }
        public long lastRunAt() { return lastRunAt; }
        public long lastErrorAt() { return lastErrorAt; }
        public String lastError() { return lastError; }

        public long consecutiveFailures() { return consecutive.get(); }
        public long shortCircuited() { return shortCircuited.get(); }
        public long trippedAt() { return trippedAt; }
        public String tripReason() { return tripReason; }

        /** True when the breaker opened: the module is no longer executed on its schedule. */
        public boolean tripped() { return trippedAt > 0L; }

        /** True when this module threw at least once and did not recover after it. */
        public boolean failingNow() {
            return failures.get() > 0 && lastErrorAt > 0 && lastErrorAt >= lastRunAt;
        }
    }

    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();
    private static final long ERROR_LOG_INTERVAL_MS = 60_000L;

    /**
     * Modules whose job is to keep WATCHING. Taking these out of the loop would hide
     * the very problem they report, so they are exempt: they keep running (and keep
     * warning, rate-limited) no matter how often they fail. Stated here explicitly
     * instead of being implied by whoever reads the code.
     */
    private static final java.util.Set<String> CRITICAL =
            java.util.Set.of("ModuleGuard", "TPSMonitor", "WorldStability");

    private static volatile boolean breakerEnabled = true;
    private static volatile int breakerThreshold = 8;

    private VoltPurGuard() {}

    /** Configured from voltpur.yml (guard.circuit-breaker.*); safe to call any time. */
    public static void configure(boolean enabled, int threshold) {
        breakerEnabled = enabled;
        breakerThreshold = Math.max(1, threshold);
    }

    public static boolean breakerEnabled() { return breakerEnabled; }
    public static int breakerThreshold() { return breakerThreshold; }

    /** True when the module is exempt from the breaker (it must keep watching). */
    public static boolean isCritical(String module) {
        return CRITICAL.contains(module);
    }

    /** Closes every breaker and clears consecutive counters. Lifetime counters are kept. */
    public static int resetBreakers() {
        int closed = 0;
        for (Stat s : STATS.values()) {
            if (s.tripped()) closed++;
            s.consecutive.set(0L);
            s.trippedAt = 0L;
            s.tripReason = "";
        }
        return closed;
    }

    public static Stat stat(String module) {
        return STATS.computeIfAbsent(module, key -> new Stat());
    }

    public static Map<String, Stat> all() {
        return STATS;
    }

    /** Runs a task, records success/failure, and returns `fallback` when it throws. */
    public static <T> T run(String module, Supplier<T> task, T fallback) {
        Stat s = stat(module);
        if (s.tripped()) {
            // The breaker is open: do not call into a module known to be failing.
            s.shortCircuited.incrementAndGet();
            return fallback;
        }
        try {
            T value = task.get();
            s.runs.incrementAndGet();   // successful executions only; failures are counted below
            s.lastRunAt = System.currentTimeMillis();
            s.consecutive.set(0L);      // it recovered - leave it alone
            return value;
        } catch (Throwable t) {
            record(module, t);
            return fallback;
        }
    }

    /** Runs a task, records success/failure. Equivalent to run(module, task, null). */
    public static void run(String module, Runnable task) {
        run(module, () -> {
            task.run();
            return null;
        }, null);
    }

    /** Records an intentional skip (module disabled by config or not applicable). */
    public static void skip(String module) {
        Stat s = stat(module);
        s.skipped.incrementAndGet();
        s.lastRunAt = System.currentTimeMillis();
    }

    /** Records a failure explicitly (when you must keep a custom catch block). */
    public static void failure(String module, Throwable t) {
        record(module, t);
    }

    private static void record(String module, Throwable t) {
        Stat s = stat(module);
        s.failures.incrementAndGet();
        long consecutive = s.consecutive.incrementAndGet();
        s.lastErrorAt = System.currentTimeMillis();
        s.lastError = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());

        long now = System.currentTimeMillis();
        boolean first = consecutive == 1L;
        boolean stale = (now - s.lastLogAt) > ERROR_LOG_INTERVAL_MS;
        if (first || stale) {
            s.lastLogAt = now;
            String note = first ? "" : " (repeat; suppressed " + (s.failures.get() - 1) + " earlier)";
            log(Level.WARNING, "[VoltPur-Guard] " + module + " failed" + note + ": " + s.lastError, t);
        }

        if (!first && breakerEnabled && consecutive >= breakerThreshold && !s.tripped()) {
            if (isCritical(module)) {
                log(Level.SEVERE, "[VoltPur-Guard] " + module + " failed " + consecutive
                        + " times in a row. This module only WATCHES the server, so it is kept running "
                        + "(a watchdog that stops watching is worse than a noisy one). Last error: " + s.lastError, null);
            } else {
                s.trippedAt = now;
                s.tripReason = s.lastError;
                log(Level.SEVERE, "[VoltPur-Guard] Module '" + module + "' DISABLED after " + consecutive
                        + " consecutive failures. It will not run again until /voltpur reload. "
                        + "Nothing else is affected. Last error: " + s.lastError
                        + " - fix the cause, then run /voltpur reload.", null);
            }
        }
    }

    /** Logging must never be able to break the guard itself. */
    private static void log(Level level, String message, Throwable t) {
        try {
            if (t == null) Bukkit.getLogger().log(level, message);
            else Bukkit.getLogger().log(level, message, t);
        } catch (Throwable ignored) {
            // Logger unavailable (very early startup) - counters are still updated.
        }
    }

    /** One-line health string for /voltpur modules, e.g. "runs=412 fails=0 last=3s". */
    public static String healthLine(String module) {
        Stat s = STATS.get(module);
        if (s == null || s.runs() == 0L && s.skipped() == 0L && s.failures() == 0L) return "no runs yet";
        StringBuilder sb = new StringBuilder();
        sb.append("runs=").append(s.runs());
        if (s.skipped() > 0) sb.append(" skipped=").append(s.skipped());
        if (s.failures() > 0) sb.append(" fails=").append(s.failures());
        if (s.lastRunAt > 0) sb.append(" last=").append(ago(s.lastRunAt));
        if (s.tripped()) sb.append(" DISABLED-AFTER-FAILURES(").append(s.tripReason).append(")");
        else if (s.failingNow()) sb.append(" FAILING(").append(s.lastError).append(")");
        if (s.tripped() && s.shortCircuited() > 0) sb.append(" short-circuited=").append(s.shortCircuited());
        return sb.toString();
    }

    private static String ago(long timestamp) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp) / 1000L);
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }
}
