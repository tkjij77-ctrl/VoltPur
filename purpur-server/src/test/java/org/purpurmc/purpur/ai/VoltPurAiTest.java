package org.purpurmc.purpur.ai;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.purpurmc.purpur.VoltPurConfig;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class VoltPurAiTest {

    private String originalApiKey;
    private String originalProvider;
    private String originalBaseUrl;
    private String originalModel;
    private double originalBudget;
    private boolean originalPaidConfirmed;
    private boolean originalIncludePlayer;

    @BeforeEach
    void setUp() {
        originalApiKey = VoltPurConfig.aiApiKey;
        originalProvider = VoltPurConfig.aiProvider;
        originalBaseUrl = VoltPurConfig.aiBaseUrl;
        originalModel = VoltPurConfig.aiModel;
        originalBudget = VoltPurConfig.aiMonthlyBudgetUsd;
        originalPaidConfirmed = VoltPurConfig.aiPaidConfirmed;
        originalIncludePlayer = VoltPurConfig.aiIncludePlayerData;

        AiClient.clearCache();
        AiBudget.resetMonthlyForTesting();
    }

    @AfterEach
    void tearDown() {
        VoltPurConfig.aiApiKey = originalApiKey;
        VoltPurConfig.aiProvider = originalProvider;
        VoltPurConfig.aiBaseUrl = originalBaseUrl;
        VoltPurConfig.aiModel = originalModel;
        VoltPurConfig.aiMonthlyBudgetUsd = originalBudget;
        VoltPurConfig.aiPaidConfirmed = originalPaidConfirmed;
        VoltPurConfig.aiIncludePlayerData = originalIncludePlayer;

        AiClient.clearCache();
        AiBudget.resetMonthlyForTesting();
    }

    @Test
    void testProviderDetectionFromKeyShape() {
        VoltPurConfig.aiProvider = "auto";
        VoltPurConfig.aiBaseUrl = "";

        // OpenRouter key
        VoltPurConfig.aiApiKey = "sk-or-v1-abcdef1234567890";
        assertEquals(AiRouter.Provider.OPENROUTER, AiRouter.detectProvider());

        // OpenAI key
        VoltPurConfig.aiApiKey = "sk-proj-xyz987654321";
        assertEquals(AiRouter.Provider.OPENAI, AiRouter.detectProvider());

        // Gemini key
        VoltPurConfig.aiApiKey = "AIzaSyD-GoogleKeyFormat";
        assertEquals(AiRouter.Provider.GEMINI, AiRouter.detectProvider());

        // Groq key
        VoltPurConfig.aiApiKey = "gsk_GroqSecretKey123";
        assertEquals(AiRouter.Provider.GROQ, AiRouter.detectProvider());

        // Custom base URL takes precedence over unknown key
        VoltPurConfig.aiApiKey = "custom-local-token";
        VoltPurConfig.aiBaseUrl = "http://localhost:11434";
        assertEquals(AiRouter.Provider.CUSTOM, AiRouter.detectProvider());

        // Explicit provider override takes top precedence
        VoltPurConfig.aiProvider = "groq";
        assertEquals(AiRouter.Provider.GROQ, AiRouter.detectProvider());
    }

    @Test
    void testRouteResolutionAndFreeTierDetection() {
        VoltPurConfig.aiProvider = "auto";
        VoltPurConfig.aiBaseUrl = "";

        // OpenRouter free default
        VoltPurConfig.aiApiKey = "sk-or-v1-test";
        VoltPurConfig.aiModel = "auto";
        AiRouter.RouteInfo openrouterDefault = AiRouter.resolveRoute();
        assertEquals(AiRouter.Provider.OPENROUTER, openrouterDefault.provider);
        assertTrue(openrouterDefault.isFreeTier, "Default OpenRouter model must be free tier");
        assertEquals("openrouter/free", openrouterDefault.model);

        // OpenRouter explicit paid model
        VoltPurConfig.aiModel = "anthropic/claude-3.5-sonnet";
        AiRouter.RouteInfo openrouterPaid = AiRouter.resolveRoute();
        assertFalse(openrouterPaid.isFreeTier, "Claude on OpenRouter should be flagged as paid tier");

        // Groq is free tier developer model
        VoltPurConfig.aiApiKey = "gsk_test";
        VoltPurConfig.aiModel = "auto";
        AiRouter.RouteInfo groqRoute = AiRouter.resolveRoute();
        assertEquals(AiRouter.Provider.GROQ, groqRoute.provider);
        assertTrue(groqRoute.isFreeTier, "Groq instant model is free tier");

        // OpenAI is strictly paid
        VoltPurConfig.aiApiKey = "sk-test";
        VoltPurConfig.aiModel = "gpt-4o-mini";
        AiRouter.RouteInfo openaiRoute = AiRouter.resolveRoute();
        assertEquals(AiRouter.Provider.OPENAI, openaiRoute.provider);
        assertFalse(openaiRoute.isFreeTier, "OpenAI models are paid tier");
    }

    @Test
    void testPaidTierGatingRules() {
        VoltPurConfig.aiProvider = "openai";
        VoltPurConfig.aiApiKey = "sk-test";
        VoltPurConfig.aiModel = "gpt-4o-mini";
        AiRouter.RouteInfo paidRoute = AiRouter.resolveRoute();

        // Rule 1: Paid unconfirmed is BLOCKED
        VoltPurConfig.aiPaidConfirmed = false;
        VoltPurConfig.aiMonthlyBudgetUsd = 5.0;
        assertTrue(AiRouter.isPaidBlocked(paidRoute), "Paid model must be blocked when unconfirmed");

        // Rule 2: Paid confirmed but $0 budget is BLOCKED
        VoltPurConfig.aiPaidConfirmed = true;
        VoltPurConfig.aiMonthlyBudgetUsd = 0.0;
        assertTrue(AiRouter.isPaidBlocked(paidRoute), "Paid model must be blocked when budget is $0.00");

        // Rule 3: Paid confirmed AND positive budget is ALLOWED
        VoltPurConfig.aiPaidConfirmed = true;
        VoltPurConfig.aiMonthlyBudgetUsd = 5.0;
        assertFalse(AiRouter.isPaidBlocked(paidRoute), "Paid model is allowed when confirmed with budget > 0");

        // Rule 4: Free tier is NEVER blocked by paid gate
        VoltPurConfig.aiPaidConfirmed = false;
        VoltPurConfig.aiMonthlyBudgetUsd = 0.0;
        AiRouter.RouteInfo freeRoute = new AiRouter.RouteInfo(
                AiRouter.Provider.OPENROUTER, "meta-llama/llama-3.2-3b-instruct:free",
                "https://openrouter.ai/api/v1/chat/completions", true
        );
        assertFalse(AiRouter.isPaidBlocked(freeRoute), "Free tier must never be blocked by paid gate");
    }

    @Test
    void testAiBudgetTrackingAndCapEnforcement() {
        VoltPurConfig.aiMonthlyBudgetUsd = 1.00; // $1.00 budget

        // Free tokens do not consume spend budget
        AiBudget.recordTokens(10_000, 5_000, true);
        assertEquals(0.0, AiBudget.getSpentUsd(), 0.0001, "Free tier tokens should not increment USD spend");
        assertEquals(10_000, AiBudget.getPromptTokens());
        assertEquals(5_000, AiBudget.getCompletionTokens());
        assertEquals(15_000, AiBudget.getTotalTokens());
        assertFalse(AiBudget.isOverBudget());

        // Record paid tokens
        // 1,000,000 prompt tokens = $0.15, 1,000,000 comp tokens = $0.60
        AiBudget.recordTokens(1_000_000, 1_000_000, false);
        double expectedSpend = 0.15 + 0.60; // $0.75
        assertEquals(expectedSpend, AiBudget.getSpentUsd(), 0.001);
        assertFalse(AiBudget.isOverBudget(), "Under $1.00 budget should not be over budget");

        // Push over budget
        AiBudget.recordTokens(500_000, 500_000, false); // adds $0.375 -> $1.125
        assertTrue(AiBudget.isOverBudget(), "Spending over $1.00 should trigger over-budget state");
        assertTrue(AiBudget.getSpentUsd() > 1.00);
    }

    @Test
    void testAiContextPrivacyFilter() {
        VoltPurConfig.aiIncludePlayerData = false;
        String snapshotPrivate = AiContext.buildSystemSnapshot();
        assertTrue(snapshotPrivate.contains("redacted"), "Default snapshot must redact player identities");
        assertFalse(snapshotPrivate.contains("coordinates:"), "Private snapshot must not leak coordinates");
        assertTrue(snapshotPrivate.contains("TPS"), "Snapshot must contain TPS metric");
        assertTrue(snapshotPrivate.contains("MSPT"), "Snapshot must contain MSPT metric");
        assertTrue(snapshotPrivate.contains("Heap Memory"), "Snapshot must contain heap metric");
    }

    @Test
    void testKeyMaskingSecurity() {
        assertEquals("[NONE]", AiClient.maskKey(null));
        assertEquals("[NONE]", AiClient.maskKey("   "));
        assertEquals("***", AiClient.maskKey("12345"));
        assertEquals("sk-or-v...", AiClient.maskKey("sk-or-v1-abcdef1234567890"));
    }

    @Test
    void testPlayerCooldownRateLimiting() {
        UUID playerId = UUID.randomUUID();

        // First call: 0 cooldown
        assertEquals(0, AiClient.checkCooldown(playerId));

        // Apply 30s cooldown
        AiClient.applyCooldown(playerId);
        int cooldown = AiClient.checkCooldown(playerId);
        assertTrue(cooldown > 0 && cooldown <= 30, "Cooldown should be between 1 and 30 seconds");

        // Console (null UUID) has no cooldown
        assertEquals(0, AiClient.checkCooldown(null));
    }

    @Test
    void testSetAiKeyAndProvider() {
        VoltPurConfig.setAiKeyAndProvider("groq", "gsk_test12345678");
        assertTrue(VoltPurConfig.aiEnabled, "Setting key must automatically enable AI");
        assertEquals("groq", VoltPurConfig.aiProvider);
        assertEquals("gsk_test12345678", VoltPurConfig.aiApiKey);
        assertEquals(AiRouter.Provider.GROQ, AiRouter.detectProvider());
    }
}
