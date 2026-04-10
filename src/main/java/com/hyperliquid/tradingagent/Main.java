package com.hyperliquid.tradingagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyperliquid.tradingagent.agent.TradingAgent;
import com.hyperliquid.tradingagent.config.ConfigLoader;
import com.hyperliquid.tradingagent.indicators.LocalIndicators;
import com.hyperliquid.tradingagent.risk.RiskManager;
import com.hyperliquid.tradingagent.trading.HyperliquidApi;
import com.hyperliquid.tradingagent.trading.CoinDCXApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Entry-point script that wires together the trading agent, data feeds, and API.
 */
@SpringBootApplication
@EnableScheduling
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final CoinDCXApi coindcx;
    private final TradingAgent agent;
    private final RiskManager riskMgr;

    private LocalDateTime startTime;
    private long invocationCount = 0;
    private List<Map<String, Object>> tradeLog = new ArrayList<>();
    private List<Map<String, Object>> activeTrades = new ArrayList<>();
    private Deque<String> recentEvents = new LinkedList<>();
    private List<String> assets;
    private String interval;

    public Main(ConfigLoader configLoader) {
        Map<String, Object> config = configLoader.getConfig();
        this.coindcx = new CoinDCXApi(config);
        this.agent = new TradingAgent(coindcx, config);
        this.riskMgr = new RiskManager(config);
        this.startTime = LocalDateTime.now(ZoneOffset.UTC);
        this.recentEvents = new LinkedList<>();
        // Load assets and interval from config
        String assetsStr = (String) config.get("assets");
        this.interval = (String) config.get("interval");
        if (assetsStr != null) {
            this.assets = Arrays.asList(assetsStr.split(","));
        } else {
            this.assets = Arrays.asList("BTC", "ETH");
        }
        if (this.interval == null) {
            this.interval = "5m";
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void runLoop() {
        invocationCount++;
        double minutesSinceStart = java.time.Duration.between(startTime, LocalDateTime.now(ZoneOffset.UTC)).toMinutes();

        // Simplified loop
        try {
            // Gather data
            Map<String, Object> state = coindcx.getUserState().join();
            double totalValue = (Double) state.getOrDefault("total_value", 0.0);
            double accountValue = totalValue;
            List<Map<String, Object>> positions = (List<Map<String, Object>>) state.get("positions");

            // Market data
            List<Map<String, Object>> marketSections = new ArrayList<>();
            Map<String, Double> assetPrices = new HashMap<>();
            for (String asset : assets) {
                double currentPrice = (Double) coindcx.getCurrentPrice(asset).join();
                assetPrices.put(asset, currentPrice);
                List<Map<String, Object>> candles5m = coindcx.getCandles(asset, "5m", 100).join();
                List<Map<String, Object>> candles4h = coindcx.getCandles(asset, "4h", 100).join();

                Map<String, List<Double>> intra = LocalIndicators.computeAll(candles5m);
                Map<String, List<Double>> lt = LocalIndicators.computeAll(candles4h);

                Map<String, Object> section = Map.of(
                        "asset", asset,
                        "current_price", currentPrice,
                        "intraday", Map.of(
                                "ema20", LocalIndicators.latest(intra.get("ema20")),
                                "rsi14", LocalIndicators.latest(intra.get("rsi14"))
                        ),
                        "long_term", Map.of(
                                "ema20", LocalIndicators.latest(lt.get("ema20")),
                                "rsi14", LocalIndicators.latest(lt.get("rsi14"))
                        )
                );
                marketSections.add(section);
            }

            // Context
            Map<String, Object> contextPayload = Map.of(
                    "invocation", Map.of("minutes_since_start", minutesSinceStart, "invocation_count", invocationCount),
                    "account", Map.of("total_return_pct", 0.0, "balance", state.get("balance"), "positions", positions),
                    "market_data", marketSections,
                    "instructions", Map.of("assets", assets, "requirement", "Decide actions for all assets.")
            );
            String context = objectMapper.writeValueAsString(contextPayload);

            // Decide
            Map<String, Object> outputs = agent.decideTrade(assets, context);
            List<Map<String, Object>> decisions = (List<Map<String, Object>>) outputs.get("trade_decisions");

            // Execute (simplified)
            for (Map<String, Object> output : decisions) {
                String action = (String) output.get("action");
                String asset = (String) output.get("asset");
                double currentPrice = assetPrices.get(asset);
                if ("buy".equals(action)) {
                    double alloc = (Double) output.get("allocation_usd");
                    coindcx.placeBuyOrder(asset, alloc / currentPrice, 0.01).join();
                } else if ("sell".equals(action)) {
                    double alloc = (Double) output.get("allocation_usd");
                    coindcx.placeSellOrder(asset, alloc / currentPrice, 0.01).join();
                }
            }

        } catch (Exception e) {
            logger.error("Loop error", e);
        }
    }
}
