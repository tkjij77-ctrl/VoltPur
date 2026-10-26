package org.purpurmc.purpur.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.util.VoltPurLogger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * VoltPur AI Budget & Token Tracker (§5.2, §5.3, §5.7).
 *
 * Enforces monthly budget caps in USD, tracks prompt and completion tokens,
 * persists usage to logs/voltpur-ai-budget.json, and blocks paid requests when budget is exceeded.
 */
public final class AiBudget {

    private static final File BUDGET_FILE = new File("logs", "voltpur-ai-budget.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SimpleDateFormat MONTH_FORMAT = new SimpleDateFormat("yyyy-MM");

    private static String currentMonth = "";
    private static final AtomicInteger PROMPT_TOKENS = new AtomicInteger(0);
    private static final AtomicInteger COMPLETION_TOKENS = new AtomicInteger(0);
    private static final DoubleAdder SPENT_USD = new DoubleAdder();
    private static boolean initialized = false;

    private AiBudget() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        currentMonth = MONTH_FORMAT.format(new Date());
        load();
    }

    private static synchronized void checkMonthRollover() {
        String nowMonth = MONTH_FORMAT.format(new Date());
        if (!nowMonth.equals(currentMonth)) {
            // New month: rollover counters
            currentMonth = nowMonth;
            PROMPT_TOKENS.set(0);
            COMPLETION_TOKENS.set(0);
            SPENT_USD.reset();
            save();
            VoltPurLogger.info("AI-Budget", "Monthly AI usage reset for " + currentMonth);
        }
    }

    /**
     * Checks if a request is permitted under the current budget cap.
     * If monthlyBudgetUsd == 0, only free models are allowed.
     */
    public static boolean canSpend(double estimatedCostUsd, boolean isFreeTier) {
        checkMonthRollover();
        if (isFreeTier) return true;

        double cap = VoltPurConfig.aiMonthlyBudgetUsd;
        if (cap <= 0.0) {
            return false; // Paid tier disabled by zero budget
        }
        return (SPENT_USD.doubleValue() + estimatedCostUsd) <= cap;
    }

    public static boolean isOverBudget() {
        checkMonthRollover();
        double cap = VoltPurConfig.aiMonthlyBudgetUsd;
        if (cap <= 0.0) return true;
        return SPENT_USD.doubleValue() >= cap;
    }

    public static void recordTokens(int promptTokens, int completionTokens, boolean isFreeTier) {
        double cost = 0.0;
        if (!isFreeTier) {
            // Standard estimate based on typical low-cost paid tier (e.g. gpt-4o-mini): $0.15 / 1M prompt, $0.60 / 1M comp
            cost = (promptTokens * 0.00000015) + (completionTokens * 0.00000060);
        }
        recordUsage(promptTokens, completionTokens, cost);
    }

    public static synchronized void recordUsage(int promptTokens, int completionTokens, double costUsd) {
        checkMonthRollover();
        PROMPT_TOKENS.addAndGet(Math.max(0, promptTokens));
        COMPLETION_TOKENS.addAndGet(Math.max(0, completionTokens));
        if (costUsd > 0.0) {
            SPENT_USD.add(costUsd);
        }
        save();
    }

    public static double getSpentUsd() {
        checkMonthRollover();
        return SPENT_USD.doubleValue();
    }

    public static int getTotalTokens() {
        return PROMPT_TOKENS.get() + COMPLETION_TOKENS.get();
    }

    public static int getPromptTokens() {
        return PROMPT_TOKENS.get();
    }

    public static int getCompletionTokens() {
        return COMPLETION_TOKENS.get();
    }

    public static String getCurrentMonth() {
        return currentMonth;
    }

    private static synchronized void load() {
        if (!BUDGET_FILE.exists()) return;
        try (FileReader reader = new FileReader(BUDGET_FILE, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String fileMonth = json.has("month") ? json.get("month").getAsString() : "";
            if (currentMonth.equals(fileMonth)) {
                if (json.has("promptTokens")) PROMPT_TOKENS.set(json.get("promptTokens").getAsInt());
                if (json.has("completionTokens")) COMPLETION_TOKENS.set(json.get("completionTokens").getAsInt());
                if (json.has("spentUsd")) SPENT_USD.add(json.get("spentUsd").getAsDouble());
            }
        } catch (Exception e) {
            VoltPurLogger.warn("AI-Budget", "Failed loading budget file", e);
        }
    }

    private static synchronized void save() {
        try {
            File dir = BUDGET_FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            JsonObject json = new JsonObject();
            json.addProperty("month", currentMonth);
            json.addProperty("promptTokens", PROMPT_TOKENS.get());
            json.addProperty("completionTokens", COMPLETION_TOKENS.get());
            json.addProperty("spentUsd", SPENT_USD.doubleValue());
            json.addProperty("budgetCapUsd", VoltPurConfig.aiMonthlyBudgetUsd);
            json.addProperty("updatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));

            try (FileWriter writer = new FileWriter(BUDGET_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (Exception e) {
            VoltPurLogger.warn("AI-Budget", "Failed saving budget file", e);
        }
    }

    public static void resetForTesting() {
        PROMPT_TOKENS.set(0);
        COMPLETION_TOKENS.set(0);
        SPENT_USD.reset();
        currentMonth = MONTH_FORMAT.format(new Date());
        initialized = false;
    }

    public static void resetMonthlyForTesting() {
        resetForTesting();
    }
}
