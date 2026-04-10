package com.hyperliquid.tradingagent.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Centralized environment variable loading for the trading agent configuration.
 */
public class ConfigLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        try {
            Dotenv.load();
        } catch (Exception e) {
            // .env file not found - continue with system environment variables
            logger.debug("Could not load .env file, continuing with system environment variables");
        }
    }

    private static String getEnv(String name) {
        return getEnv(name, null, false);
    }

    private static String getEnv(String name, String defaultValue) {
        return getEnv(name, defaultValue, false);
    }

    private static String getEnv(String name, String defaultValue, boolean required) {
        String value = System.getenv(name);
        if (required && (value == null || value.trim().isEmpty())) {
            throw new RuntimeException("Missing required environment variable: " + name);
        }
        return value != null ? value : defaultValue;
    }

    private static boolean getBool(String name, boolean defaultValue) {
        String raw = System.getenv(name);
        if (raw == null) {
            return defaultValue;
        }
        return raw.trim().toLowerCase().matches("1|true|yes|on");
    }

    private static Integer getInt(String name, Integer defaultValue) {
        String raw = System.getenv(name);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid integer for " + name + ": " + raw, e);
        }
    }

    private static Map<String, Object> getJson(String name, Map<String, Object> defaultValue) {
        String raw = System.getenv(name);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid JSON for " + name + ": " + raw, e);
        }
    }

    private static List<String> getList(String name, List<String> defaultValue) {
        String raw = System.getenv(name);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        raw = raw.trim();
        // Support JSON-style lists
        if (raw.startsWith("[") && raw.endsWith("]")) {
            try {
                List<?> parsed = objectMapper.readValue(raw, List.class);
                List<String> result = new ArrayList<>();
                for (Object item : parsed) {
                    if (item != null) {
                        result.add(item.toString().trim().replaceAll("^\"|\"$", ""));
                    }
                }
                return result;
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Invalid JSON list for " + name + ": " + raw, e);
            }
        }
        // Fallback: comma separated string
        List<String> values = new ArrayList<>();
        for (String item : raw.split(",")) {
            String cleaned = item.trim().replaceAll("^\"|\"$", "");
            if (!cleaned.isEmpty()) {
                values.add(cleaned);
            }
        }
        return values.isEmpty() ? defaultValue : values;
    }

    public static final Map<String, Object> CONFIG = Map.ofEntries(
            // Hyperliquid
            Map.entry("hyperliquidPrivateKey", getEnv("HYPERLIQUID_PRIVATE_KEY")),
            Map.entry("mnemonic", getEnv("MNEMONIC")),
            Map.entry("hyperliquidBaseUrl", getEnv("HYPERLIQUID_BASE_URL")),
            Map.entry("hyperliquidNetwork", getEnv("HYPERLIQUID_NETWORK", "mainnet")),
            Map.entry("hyperliquidVaultAddress", getEnv("HYPERLIQUID_VAULT_ADDRESS")),

            // LLM — Anthropic Claude API (primary)
            Map.entry("anthropicApiKey", getEnv("ANTHROPIC_API_KEY", null, true)),
            Map.entry("llmModel", getEnv("LLM_MODEL", "claude-sonnet-4-20250514")),
            Map.entry("sanitizeModel", getEnv("SANITIZE_MODEL", "claude-haiku-4-5-20251001")),
            Map.entry("maxTokens", getInt("MAX_TOKENS", 4096)),
            Map.entry("enableToolCalling", getBool("ENABLE_TOOL_CALLING", false)),

            // Extended thinking (Claude)
            Map.entry("thinkingEnabled", getBool("THINKING_ENABLED", false)),
            Map.entry("thinkingBudgetTokens", getInt("THINKING_BUDGET_TOKENS", 10000)),

            // Runtime controls
            Map.entry("assets", getEnv("ASSETS")),
            Map.entry("interval", getEnv("INTERVAL")),

            // Risk management
            Map.entry("maxPositionPct", getEnv("MAX_POSITION_PCT", "20")),
            Map.entry("maxLossPerPositionPct", getEnv("MAX_LOSS_PER_POSITION_PCT", "20")),
            Map.entry("maxLeverage", getEnv("MAX_LEVERAGE", "10")),
            Map.entry("maxTotalExposurePct", getEnv("MAX_TOTAL_EXPOSURE_PCT", "80")),
            Map.entry("dailyLossCircuitBreakerPct", getEnv("DAILY_LOSS_CIRCUIT_BREAKER_PCT", "25")),
            Map.entry("mandatorySlPct", getEnv("MANDATORY_SL_PCT", "5")),
            Map.entry("maxConcurrentPositions", getEnv("MAX_CONCURRENT_POSITIONS", "10")),
            Map.entry("minBalanceReservePct", getEnv("MIN_BALANCE_RESERVE_PCT", "10")),

            // API server
            Map.entry("apiHost", getEnv("API_HOST", "0.0.0.0")),
            Map.entry("apiPort", getEnv("APP_PORT") != null ? getEnv("APP_PORT") : getEnv("API_PORT", "3000")),

            // Legacy / optional
            Map.entry("taapiApiKey", getEnv("TAAPI_API_KEY")),
            Map.entry("openrouterApiKey", getEnv("OPENROUTER_API_KEY"))
    );
}
