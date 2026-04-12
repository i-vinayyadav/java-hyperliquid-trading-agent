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
    private Double initialAccountValue = null;  // Tracks the initial account value, null until set

    @PostConstruct
    private void init() {
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
            double accountValue = (Double) state.getOrDefault("total_value", 0.0);
            if (initialAccountValue == null)
                initialAccountValue = accountValue;
            double totalReturnPct = (initialAccountValue != null && initialAccountValue != 0.0)
                    ? ((accountValue - initialAccountValue) / initialAccountValue * 100.0)
                    : 0.0;
            double balance = (Double) state.getOrDefault("balance", 0.0);
            List<Map<String, Object>> positions = (List<Map<String, Object>>) state.get("positions");

            Map<String, Object> dashboard = Map.of(
                    "total_return_pct", totalReturnPct,
                    "balance", balance,
                    "account_value", accountValue,
                    "positions", positions);
            /*To Do
                "total_return_pct": round(total_return_pct, 2),
                "balance": round_or_none(state['balance'], 2),
                "account_value": round_or_none(account_value, 2),
                "sharpe_ratio": round_or_none(sharpe, 3),
                "positions": positions,
                "active_trades": [
                    {
                        "asset": tr.get('asset'),
                        "is_long": tr.get('is_long'),
                        "amount": round_or_none(tr.get('amount'), 6),
                        "entry_price": round_or_none(tr.get('entry_price'), 2),
                        "tp_oid": tr.get('tp_oid'),
                        "sl_oid": tr.get('sl_oid'),
                        "exit_plan": tr.get('exit_plan'),
                        "opened_at": tr.get('opened_at')
                    }
                    for tr in active_trades
                ],
                "open_orders": open_orders_struct,
                "recent_diary": recent_diary,
                "recent_fills": recent_fills_struct,
            */

            // Market data
            List<Map<String, Object>> marketSections = new ArrayList<>();
            Map<String, Double> assetPrices = new HashMap<>();
            for (String asset : assets) {
                double currentPrice = coinDCXApi.getCurrentPrice(asset);
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
                    "account", dashboard,
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
                    logger.info("Risk Validation Failed: {}", riskValidationResult.reason);
                    continue;
                }
                String orderType = (String) output.get("order_type");
                if ("buy".equals(action)) {
                    double alloc = (Double) output.get("allocation_inr");
                    logger.info("Going to place buy order for order type: {}, asset: {}, allocation: {} and current price: {}",
                            orderType, asset, alloc, currentPrice);
                    /*if("market".equals(orderType)) {
                        coinDCXApi.placeBuyOrder(asset, alloc / currentPrice, 0.01).join();
                    }
                    else if ("limit".equals(orderType)) {
                        coinDCXApi.placeLimitBuyOrder(asset, alloc / currentPrice, 0.01).join();
                    }*/
                } else if ("sell".equals(action)) {
                    double alloc = (Double) output.get("allocation_inr");
                    logger.info("Going to place sell order for order type: {}, asset: {}, allocation: {} and current price: {}",
                            orderType, asset, alloc, currentPrice);
                    /*if("market".equals(orderType)) {
                        coinDCXApi.placeSellOrder(asset, alloc / currentPrice, 0.01).join();
                    }
                    else if ("limit".equals(orderType)) {
                        coinDCXApi.placeLimitSellOrder(asset, alloc / currentPrice, 0.01).join();
                    }*/
                }
            }

        } catch (Exception e) {
            logger.error("Loop error", e);
        }
    }
}
