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
 * Rule for contributors: never wrap VoltPur work in an empty catch. Use
 * VoltPurGuard.run("ModuleName", ...) instead.
 */
public final class VoltPurGuard {

    /** Per-module counters. Volatile fields keep this readable from any thread. */
    public static final class Stat {
        private final AtomicLong runs = new AtomicLong();
        private final AtomicLong failures = new AtomicLong();
        private final AtomicLong skipped = new AtomicLong();
        private volatile long lastRunAt = 0L;
        private volatile long lastErrorAt = 0L;
        private volatile long lastLogAt = 0L;
        private volatile String lastError = "";

        public long runs() { return runs.get(); }
        public long failures() { return failures.get(); }
        public long skipped() { return skipped.get(); }
        public long lastRunAt() { return lastRunAt; }
        public long lastErrorAt() { return lastErrorAt; }
        public String lastError() { return lastError; }

        /** True when this module threw at least once and did not recover after it. */
        public boolean failingNow() {
            return failures.get() > 0 && lastErrorAt > 0 && lastErrorAt >= lastRunAt;
        }
    }

    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();
    private static final long ERROR_LOG_INTERVAL_MS = 60_000L;

    private VoltPurGuard() {}

    public static Stat stat(String module) {
        return STATS.computeIfAbsent(module, key -> new Stat());
    }

    public static Map<String, Stat> all() {
        return STATS;
    }

    /** Runs a task, records success/failure, and returns `fallback` when it throws. */
    public static <T> T run(String module, Supplier<T> task, T fallback) {
        Stat s = stat(module);
        try {
            T value = task.get();
            s.runs.incrementAndGet();   // successful executions only; failures are counted below
            s.lastRunAt = System.currentTimeMillis();
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
        s.lastErrorAt = System.currentTimeMillis();
        s.lastError = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());

        long now = System.currentTimeMillis();
        boolean first = s.failures.get() == 1L;
        boolean stale = (now - s.lastLogAt) > ERROR_LOG_INTERVAL_MS;
        if (first || stale) {
            s.lastLogAt = now;
            String note = first ? "" : " (repeat; suppressed " + (s.failures.get() - 1) + " earlier)";
            try {
                Bukkit.getLogger().log(Level.WARNING,
                        "[VoltPur-Guard] " + module + " failed" + note + ": " + s.lastError, t);
            } catch (Throwable ignored) {
                // Logger unavailable (very early startup) - counters are still updated.
            }
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
        if (s.failingNow()) sb.append(" FAILING(").append(s.lastError).append(")");
        return sb.toString();
    }

    private static String ago(long timestamp) {
        long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp) / 1000L);
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }
}
