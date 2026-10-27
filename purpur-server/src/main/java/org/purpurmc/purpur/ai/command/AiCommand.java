package org.purpurmc.purpur.ai.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.ai.AiBudget;
import org.purpurmc.purpur.ai.AiClient;
import org.purpurmc.purpur.ai.AiContext;
import org.purpurmc.purpur.ai.AiRouter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * VoltPur AI Command (§5.1, §5.6).
 *
 * Implements /ai:
 *   /ai <question>     Queries the AI with live server context.
 *   /ai status         Displays provider, model, tier, monthly budget, tokens used.
 *   /ai models         Lists active/suggested models.
 *   /ai test           Non-blocking ping/query test.
 *   /ai doctor         Comprehensive server performance diagnosis.
 *   /ai confirm-paid   Confirms acknowledgment of paid usage.
 */
public class AiCommand extends Command {

    public static final String PERM_QUERY = "voltpur.ai";
    public static final String PERM_ADMIN = "voltpur.admin.ai";

    public AiCommand(String name) {
        super(name);
        this.description = "VoltPur practical AI assistant and server doctor";
        this.usageMessage = "/ai [<question>|status|setkey|setmodel|models|test|doctor|confirm-paid]";
        this.setPermission(null); // Checked per subcommand
        this.setAliases(Arrays.asList("voltpurai", "vai"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("status");
            if (sender.hasPermission(PERM_ADMIN)) {
                list.add("setkey");
                list.add("setmodel");
                list.add("doctor");
                list.add("models");
                list.add("test");
                list.add("confirm-paid");
            }
            return list.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setkey") && sender.hasPermission(PERM_ADMIN)) {
            return Stream.of("openrouter", "groq", "gemini", "openai", "auto", "custom")
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setmodel") && sender.hasPermission(PERM_ADMIN)) {
            return Stream.of("openrouter/free", "liquid/lfm-2.5-2.6b:free", "google/gemma-4-31b-it:free",
                    "llama-3.1-8b-instant", "gemini-1.5-flash", "gpt-4o-mini", "auto")
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "status":
                handleStatus(sender);
                return true;

            case "setkey":
                if (!checkAdmin(sender)) return true;
                handleSetKey(sender, args);
                return true;

            case "setmodel":
                if (!checkAdmin(sender)) return true;
                handleSetModel(sender, args);
                return true;

            case "models":
                if (!checkAdmin(sender)) return true;
                handleModels(sender);
                return true;

            case "test":
                if (!checkAdmin(sender)) return true;
                handleTest(sender);
                return true;

            case "doctor":
                if (!checkAdmin(sender)) return true;
                handleDoctor(sender);
                return true;

            case "confirm-paid":
                if (!checkAdmin(sender)) return true;
                handleConfirmPaid(sender);
                return true;

            default:
                // Handle general query (§5.1)
                handleQuery(sender, args);
                return true;
        }
    }

    private boolean checkAdmin(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§cYou do not have permission (" + PERM_ADMIN + ") to use this subcommand.");
            return false;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6━━━━━━━━━━ §e⚡ VoltPur AI §6━━━━━━━━━━");
        sender.sendMessage("§b/ai <question> §7- Ask AI with live server snapshot context");
        sender.sendMessage("§b/ai status §7- View AI provider, model, budget & usage");
        if (sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§b/ai setkey <provider> <key> §7- Link AI provider & set API key");
            sender.sendMessage("§b/ai setmodel <model> §7- Set custom model (e.g. openrouter/free)");
            sender.sendMessage("§b/ai doctor §7- Run comprehensive performance diagnosis");
            sender.sendMessage("§b/ai models §7- View provider models and recommendations");
            sender.sendMessage("§b/ai test §7- Async connection and latency test");
            sender.sendMessage("§b/ai confirm-paid §7- Confirm permission to use paid models");
        }
        sender.sendMessage("§7Config: §evoltpur.yml §7(ai.*)");
    }

    private void handleSetModel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c[VoltPur AI] Usage: /ai setmodel <model_name>");
            sender.sendMessage("§7Example: §b/ai setmodel openrouter/free");
            return;
        }

        String model = args[1].trim();
        VoltPurConfig.setAiModel(model);
        AiClient.clearCache();

