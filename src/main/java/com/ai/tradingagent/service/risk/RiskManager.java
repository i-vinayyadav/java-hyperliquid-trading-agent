package com.ai.tradingagent.service.risk;

import com.ai.tradingagent.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;

/**
 * Enforces risk limits on every trade before execution.
 */
@Service
public class RiskManager {
    private static final Logger logger = LoggerFactory.getLogger(RiskManager.class);

    private double maxPositionPct;
    private double maxLossPerPositionPct;
    private double maxLeverage;
    private double maxTotalExposurePct;
    private double dailyLossCircuitBreakerPct;
    private double mandatorySlPct;
    private int maxConcurrentPositions;
    private double minBalanceReservePct;

    // Daily tracking
    private Double dailyHighValue = null;
    private LocalDate dailyHighDate = null;
    private boolean circuitBreakerActive = false;
    private LocalDate circuitBreakerDate = null;

    @Autowired
    ConfigLoader configLoader;

    @PostConstruct
    private void initialize() {
        Map<String, Object> config = configLoader.getConfig();
        this.maxPositionPct = Double.parseDouble((String) config.get("maxPositionPct"));
        this.maxLossPerPositionPct = Double.parseDouble((String) config.get("maxLossPerPositionPct"));
        this.maxLeverage = Double.parseDouble((String) config.get("maxLeverage"));
        this.maxTotalExposurePct = Double.parseDouble((String) config.get("maxTotalExposurePct"));
        this.dailyLossCircuitBreakerPct = Double.parseDouble((String) config.get("dailyLossCircuitBreakerPct"));
        this.mandatorySlPct = Double.parseDouble((String) config.get("mandatorySlPct"));
        this.maxConcurrentPositions = Integer.parseInt((String) config.get("maxConcurrentPositions"));
        this.minBalanceReservePct = Double.parseDouble((String) config.get("minBalanceReservePct"));
    }

    private void resetDailyIfNeeded(double accountValue) {
        LocalDate today = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate();
        if (!today.equals(dailyHighDate)) {
            dailyHighValue = accountValue;
            dailyHighDate = today;
            circuitBreakerActive = false;
            circuitBreakerDate = null;
        } else if (accountValue > dailyHighValue) {
            dailyHighValue = accountValue;
        }
    }

    // Individual checks — each returns (allowed: bool, reason: str)

    public RiskCheckResult checkPositionSize(double allocUsd, double accountValue) {
        if (accountValue <= 0) {
            return new RiskCheckResult(false, "Account value is zero or negative");
        }
        double maxAlloc = accountValue * (maxPositionPct / 100.0);
        if (allocUsd > maxAlloc) {
            return new RiskCheckResult(false,
                    String.format("Allocation $%.2f exceeds %.1f%% of account ($%.2f)",
                            allocUsd, maxPositionPct, maxAlloc));
        }
        return new RiskCheckResult(true, "");
    }

    public RiskCheckResult checkTotalExposure(List<Map<String, Object>> positions, double newAlloc, double accountValue) {
        double currentExposure = 0.0;
        for (Map<String, Object> pos : positions) {
            double qty = Math.abs(safeDouble(pos.get("quantity")) + safeDouble(pos.get("szi")));
            double entry = safeDouble(pos.get("entry_price")) + safeDouble(pos.get("entryPx"));
            currentExposure += qty * entry;
        }
        double total = currentExposure + newAlloc;
        double maxExposure = accountValue * (maxTotalExposurePct / 100.0);
        if (total > maxExposure) {
            return new RiskCheckResult(false,
                    String.format("Total exposure $%.2f would exceed %.1f%% of account ($%.2f)",
                            total, maxTotalExposurePct, maxExposure));
        }
        return new RiskCheckResult(true, "");
    }

    public RiskCheckResult checkLeverage(double allocUsd, double balance) {
        if (balance <= 0) {
            return new RiskCheckResult(false, "Balance is zero or negative");
        }
        double effectiveLev = allocUsd / balance;
        if (effectiveLev > maxLeverage) {
            return new RiskCheckResult(false,
                    String.format("Effective leverage %.1f x exceeds max %.1f x", effectiveLev, maxLeverage));
        }
        return new RiskCheckResult(true, "");
    }

