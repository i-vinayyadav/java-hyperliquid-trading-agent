package com.ai.tradingagent.schedular;

import com.ai.tradingagent.service.agent.TradingAgent;
import com.ai.tradingagent.config.ConfigLoader;
import com.ai.tradingagent.service.indicators.LocalIndicators;
import com.ai.tradingagent.service.risk.RiskManager;
import com.ai.tradingagent.service.trading.CoinDCXApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Entry-point script that wires together the trading agent, data feeds, and API.
 */
@Component
public class Schedular {
    private static final Logger logger = LoggerFactory.getLogger(Schedular.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    ConfigLoader configLoader;

    @Autowired
    private RiskManager riskManager;

//    @Autowired
//    private LocalIndicators localIndicators;

    @Autowired
    CoinDCXApi coinDCXApi;

    @Autowired
    TradingAgent tradingAgent;

    private LocalDateTime startTime;
    private long invocationCount = 0;
    private List<Map<String, Object>> tradeLog = new ArrayList<>();
    private List<Map<String, Object>> activeTrades = new ArrayList<>();
    private Deque<String> recentEvents = new LinkedList<>();
    private List<String> assets;
    private String interval;

    @PostConstruct
    private void init(){
        Map<String, Object> config = configLoader.getConfig();
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

    //Main trading loop that gathers data, calls the agent, and executes trades.
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void runLoop() {
        logger.info("Starting trading agent for assets: {} at interval: {}", assets.toString(), interval);
        invocationCount++;
        double minutesSinceStart = Duration.between(startTime, LocalDateTime.now(ZoneOffset.UTC)).toMinutes();

        // Simplified loop
        try {
            // Gather data
            Map<String, Object> state = coinDCXApi.getUserState().join();
            double totalValue = (Double) state.getOrDefault("total_value", 0.0);
            double accountValue = totalValue;
            double balance = (Double) state.getOrDefault("balance", 0.0);
            List<Map<String, Object>> positions = (List<Map<String, Object>>) state.get("positions");

            // Market data
            List<Map<String, Object>> marketSections = new ArrayList<>();
            Map<String, Double> assetPrices = new HashMap<>();
            for (String asset : assets) {
                double currentPrice = (Double) coinDCXApi.getCurrentPrice(asset).join();
                assetPrices.put(asset, currentPrice);
                List<Map<String, Object>> candles5m = coinDCXApi.getCandles(asset, "5m", 100).join();
                List<Map<String, Object>> candles4h = coinDCXApi.getCandles(asset, "4h", 100).join();

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
                    "invocation", Map.of(
                            "minutes_since_start", minutesSinceStart,
                            "current_time", System.currentTimeMillis(),
                            "invocation_count", invocationCount),
                    "account", Map.of(
                            "total_return_pct", 0.0,
                            "balance", balance,
                            "positions", positions),
                    "risk_limits", riskManager.getRiskSummary(),
                    "market_data", marketSections,
                    "instructions", Map.of(
                            "assets", assets,
                            "requirement", "Decide actions for all assets and return a strict JSON object matching the schema.")
            );
            String context = objectMapper.writeValueAsString(contextPayload);

            // Decide
            Map<String, Object> outputs = tradingAgent.decideTrade(assets, context);
            List<Map<String, Object>> decisions = (List<Map<String, Object>>) outputs.get("trade_decisions");

            // Execute (simplified)
            for (Map<String, Object> output : decisions) {
                String action = (String) output.get("action");
                String asset = (String) output.get("asset");
                double currentPrice = assetPrices.get(asset);
                //--- RISK: Validate trade before execution ---
                RiskManager.RiskValidationResult riskValidationResult = riskManager.validateTrade(output, state, balance);
                if (!riskValidationResult.allowed) {
                    System.out.println("Risk Validation Failed");
                    logger.info("Risk Validation Failed: " + riskValidationResult.reason);
                    continue;
                }
                if ("buy".equals(action)) {
                    double alloc = (Double) output.get("allocation_usd");
                    coinDCXApi.placeBuyOrder(asset, alloc / currentPrice, 0.01).join();
                } else if ("sell".equals(action)) {
                    double alloc = (Double) output.get("allocation_usd");
                    coinDCXApi.placeSellOrder(asset, alloc / currentPrice, 0.01).join();
                }
            }

        } catch (Exception e) {
            logger.error("Loop error", e);
        }
    }
}