        AiRouter.RouteInfo route = AiRouter.resolveRoute();
        sender.sendMessage("§a[VoltPur AI] Active model updated to: §f" + route.model +
                (route.isFreeTier ? " §a(FREE)" : " §e(PAID - requires /ai confirm-paid)"));
        sender.sendMessage("§7Saved to voltpur.yml. Run §b/ai test §7to verify.");
    }

    private void handleSetKey(CommandSender sender, String[] args) {
        String provider = "auto";
        String key = "";

        if (args.length >= 3) {
            provider = args[1].toLowerCase().trim();
            key = args[2].trim();
        } else if (args.length == 2) {
            String candidate = args[1].trim();
            if (isKnownProvider(candidate)) {
                sender.sendMessage("§c[VoltPur AI] Usage: /ai setkey " + candidate + " <API_KEY>");
                return;
            }
            key = candidate;
            provider = "auto";
        } else {
            sender.sendMessage("§c[VoltPur AI] Usage: /ai setkey <provider> <API_KEY>");
            sender.sendMessage("§7Providers: §bopenrouter§7, §bgroq§7, §bgemini§7, §bopenai§7, §bcustom§7, §bauto");
            return;
        }

        if (key.isBlank()) {
            sender.sendMessage("§c[VoltPur AI] API key cannot be empty.");
            return;
        }

        if (!"auto".equals(provider) && !isKnownProvider(provider)) {
            sender.sendMessage("§c[VoltPur AI] Unknown provider '" + provider + "'.");
            sender.sendMessage("§7Valid providers: §bopenrouter§7, §bgroq§7, §bgemini§7, §bopenai§7, §bcustom§7, §bauto");
            return;
        }

        VoltPurConfig.setAiKeyAndProvider(provider, key);
        AiClient.clearCache();

        AiRouter.RouteInfo route = AiRouter.resolveRoute();
        sender.sendMessage("§a[VoltPur AI] Successfully linked provider §b" + route.provider.name() +
                " §awith key §e" + AiClient.maskKey(key) + "§a!");
        sender.sendMessage("§7AI is enabled. Default model: §f" + route.model +
                (route.isFreeTier ? " §a(FREE)" : " §e(PAID - requires /ai confirm-paid)"));
        sender.sendMessage("§7Saved to voltpur.yml. Run §b/ai test §7to verify connection.");
    }

    private static boolean isKnownProvider(String name) {
        String n = name.toLowerCase();
        return n.equals("openrouter") || n.equals("groq") || n.equals("gemini")
                || n.equals("openai") || n.equals("custom");
    }

    private void handleStatus(CommandSender sender) {
        AiRouter.RouteInfo route = AiRouter.resolveRoute();
        sender.sendMessage("§6━━━━━━━━━━ §e⚡ VoltPur AI Status §6━━━━━━━━━━");
        sender.sendMessage("§7Enabled: " + (VoltPurConfig.aiEnabled ? "§aYES" : "§cNO (disabled in config)"));
        sender.sendMessage("§7Provider: §f" + route.provider.name());
        sender.sendMessage("§7Model: §f" + route.model + (route.isFreeTier ? " §a(FREE)" : " §e(PAID)"));
        sender.sendMessage("§7Paid Confirmed: " + (VoltPurConfig.aiPaidConfirmed ? "§aYES" : "§cNO (locked)"));
        sender.sendMessage("§7Monthly Budget: §f$" + String.format("%.2f USD", VoltPurConfig.aiMonthlyBudgetUsd));
        sender.sendMessage("§7Month Spending: §e$" + String.format("%.4f USD", AiBudget.getSpentUsd()) +
                (AiBudget.isOverBudget() ? " §c(BUDGET CAP REACHED)" : ""));
        sender.sendMessage("§7Token Usage (Month): §f" + AiBudget.getPromptTokens() + " prompt, " +
                AiBudget.getCompletionTokens() + " comp (§6" + AiBudget.getTotalTokens() + " total§7)");
        sender.sendMessage("§7API Key: §8" + AiClient.maskKey(VoltPurConfig.aiApiKey));
        sender.sendMessage("§7Endpoint: §8" + route.endpointUrl);
    }

    private void handleModels(CommandSender sender) {
        AiRouter.RouteInfo route = AiRouter.resolveRoute();
        sender.sendMessage("§6━━━━━━━━━━ §e⚡ VoltPur AI Models §6━━━━━━━━━━");
        sender.sendMessage("§7Active Provider: §b" + route.provider.name());
        sender.sendMessage("§7Active Model: §f" + route.model + (route.isFreeTier ? " §a(FREE)" : " §e(PAID)"));
        sender.sendMessage("");
        sender.sendMessage("§eRecommended Models:");
        sender.sendMessage("§7• §bOpenRouter (Free): §fopenrouter/free §7(auto-routed), §fliquid/lfm-2.5-2.6b:free, §fgoogle/gemma-4-31b-it:free");
        sender.sendMessage("§7• §bGroq: §fllama-3.1-8b-instant §7(ultra fast, free tier)");
        sender.sendMessage("§7• §bGemini: §fgemini-1.5-flash §7(high rate limit, free tier)");
        sender.sendMessage("§7• §bOpenAI: §fgpt-4o-mini §7(paid, low cost), §fgpt-4o");
        sender.sendMessage("§7• §bCustom: §flocal Ollama / LM Studio (always free)");
        sender.sendMessage("§7To switch model: §b/ai setmodel <model>");
    }

    private void handleTest(CommandSender sender) {
        if (!VoltPurConfig.aiEnabled) {
            sender.sendMessage("§c[VoltPur AI] AI is disabled in voltpur.yml (modules.ai.enabled: false).");
            return;
        }

        sender.sendMessage("§e[VoltPur AI] Testing connection to AI provider (" + AiRouter.detectProvider().name() + ")...");
        long start = System.currentTimeMillis();

        AiClient.queryAsync("Respond with 'VoltPur AI connection successful.' only.", true)
                .thenAccept(response -> {
                    long latency = System.currentTimeMillis() - start;
                    if (response.startsWith("§c")) {
                        sender.sendMessage(response);
                    } else {
                        sender.sendMessage("§a[VoltPur AI] Test passed (" + latency + " ms): §f" + response);
                    }
                });
    }

    private void handleDoctor(CommandSender sender) {
        if (!VoltPurConfig.aiEnabled) {
            sender.sendMessage("§c[VoltPur AI] AI is disabled in voltpur.yml (modules.ai.enabled: false).");
            return;
        }

        sender.sendMessage("§e[VoltPur AI] Analyzing live server metrics (MSPT, TPS, hoppers, heap, chunks)...");
        String doctorPrompt = AiContext.buildDoctorQuery();

        AiClient.queryAsync(doctorPrompt, false)
                .thenAccept(response -> {
                    sender.sendMessage("§6━━━━━━━━━━ §e⚡ VoltPur AI Doctor Report §6━━━━━━━━━━");
                    for (String line : response.split("\n")) {
                        if (!line.isBlank()) {
                            sender.sendMessage("§f" + line);
                        }
                    }
                    sender.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                });
    }

    private void handleConfirmPaid(CommandSender sender) {
        VoltPurConfig.setAiPaidConfirmed(true);
        sender.sendMessage("§a[VoltPur AI] Paid tier confirmed! Paid models are now unlocked up to the monthly budget cap ($"
                + String.format("%.2f", VoltPurConfig.aiMonthlyBudgetUsd) + " USD).");
        sender.sendMessage("§7Preference saved to voltpur.yml (modules.ai.paid-confirmed: true).");
    }

    private void handleQuery(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_QUERY) && !sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("§cYou do not have permission (" + PERM_QUERY + ") to query VoltPur AI.");
            return;
        }

        UUID playerId = (sender instanceof Player) ? ((Player) sender).getUniqueId() : null;
        int cooldown = AiClient.checkCooldown(playerId);
        if (cooldown > 0) {
            sender.sendMessage("§c[VoltPur AI] Please wait " + cooldown + "s before asking another question.");
            return;
        }

        AiClient.applyCooldown(playerId);

        String question = String.join(" ", args);
        sender.sendMessage("§7[VoltPur AI] Consulting advisor with live server snapshot...");

        AiClient.queryAsync(question, false)
                .thenAccept(response -> {
                    sender.sendMessage("§6[VoltPur AI] §f" + response);
                });
    }
}
