package com.ai.tradingagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Centralized configuration loading using Spring @Value annotations.
 */
@Configuration
public class ConfigLoader {

    // Hyperliquid
    @Value("${HYPERLIQUID_PRIVATE_KEY:#{null}}")
    private String hyperliquidPrivateKey;

    @Value("${MNEMONIC:#{null}}")
    private String mnemonic;

    @Value("${HYPERLIQUID_BASE_URL:#{null}}")
    private String hyperliquidBaseUrl;

    @Value("${HYPERLIQUID_NETWORK:mainnet}")
    private String hyperliquidNetwork;

    @Value("${HYPERLIQUID_VAULT_ADDRESS:#{null}}")
    private String hyperliquidVaultAddress;

    // LLM — Google Gemini API
    @Value("${GOOGLE_API_KEY:#{null}}")
    private String googleApiKey;

    @Value("${LLM_MODEL:gemini-flash-latest}")
    private String llmModel;

    @Value("${SANITIZE_MODEL:gemini-flash-latest}")
    private String sanitizeModel;

    @Value("${MAX_TOKENS:4096}")
    private int maxTokens;

    @Value("${ENABLE_TOOL_CALLING:false}")
    private boolean enableToolCalling;

    // Extended thinking
    @Value("${THINKING_ENABLED:false}")
    private boolean thinkingEnabled;

    @Value("${THINKING_BUDGET_TOKENS:10000}")
    private int thinkingBudgetTokens;

    // Runtime controls
    @Value("${ASSETS:#{null}}")
    private String assets;

    @Value("${INTERVAL:#{null}}")
    private String interval;

    // Risk management
    @Value("${MAX_POSITION_PCT:20}")
    private String maxPositionPct;

    @Value("${MAX_LOSS_PER_POSITION_PCT:20}")
    private String maxLossPerPositionPct;

    @Value("${MAX_LEVERAGE:10}")
    private String maxLeverage;

    @Value("${MAX_TOTAL_EXPOSURE_PCT:80}")
    private String maxTotalExposurePct;

    @Value("${DAILY_LOSS_CIRCUIT_BREAKER_PCT:25}")
    private String dailyLossCircuitBreakerPct;

    @Value("${MANDATORY_SL_PCT:5}")
    private String mandatorySlPct;

    @Value("${MAX_CONCURRENT_POSITIONS:10}")
    private String maxConcurrentPositions;

    @Value("${MIN_BALANCE_RESERVE_PCT:10}")
    private String minBalanceReservePct;

    // API server
    @Value("${API_HOST:0.0.0.0}")
    private String apiHost;

    @Value("${APP_PORT:3000}")
    private String apiPort;

    // Legacy / optional
    @Value("${TAAPI_API_KEY:#{null}}")
    private String taapiApiKey;

    @Value("${OPENROUTER_API_KEY:#{null}}")
    private String openrouterApiKey;

    // CoinDCX
    @Value("${COINDCX_API_KEY:#{null}}")
    private String coindcxApiKey;

    @Value("${COINDCX_API_SECRET:#{null}}")
    private String coindcxApiSecret;

    @Value("${COINDCX_BASE_URL:#{null}}")
    private String coindcxBaseUrl;

    public Map<String, Object> getConfig() {
        return Map.ofEntries(
                // Hyperliquid
                Map.entry("hyperliquidPrivateKey", hyperliquidPrivateKey),
                Map.entry("mnemonic", mnemonic),
                Map.entry("hyperliquidBaseUrl", hyperliquidBaseUrl),
                Map.entry("hyperliquidNetwork", hyperliquidNetwork),
                Map.entry("hyperliquidVaultAddress", hyperliquidVaultAddress),

                // LLM — Google Gemini API (primary)
                Map.entry("googleApiKey", googleApiKey),
                Map.entry("llmModel", llmModel),
                Map.entry("sanitizeModel", sanitizeModel),
                Map.entry("maxTokens", maxTokens),
                Map.entry("enableToolCalling", enableToolCalling),

                // Extended thinking (Claude)
                Map.entry("thinkingEnabled", thinkingEnabled),
                Map.entry("thinkingBudgetTokens", thinkingBudgetTokens),

                // Runtime controls
                Map.entry("assets", assets),
                Map.entry("interval", interval),

                // Risk management
                Map.entry("maxPositionPct", maxPositionPct),
                Map.entry("maxLossPerPositionPct", maxLossPerPositionPct),
                Map.entry("maxLeverage", maxLeverage),
                Map.entry("maxTotalExposurePct", maxTotalExposurePct),
                Map.entry("dailyLossCircuitBreakerPct", dailyLossCircuitBreakerPct),
                Map.entry("mandatorySlPct", mandatorySlPct),
                Map.entry("maxConcurrentPositions", maxConcurrentPositions),
                Map.entry("minBalanceReservePct", minBalanceReservePct),

                // API server
                Map.entry("apiHost", apiHost),
                Map.entry("apiPort", apiPort),

                // Legacy / optional
                Map.entry("taapiApiKey", taapiApiKey),
                Map.entry("openrouterApiKey", openrouterApiKey),

                // CoinDCX
                Map.entry("coindcxApiKey", coindcxApiKey),
                Map.entry("coindcxApiSecret", coindcxApiSecret),
                Map.entry("coindcxBaseUrl", coindcxBaseUrl)
        );
    }
}
