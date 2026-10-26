package org.purpurmc.purpur.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.purpurmc.purpur.VoltPurConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VoltPur AI Async Client (§5.6, §5.7).
 *
 * Implements non-blocking HTTP calls adhering to OpenAI's schema (/v1/chat/completions),
 * 15-second timeout, 5-minute response cache, 30-second per-player rate limiting,
 * masked key logging, and automatic fallback on rate limit.
 */
public final class AiClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Gson GSON = new Gson();

    // 5-minute response cache (§5.6)
    private static final class CachedResponse {
        final String answer;
        final long timestamp;
        CachedResponse(String answer, long timestamp) {
            this.answer = answer;
            this.timestamp = timestamp;
        }
    }
    private static final Map<String, CachedResponse> RESPONSE_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    // 30-second per-player cooldown (§5.6)
    private static final Map<UUID, Long> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 30 * 1000L; // 30 seconds

    // Fallback free models for OpenRouter on rate-limit (429)
    private static final List<String> OPENROUTER_FREE_FALLBACKS = Arrays.asList(
            "meta-llama/llama-3.2-3b-instruct:free",
            "google/gemini-2.0-flash-exp:free",
            "qwen/qwen-2.5-7b-instruct:free",
            "meta-llama/llama-3.1-8b-instruct:free"
    );

    private AiClient() {}

    /**
     * Checks rate limit for a player. Returns remaining seconds, or 0 if allowed.
     */
    public static int checkCooldown(UUID playerId) {
        if (playerId == null) return 0; // Console has no cooldown
        Long last = PLAYER_COOLDOWNS.get(playerId);
        if (last == null) return 0;
        long elapsed = System.currentTimeMillis() - last;
        if (elapsed < COOLDOWN_MS) {
            return (int) Math.ceil((COOLDOWN_MS - elapsed) / 1000.0);
        }
        return 0;
    }

    /**
     * Registers player activity to start their 30-second cooldown.
     */
    public static void applyCooldown(UUID playerId) {
        if (playerId != null) {
            PLAYER_COOLDOWNS.put(playerId, System.currentTimeMillis());
        }
    }

    /**
     * Safely masks an API key (first 7 characters only, e.g. "sk-or-v...") (§5.6).
     */
    public static String maskKey(String key) {
        if (key == null || key.isBlank()) return "[NONE]";
        String trimmed = key.trim();
        if (trimmed.length() <= 7) return "***";
        return trimmed.substring(0, 7) + "...";
    }

    /**
     * Sends an AI query asynchronously using the configured provider and model.
     * Enforces paid gate, budget limits, response cache, and 15s timeout.
     */
    public static CompletableFuture<String> queryAsync(String userPrompt, boolean bypassCache) {
        if (!VoltPurConfig.aiEnabled) {
            return CompletableFuture.completedFuture("VoltPur AI is currently disabled in voltpur.yml (modules.ai.enabled: false).");
        }

        AiRouter.RouteInfo route = AiRouter.resolveRoute();

        // Check paid gate (§5.3)
        if (AiRouter.isPaidBlocked(route)) {
            if (!VoltPurConfig.aiPaidConfirmed) {
                return CompletableFuture.completedFuture(
                        "§c[VoltPur AI] Paid model (" + route.model + ") blocked! " +
                        "Use §e/ai confirm-paid§c and configure a positive §emonthly-budget-usd§c before making paid requests."
                );
            }
            if (VoltPurConfig.aiMonthlyBudgetUsd <= 0.0) {
                return CompletableFuture.completedFuture(
                        "§c[VoltPur AI] Monthly budget is set to $0.00. Paid queries are disabled until budget is increased in voltpur.yml."
                );
            }
        }

        // Check budget cap (§5.2)
        if (!route.isFreeTier && AiBudget.isOverBudget()) {
            return CompletableFuture.completedFuture(
                    "§c[VoltPur AI] Monthly spending cap reached (" +
                    String.format("$%.4f / $%.2f", AiBudget.getSpentUsd(), VoltPurConfig.aiMonthlyBudgetUsd) +
                    "). Service paused until next month or budget increase."
            );
        }

        // Cache lookup (§5.6)
        String cacheKey = route.model + ":" + userPrompt.trim().toLowerCase();
        if (!bypassCache) {
            CachedResponse cached = RESPONSE_CACHE.get(cacheKey);
            if (cached != null) {
                if (System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    return CompletableFuture.completedFuture(cached.answer + " §7(cached)§r");
                } else {
                    RESPONSE_CACHE.remove(cacheKey);
                }
            }
        }

        String systemPrompt = AiContext.buildSystemSnapshot();
        return executeRequest(route, route.model, systemPrompt, userPrompt, 0)
                .thenApply(response -> {
                    if (response != null && !response.startsWith("§c")) {
                        RESPONSE_CACHE.put(cacheKey, new CachedResponse(response, System.currentTimeMillis()));
                    }
                    return response;
                });
    }

    private static CompletableFuture<String> executeRequest(
            AiRouter.RouteInfo route,
            String targetModel,
            String systemPrompt,
            String userPrompt,
            int retryDepth
    ) {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", targetModel);

        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        payload.add("messages", messages);
        payload.addProperty("temperature", 0.6);
        payload.addProperty("max_tokens", 800);

        String jsonBody = GSON.toJson(payload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(route.endpointUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", "VoltPur-Server/1.21.10");

        String apiKey = VoltPurConfig.aiApiKey;
        if (apiKey != null && !apiKey.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        if (route.provider == AiRouter.Provider.OPENROUTER) {
            reqBuilder.header("HTTP-Referer", "https://github.com/tkjij77-ctrl/VoltPur");
            reqBuilder.header("X-Title", "VoltPur Minecraft Server");
        }

        HttpRequest request = reqBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((resp, ex) -> {
                    if (ex != null) {
                        Bukkit.getLogger().warning("[VoltPur-AI] Request failed (" + maskKey(apiKey) + "): " + ex.getMessage());
                        return "§c[VoltPur AI] Connection error: " + ex.getMessage();
                    }

                    int statusCode = resp.statusCode();
                    String respBody = resp.body();

                    // Automatic fallback on rate-limit (429) for OpenRouter (§5.4)
                    if (statusCode == 429 && route.provider == AiRouter.Provider.OPENROUTER && retryDepth < OPENROUTER_FREE_FALLBACKS.size()) {
                        String nextModel = OPENROUTER_FREE_FALLBACKS.get(retryDepth);
                        Bukkit.getLogger().info("[VoltPur-AI] Rate-limited on " + targetModel + ". Falling back to " + nextModel);
                        return executeRequest(route, nextModel, systemPrompt, userPrompt, retryDepth + 1).join();
                    }

                    if (statusCode != 200) {
                        Bukkit.getLogger().warning("[VoltPur-AI] Provider HTTP " + statusCode + " (" + maskKey(apiKey) + "): " + respBody);
                        return "§c[VoltPur AI] API error (HTTP " + statusCode + "): " + extractErrorMessage(respBody);
                    }

                    try {
                        JsonObject json = JsonParser.parseString(respBody).getAsJsonObject();
                        
                        // Parse usage tokens (§5.2)
                        int promptTokens = 0;
                        int compTokens = 0;
                        if (json.has("usage") && json.get("usage").isJsonObject()) {
                            JsonObject usage = json.getAsJsonObject("usage");
                            if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").getAsInt();
                            if (usage.has("completion_tokens")) compTokens = usage.get("completion_tokens").getAsInt();
                        }
                        AiBudget.recordTokens(promptTokens, compTokens, route.isFreeTier);

                        // Parse response content
                        if (json.has("choices") && json.get("choices").isJsonArray()) {
                            JsonArray choices = json.getAsJsonArray("choices");
                            if (choices.size() > 0) {
                                JsonObject choice = choices.get(0).getAsJsonObject();
                                if (choice.has("message") && choice.get("message").isJsonObject()) {
                                    JsonObject msg = choice.getAsJsonObject("message");
                                    if (msg.has("content")) {
                                        return msg.get("content").getAsString().trim();
                                    }
                                }
                            }
                        }
                        return "§c[VoltPur AI] Unexpected response format from AI provider.";
                    } catch (Exception parseEx) {
                        Bukkit.getLogger().warning("[VoltPur-AI] Response parsing error: " + parseEx.getMessage());
                        return "§c[VoltPur AI] Failed to parse provider response: " + parseEx.getMessage();
                    }
                });
    }

    private static String extractErrorMessage(String body) {
        if (body == null || body.isBlank()) return "Unknown error";
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error")) {
                JsonElement err = json.get("error");
                if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    return err.getAsJsonObject().get("message").getAsString();
                }
                return err.toString();
            }
        } catch (Exception ignored) {}
        return body.length() > 100 ? body.substring(0, 100) + "..." : body;
    }

    /**
     * Clears response cache.
     */
    public static void clearCache() {
        RESPONSE_CACHE.clear();
    }
}
