package org.purpurmc.purpur.ai;

import org.purpurmc.purpur.VoltPurConfig;

/**
 * VoltPur AI Router (§5.3, §5.4, §5.7).
 *
 * Detects AI provider from API key or config, enforces free/paid gates,
 * selects appropriate models, and maps target endpoints.
 */
public final class AiRouter {

    public enum Provider {
        OPENROUTER, OPENAI, GEMINI, GROQ, CUSTOM, UNKNOWN
    }

    public static class RouteInfo {
        public final Provider provider;
        public final String model;
        public final String endpointUrl;
        public final boolean isFreeTier;

        public RouteInfo(Provider provider, String model, String endpointUrl, boolean isFreeTier) {
            this.provider = provider;
            this.model = model;
            this.endpointUrl = endpointUrl;
            this.isFreeTier = isFreeTier;
        }
    }

    private AiRouter() {}

    /**
     * Identifies the AI provider from key format or explicit configuration (§5.3).
     */
    public static Provider detectProvider() {
        String explicit = VoltPurConfig.aiProvider;
        if (explicit != null && !explicit.isBlank() && !"auto".equalsIgnoreCase(explicit)) {
            try {
                return Provider.valueOf(explicit.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        String baseUrl = VoltPurConfig.aiBaseUrl;
        if (baseUrl != null && !baseUrl.isBlank()) {
            return Provider.CUSTOM;
        }

        String key = VoltPurConfig.aiApiKey;
        if (key == null || key.isBlank()) return Provider.UNKNOWN;

        key = key.trim();
        if (key.startsWith("sk-or-")) return Provider.OPENROUTER;
        if (key.startsWith("sk-")) return Provider.OPENAI;
        if (key.startsWith("AIza")) return Provider.GEMINI;
        if (key.startsWith("gsk_")) return Provider.GROQ;

        return Provider.UNKNOWN;
    }

    /**
     * Resolves the full route info, selecting the model and endpoint URL.
     */
    public static RouteInfo resolveRoute() {
        Provider provider = detectProvider();
        String model = VoltPurConfig.aiModel;
        String endpoint;
        boolean free = false;

        switch (provider) {
            case OPENROUTER:
                endpoint = "https://openrouter.ai/api/v1/chat/completions";
                if (model == null || model.isBlank() || "auto".equalsIgnoreCase(model)) {
                    model = "openrouter/free";
                }
                free = model.toLowerCase().endsWith(":free") || model.equalsIgnoreCase("openrouter/free");
                break;

            case GROQ:
                endpoint = "https://api.groq.com/openai/v1/chat/completions";
                if (model == null || model.isBlank() || "auto".equalsIgnoreCase(model)) {
                    model = "llama-3.1-8b-instant";
                }
                free = true; // Groq developer free rate-limited tier
                break;

            case GEMINI:
                endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
                if (model == null || model.isBlank() || "auto".equalsIgnoreCase(model)) {
                    model = "gemini-1.5-flash";
                }
                free = true; // Gemini developer free tier
                break;

            case OPENAI:
                endpoint = "https://api.openai.com/v1/chat/completions";
                if (model == null || model.isBlank() || "auto".equalsIgnoreCase(model)) {
                    model = "gpt-4o-mini";
                }
                free = false; // OpenAI is always paid
                break;

            case CUSTOM:
                String base = VoltPurConfig.aiBaseUrl.trim();
                if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                if (base.endsWith("/chat/completions")) {
                    endpoint = base;
                } else if (base.endsWith("/v1")) {
                    endpoint = base + "/chat/completions";
                } else {
                    endpoint = base + "/v1/chat/completions";
                }
                if (model == null || model.isBlank() || "auto".equalsIgnoreCase(model)) {
                    model = "default";
                }
                free = true; // Local instances (Ollama, LM Studio) are free
                break;

            default:
                endpoint = "https://openrouter.ai/api/v1/chat/completions";
                if (model == null || model.isBlank() || "auto".equalsIgnoreCase(model)) {
                    model = "openrouter/free";
                }
                free = model.toLowerCase().endsWith(":free") || model.equalsIgnoreCase("openrouter/free");
                break;
        }

        return new RouteInfo(provider, model, endpoint, free);
    }

    /**
     * Checks if a request is blocked by the paid gate policy (§5.3).
     * Paid calls require explicit confirmation (/ai confirm-paid) and monthly-budget-usd > 0.
     */
    public static boolean isPaidBlocked(RouteInfo route) {
        if (route.isFreeTier) return false;

        // Paid tier checks:
        if (!VoltPurConfig.aiPaidConfirmed) {
            return true; // Not confirmed yet
        }
        if (VoltPurConfig.aiMonthlyBudgetUsd <= 0.0) {
            return true; // Budget cap is 0
        }
        return false;
    }
}