    public RiskCheckResult checkDailyDrawdown(double accountValue) {
        resetDailyIfNeeded(accountValue);
        if (circuitBreakerActive) {
            return new RiskCheckResult(false, "Daily loss circuit breaker is active — no new trades until tomorrow (UTC)");
        }
        if (dailyHighValue != null && dailyHighValue > 0) {
            double drawdownPct = ((dailyHighValue - accountValue) / dailyHighValue) * 100;
            if (drawdownPct >= dailyLossCircuitBreakerPct) {
                circuitBreakerActive = true;
                circuitBreakerDate = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate();
                return new RiskCheckResult(false,
                        String.format("Daily drawdown %.2f%% exceeds circuit breaker threshold of %.1f%%",
                                drawdownPct, dailyLossCircuitBreakerPct));
            }
        }
        return new RiskCheckResult(true, "");
    }

    public RiskCheckResult checkConcurrentPositions(int currentCount) {
        if (currentCount >= maxConcurrentPositions) {
            return new RiskCheckResult(false,
                    String.format("Already at max concurrent positions (%d)", maxConcurrentPositions));
        }
        return new RiskCheckResult(true, "");
    }

    public RiskCheckResult checkBalanceReserve(double balance, double initialBalance) {
        if (initialBalance <= 0) {
            return new RiskCheckResult(true, "");
        }
        double minBalance = initialBalance * (minBalanceReservePct / 100.0);
        if (balance < minBalance) {
            return new RiskCheckResult(false,
                    String.format("Balance $%.2f below minimum reserve $%.2f (%.1f%% of initial)",
                            balance, minBalance, minBalanceReservePct));
        }
        return new RiskCheckResult(true, "");
    }

    // Stop-loss enforcement

    public Double enforceStopLoss(Double slPrice, double entryPrice, boolean isBuy) {
        if (slPrice != null) {
            return slPrice;
        }
        // Auto-set SL at mandatory_sl_pct from entry
        double slDistance = entryPrice * (mandatorySlPct / 100.0);
        if (isBuy) {
            return Math.round((entryPrice - slDistance) * 100.0) / 100.0;
        } else {
            return Math.round((entryPrice + slDistance) * 100.0) / 100.0;
        }
    }

    // Force-close losing positions

    public List<Map<String, Object>> checkLosingPositions(List<Map<String, Object>> positions) {
        List<Map<String, Object>> toClose = new java.util.ArrayList<>();
        for (Map<String, Object> pos : positions) {
            String coin = (String) pos.getOrDefault("coin", pos.get("symbol"));
            double entryPx = safeDouble(pos.get("entryPx")) + safeDouble(pos.get("entry_price"));
            double size = safeDouble(pos.get("szi")) + safeDouble(pos.get("quantity"));
            double pnl = safeDouble(pos.get("pnl")) + safeDouble(pos.get("unrealized_pnl"));

            if (entryPx == 0 || size == 0) {
                continue;
            }

            double notional = Math.abs(size) * entryPx;
            if (notional == 0) {
                continue;
            }

            double lossPct = pnl < 0 ? Math.abs(pnl / notional) * 100 : 0;

            if (lossPct >= maxLossPerPositionPct) {
                logger.warn("RISK: Force-closing {} — loss {:.2f}% exceeds {:.2f}%", coin, lossPct, maxLossPerPositionPct);
                Map<String, Object> closeInfo = Map.of(
                        "coin", coin,
                        "size", Math.abs(size),
                        "is_long", size > 0,
                        "loss_pct", Math.round(lossPct * 100.0) / 100.0,
                        "pnl", Math.round(pnl * 100.0) / 100.0
                );
                toClose.add(closeInfo);
            }
        }
        return toClose;
    }

    // Composite validation — run all checks before a trade

