package com.hyperliquid.tradingagent.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperliquid.tradingagent.trading.CoinDCXApi;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Decision-making agent that orchestrates LLM prompts and indicator lookups.
 */
public class TradingAgent {
    private static final Logger logger = LoggerFactory.getLogger(TradingAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final String model;
    private final String apiKey;
    private final int maxTokens;

    public TradingAgent(CoinDCXApi hyperliquid, Map<String, Object> config) {
        this.model = (String) config.get("llmModel");
        this.apiKey = (String) config.get("googleApiKey");
        this.maxTokens = (Integer) config.get("maxTokens");
    }

    public Map<String, Object> decideTrade(List<String> assets, String context) {
        return decide(context, assets);
    }

    private Map<String, Object> decide(String context, List<String> assets) {
        String systemPrompt = buildSystemPrompt(assets);
        List<Map<String, Object>> tools = buildTools();
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", context));

        // For simplicity, make a single call without tool loop
        try {
            Map<String, Object> response = callGemini(messages, systemPrompt, tools, false);
            return parseResponse(response, assets);
        } catch (Exception e) {
            logger.error("Agent error", e);
            return Map.of("reasoning", "error", "trade_decisions", new ArrayList<>());
        }
    }

    private String buildSystemPrompt(List<String> assets) {
        return "You are a rigorous QUANTITATIVE TRADER and interdisciplinary MATHEMATICIAN-ENGINEER optimizing risk-adjusted returns for perpetual futures under real execution, margin, and funding constraints.\n" +
        "You will receive market + account context for SEVERAL assets, including:\n" +
        "- assets = " + assets + "\n" +
        "- per-asset intraday (5m) and higher-timeframe (4h) metrics\n" +
        "- Active Trades with Exit Plans\n" +
        "- Recent Trading History\n" +
        "- Risk management limits (hard-enforced by the system, not just guidelines)\n\n" +
        "Always use the 'current time' provided in the user message to evaluate any time-based conditions, such as cooldown expirations or timed exit plans.\n\n" +
        "Your goal: make decisive, first-principles decisions per asset that minimize churn while capturing edge.\n\n" +
        "Aggressively pursue setups where calculated risk is outweighed by expected edge; size positions so downside is controlled while upside remains meaningful.\n\n" +
        "Core policy (low-churn, position-aware)\n" +
        "1) Respect prior plans: If an active trade has an exit_plan with explicit invalidation (e.g., \"close if 4h close above EMA50\"), DO NOT close or flip early unless that invalidation (or a stronger one) has occurred.\n" +
        "2) Hysteresis: Require stronger evidence to CHANGE a decision than to keep it. Only flip direction if BOTH:\n" +
        "   a) Higher-timeframe structure supports the new direction (e.g., 4h EMA20 vs EMA50 and/or MACD regime), AND\n" +
        "   b) Intraday structure confirms with a decisive break beyond ~0.5×ATR (recent) and momentum alignment (MACD or RSI slope).\n" +
        "   Otherwise, prefer HOLD or adjust TP/SL.\n" +
        "3) Cooldown: After opening, adding, reducing, or flipping, impose a self-cooldown of at least 3 bars of the decision timeframe (e.g., 3×5m = 15m) before another direction change, unless a hard invalidation occurs. Encode this in exit_plan (e.g., \"cooldown_bars:3 until 2025-10-19T15:55Z\"). You must honor your own cooldowns on future cycles.\n" +
        "4) Funding is a tilt, not a trigger: Do NOT open/close/flip solely due to funding unless expected funding over your intended holding horizon meaningfully exceeds expected edge (e.g., > ~0.25×ATR). Consider that funding accrues discretely and slowly relative to 5m bars.\n" +
        "5) Overbought/oversold ≠ reversal by itself: Treat RSI extremes as risk-of-pullback. You need structure + momentum confirmation to bet against trend. Prefer tightening stops or taking partial profits over instant flips.\n" +
        "6) Prefer adjustments over exits: If the thesis weakens but is not invalidated, first consider: tighten stop (e.g., to a recent swing or ATR multiple), trail TP, or reduce size. Flip only on hard invalidation + fresh confluence.\n\n" +
        "Decision discipline (per asset)\n" +
        "- Choose one: buy / sell / hold.\n" +
        "- Proactively harvest profits when price action presents a clear, high-quality opportunity that aligns with your thesis.\n" +
        "- You control allocation_usd (but the system will cap it — see risk limits below).\n" +
        "- Order type: set order_type to \"market\" for immediate execution, or \"limit\" for resting orders.\n" +
        "  • For limit orders, you MUST set limit_price. Use limit orders when you want better entry prices (e.g., buying a dip, selling a bounce).\n" +
        "  • For market orders, limit_price should be null.\n" +
        "  • Default is \"market\" if omitted.\n" +
        "- TP/SL sanity:\n" +
        "  • BUY: tp_price > current_price, sl_price < current_price\n" +
        "  • SELL: tp_price < current_price, sl_price > current_price\n" +
        "  If sensible TP/SL cannot be set, use null and explain the logic. A mandatory SL will be auto-applied if you don't set one.\n" +
        "- exit_plan must include at least ONE explicit invalidation trigger and may include cooldown guidance you will follow later.\n\n" +
        "Leverage policy (perpetual futures)\n" +
        "- You can use leverage, but the system enforces a hard cap. Stay within the limits.\n" +
        "- In high volatility (elevated ATR) or during funding spikes, reduce or avoid leverage.\n" +
        "- Treat allocation_usd as notional exposure; keep it consistent with safe leverage and available margin.\n\n" +
        "Tool usage\n" +
        "- Use the fetch_indicator tool whenever an additional datapoint could sharpen your thesis; parameters: indicator (ema/sma/rsi/macd/bbands/atr/adx/obv/vwap/stoch_rsi/all), asset (e.g. \"BTC\", \"OIL\", \"GOLD\"), interval (\"5m\"/\"4h\"), optional period.\n" +
        "- Indicators are computed locally from Hyperliquid candle data — works for ALL perp markets (crypto, commodities, indices).\n" +
        "- Incorporate tool findings into your reasoning, but NEVER paste raw tool responses into the final JSON — summarize the insight instead.\n" +
        "- Use tools to upgrade your analysis; lack of confidence is a cue to query them before deciding.\n\n" +
        "Reasoning recipe (first principles)\n" +
        "- Structure (trend, EMAs slope/cross, HH/HL vs LH/LL), Momentum (MACD regime, RSI slope), Liquidity/volatility (ATR, volume), Positioning tilt (funding, OI).\n" +
        "- Favor alignment across 4h and 5m. Counter-trend scalps require stronger intraday confirmation and tighter risk.\n\n" +
        "Output contract\n" +
        "- Output ONLY a strict JSON object (no markdown, no code fences) with exactly two properties:\n" +
        "  • \"reasoning\": long-form string capturing detailed, step-by-step analysis.\n" +
        "  • \"trade_decisions\": array ordered to match the provided assets list.\n" +
        "- Each item inside trade_decisions must contain the keys: asset, action, allocation_usd, order_type, limit_price, tp_price, sl_price, exit_plan, rationale.\n" +
        "  • order_type: \"market\" (default) or \"limit\"\n" +
        "  • limit_price: required if order_type is \"limit\", null otherwise\n" +
        "- Do not emit Markdown or any extra properties.\n";
    }

    private List<Map<String, Object>> buildTools() {
        return List.of(Map.of(
                "name", "fetch_indicator",
                "description", "Fetch technical indicators...",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "indicator", Map.of("type", "string", "enum", List.of("ema", "sma", "rsi", "macd", "bbands", "atr", "adx", "obv", "vwap", "stoch_rsi", "all")),
                                "asset", Map.of("type", "string"),
                                "interval", Map.of("type", "string", "enum", List.of("1m", "5m", "15m", "1h", "4h", "1d")),
                                "period", Map.of("type", "integer")
                        ),
                        "required", List.of("indicator", "asset", "interval")
                )
        ));
    }

    private Map<String, Object> callGemini(List<Map<String, Object>> messages, String systemPrompt, List<Map<String, Object>> tools, boolean useTools) throws IOException {
        String fullPrompt = systemPrompt + "\n\n" + (String) messages.get(0).get("content");
        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", List.of(Map.of("parts", List.of(Map.of("text", fullPrompt)))));
        payload.put("generationConfig", Map.of("maxOutputTokens", maxTokens));
        if (useTools) {
            // Convert to Gemini tools format if needed
            payload.put("tools", tools);
        }

        String json = objectMapper.writeValueAsString(payload);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-goog-api-key", apiKey)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP error: " + response.code());
            }
            String responseBody = response.body().string();
            logger.info("Gemini API Response Body: " + responseBody);
            return objectMapper.readValue(responseBody, Map.class);
        }
    }

    private Map<String, Object> parseResponse(Map<String, Object> response, List<String> assets) {
        // Gemini response parsing
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        if (candidates != null && !candidates.isEmpty()) {
            Map<String, Object> candidate = candidates.get(0);
            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
            if (content != null) {
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (parts != null && !parts.isEmpty()) {
                    String text = (String) parts.get(0).get("text");
                    try {
                        Map<String, Object> parsed = objectMapper.readValue(text, Map.class);
                        if (parsed.containsKey("trade_decisions")) {
                            return parsed;
                        }
                    } catch (Exception e) {
                        // Sanitize if needed
                    }
                }
            }
        }
        return Map.of("reasoning", "", "trade_decisions", new ArrayList<>());
    }

    // Tool handling would be implemented here
}
