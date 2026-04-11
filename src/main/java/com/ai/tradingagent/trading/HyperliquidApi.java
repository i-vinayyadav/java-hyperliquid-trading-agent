package com.ai.tradingagent.trading;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Bip44WalletUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * High-level Hyperliquid exchange client with async retry helpers.
 */
public class HyperliquidApi {
    private static final Logger logger = LoggerFactory.getLogger(HyperliquidApi.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final String baseUrl;
    private final Credentials wallet;
    private final String accountAddress;
    private final String queryAddress;

    private List<Map<String, Object>> metaCache = null;
    private Map<String, List<Object>> hip3MetaCache = new HashMap<>();

    public HyperliquidApi(Map<String, Object> config) {
        String privateKey = (String) config.get("hyperliquidPrivateKey");
        String mnemonic = (String) config.get("mnemonic");
        if (privateKey != null) {
            this.wallet = Credentials.create(privateKey);
        } else if (mnemonic != null) {
            // Generate credentials from mnemonic using BIP44 standard derivation
            // BIP44 path: m/44'/60'/0'/0/0 (Ethereum standard)
            // The boolean parameter indicates if using testnet (false = mainnet)
            this.wallet = Bip44WalletUtils.loadBip44Credentials("", mnemonic, false);
        } else {
            throw new RuntimeException("Either HYPERLIQUID_PRIVATE_KEY or MNEMONIC must be provided");
        }

        String network = (String) config.getOrDefault("hyperliquidNetwork", "mainnet");
        String baseUrl = (String) config.get("hyperliquidBaseUrl");
        if (baseUrl == null) {
            if ("testnet".equals(network)) {
                baseUrl = "https://api.hyperliquid-testnet.xyz";
            } else {
                baseUrl = "https://api.hyperliquid.xyz";
            }
        }
        this.baseUrl = baseUrl;

        this.accountAddress = (String) config.get("hyperliquidVaultAddress");
        this.queryAddress = this.accountAddress != null ? this.accountAddress : wallet.getAddress();
    }

    private <T> CompletableFuture<T> retry(CompletableFuture<T> future, int maxAttempts, long backoffMs) {
        return future.handle((result, ex) -> {
            if (ex == null) {
                return CompletableFuture.completedFuture(result);
            }
            if (maxAttempts > 1) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return retry(future, maxAttempts - 1, backoffMs * 2);
            }
            throw new RuntimeException(ex);
        }).thenCompose(f -> f);
    }

    public double roundSize(String asset, double amount) {
        // Simplified, assume 8 decimals
        return Math.round(amount * 100000000.0) / 100000000.0;
    }

    public CompletableFuture<Map<String, Object>> placeBuyOrder(String asset, double amount, double slippage) {
        amount = roundSize(asset, amount);
        Map<String, Object> order = Map.of(
                "asset", asset,
                "isBuy", true,
                "amount", amount,
                "slippage", slippage
        );
        return placeOrder(order);
    }

    public CompletableFuture<Map<String, Object>> placeSellOrder(String asset, double amount, double slippage) {
        amount = roundSize(asset, amount);
        Map<String, Object> order = Map.of(
                "asset", asset,
                "isBuy", false,
                "amount", amount,
                "slippage", slippage
        );
        return placeOrder(order);
    }

    private CompletableFuture<Map<String, Object>> placeOrder(Map<String, Object> order) {
        // Implement order placement via REST API
        // This is a placeholder; actual implementation would require signing and posting to exchange API
        return CompletableFuture.completedFuture(Map.of("status", "ok"));
    }

    public CompletableFuture<Map<String, Object>> placeLimitBuy(String asset, double amount, double limitPrice, String tif) {
        amount = roundSize(asset, amount);
        Map<String, Object> order = Map.of(
                "asset", asset,
                "isBuy", true,
                "amount", amount,
                "limitPrice", limitPrice,
                "tif", tif
        );
        return placeOrder(order);
    }

    public CompletableFuture<Map<String, Object>> placeLimitSell(String asset, double amount, double limitPrice, String tif) {
        amount = roundSize(asset, amount);
        Map<String, Object> order = Map.of(
                "asset", asset,
                "isBuy", false,
                "amount", amount,
                "limitPrice", limitPrice,
                "tif", tif
        );
        return placeOrder(order);
    }

    public CompletableFuture<Map<String, Object>> placeTakeProfit(String asset, boolean isBuy, double amount, double tpPrice) {
        amount = roundSize(asset, amount);
        Map<String, Object> order = Map.of(
                "asset", asset,
                "isBuy", !isBuy,
                "amount", amount,
                "tpPrice", tpPrice,
                "reduceOnly", true
        );
        return placeOrder(order);
    }

    public CompletableFuture<Map<String, Object>> placeStopLoss(String asset, boolean isBuy, double amount, double slPrice) {
        amount = roundSize(asset, amount);
        Map<String, Object> order = Map.of(
                "asset", asset,
                "isBuy", !isBuy,
                "amount", amount,
                "slPrice", slPrice,
                "reduceOnly", true
        );
        return placeOrder(order);
    }

    public CompletableFuture<Map<String, Object>> cancelOrder(String asset, long oid) {
        // Implement cancel
        return CompletableFuture.completedFuture(Map.of("status", "ok"));
    }

    public CompletableFuture<Map<String, Object>> cancelAllOrders(String asset) {
        // Implement cancel all
        return CompletableFuture.completedFuture(Map.of("status", "ok"));
    }

    public CompletableFuture<List<Map<String, Object>>> getOpenOrders() {
        // Fetch open orders
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    public CompletableFuture<List<Map<String, Object>>> getRecentFills(int limit) {
        // Fetch recent fills
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    public CompletableFuture<Map<String, Object>> getUserState() {
        // Fetch user state
        return CompletableFuture.completedFuture(Map.of("balance", 0.0, "total_value", 0.0, "positions", new ArrayList<>()));
    }

    public CompletableFuture<Double> getCurrentPrice(String asset) {
        // Fetch current price
        return CompletableFuture.completedFuture(0.0);
    }

    public CompletableFuture<List<Object>> getMetaAndCtxs(String dex) {
        // Fetch meta
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    public CompletableFuture<Double> getOpenInterest(String asset) {
        // Fetch OI
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<List<Map<String, Object>>> getCandles(String asset, String interval, int count) {
        // Fetch candles
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    public CompletableFuture<Double> getFundingRate(String asset) {
        // Fetch funding
        return CompletableFuture.completedFuture(null);
    }

    // Helper methods for HTTP requests
    private CompletableFuture<String> post(String endpoint, Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(payload);
                RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
                Request request = new Request.Builder()
                        .url(baseUrl + endpoint)
                        .post(body)
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP error: " + response.code());
                    }
                    return response.body().string();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