    public RiskValidationResult validateTrade(Map<String, Object> trade, Map<String, Object> accountState, double initialBalance) {
        String action = (String) trade.get("action");
        if ("hold".equals(action)) {
            return new RiskValidationResult(true, "", trade);
        }

        double allocUsd = safeDouble(trade.get("allocation_usd"));
        if (allocUsd <= 0) {
            return new RiskValidationResult(false, "Zero or negative allocation", trade);
        }

        // Hyperliquid minimum order size is $10
        if (allocUsd < 11.0) {
            allocUsd = 11.0;
            trade = new java.util.HashMap<>(trade);
            trade.put("allocation_usd", allocUsd);
            logger.info("RISK: Bumped allocation to $11 (Hyperliquid $10 minimum)");
        }

        double accountValue = safeDouble(accountState.get("total_value"));
        double balance = safeDouble(accountState.get("balance"));
        List<Map<String, Object>> positions = (List<Map<String, Object>>) accountState.get("positions");
        boolean isBuy = "buy".equals(action);

        // 1. Daily drawdown circuit breaker
        RiskCheckResult result = checkDailyDrawdown(accountValue);
        if (!result.allowed) {
            return new RiskValidationResult(false, result.reason, trade);
        }

        // 2. Balance reserve
        result = checkBalanceReserve(balance, initialBalance);
        if (!result.allowed) {
            return new RiskValidationResult(false, result.reason, trade);
        }

        // 3. Position size limit
        result = checkPositionSize(allocUsd, accountValue);
        if (!result.allowed) {
            // Cap allocation instead of rejecting
            double maxAlloc = accountValue * (maxPositionPct / 100.0);
            // But never below Hyperliquid's $10 minimum
            if (maxAlloc < 11.0) {
                maxAlloc = 11.0;
            }
            logger.warn("RISK: Capping allocation from ${} to ${}", allocUsd, maxAlloc);
            allocUsd = maxAlloc;
            trade = new java.util.HashMap<>(trade);
            trade.put("allocation_usd", allocUsd);
        }

        // 4. Total exposure
        result = checkTotalExposure(positions, allocUsd, accountValue);
        if (!result.allowed) {
            return new RiskValidationResult(false, result.reason, trade);
        }

        // 5. Leverage check
        result = checkLeverage(allocUsd, balance);
        if (!result.allowed) {
            return new RiskValidationResult(false, result.reason, trade);
        }

        // 6. Concurrent positions
        int activeCount = (int) positions.stream()
                .filter(p -> Math.abs(safeDouble(p.get("szi")) + safeDouble(p.get("quantity"))) > 0)
                .count();
        result = checkConcurrentPositions(activeCount);
        if (!result.allowed) {
            return new RiskValidationResult(false, result.reason, trade);
        }

        // 7. Enforce mandatory stop-loss
        double currentPrice = safeDouble(trade.get("current_price"));
        double entryPrice = currentPrice > 0 ? currentPrice : 1.0;
        Double slPrice = (Double) trade.get("sl_price");
        Double enforcedSl = enforceStopLoss(slPrice, entryPrice, isBuy);
        if (slPrice == null) {
            logger.info("RISK: Auto-setting SL at {} ({}% from entry)", enforcedSl, mandatorySlPct);
        }
        trade = new java.util.HashMap<>(trade);
        trade.put("sl_price", enforcedSl);

        return new RiskValidationResult(true, "", trade);
    }

    public Map<String, Object> getRiskSummary() {
        return Map.of(
                "max_position_pct", maxPositionPct,
                "max_loss_per_position_pct", maxLossPerPositionPct,
                "max_leverage", maxLeverage,
                "max_total_exposure_pct", maxTotalExposurePct,
                "daily_loss_circuit_breaker_pct", dailyLossCircuitBreakerPct,
                "mandatory_sl_pct", mandatorySlPct,
                "max_concurrent_positions", maxConcurrentPositions,
                "min_balance_reserve_pct", minBalanceReservePct,
                "circuit_breaker_active", circuitBreakerActive
        );
    }

    private double safeDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return 0.0;
    }

    public static class RiskCheckResult {
        public final boolean allowed;
        public final String reason;

        public RiskCheckResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
    }

    public static class RiskValidationResult {
        public final boolean allowed;
        public final String reason;
        public final Map<String, Object> adjustedTrade;

        public RiskValidationResult(boolean allowed, String reason, Map<String, Object> adjustedTrade) {
            this.allowed = allowed;
            this.reason = reason;
            this.adjustedTrade = adjustedTrade;
        }
    }
}
