package com.ai.tradingagent.service.trading;

import com.ai.tradingagent.config.ConfigLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * High-level CoinDCX exchange client with async retry helpers.
 */
@Service
public class CoinDCXApi {
    private static final Logger logger = LoggerFactory.getLogger(CoinDCXApi.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final String PUBLIC_BASE_URL = "https://public.coindcx.com";

    private String baseUrl;
    private String apiKey;
    private String apiSecret;

    @Autowired
    ConfigLoader configLoader;

    @PostConstruct
    private void initialize(){
        Map<String, Object> config = configLoader.getConfig();
        this.apiKey = (String) config.get("coindcxApiKey");
        this.apiSecret = (String) config.get("coindcxApiSecret");
        String baseUrl1 = (String) config.get("coindcxBaseUrl");
        if (baseUrl1 == null) {
            baseUrl1 = "https://api.coindcx.com";
        }
        this.baseUrl = baseUrl1;
    }

    private String generateSignature(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private CompletableFuture<String> postPrivate(String endpoint, Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = objectMapper.writeValueAsString(payload);
                String signature = generateSignature(body);

                RequestBody requestBody = RequestBody.create(body, MediaType.get("application/json"));
                Request request = new Request.Builder()
                        .url(baseUrl + endpoint)
                        .addHeader("X-AUTH-APIKEY", apiKey)
                        .addHeader("X-AUTH-SIGNATURE", signature)
                        //.addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP error: " + response.code() + " " + response.body().string());
                    }
                    return response.body().string();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<String> getPrivate(String endpoint, Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                        .url(baseUrl + endpoint)
                        .get()
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

    private CompletableFuture<String> getPublic(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                        .url(PUBLIC_BASE_URL + endpoint)
                        .get()
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

    public double roundSize(String asset, double amount) {
        // Simplified, assume 8 decimals
        return Math.round(amount * 100000000.0) / 100000000.0;
    }

    public CompletableFuture<Map<String, Object>> placeBuyOrder(String asset, double amount, double slippage) {
        amount = roundSize(asset, amount);
        Map<String, Object> payload = Map.of(
                "side", "buy",
                "order_type", "market",
                "market", asset + "_INR",
                "price_per_unit", 0.0, // Market order, but CoinDCX uses limit
                "total_quantity", amount,
                "timestamp", System.currentTimeMillis()
        );
        return postPrivate("/exchange/v1/orders/create", payload).thenApply(response -> Map.of("status", "ok"));
    }

    public CompletableFuture<Map<String, Object>> placeLimitBuyOrder(String asset, double amount, double slippage) {
        amount = roundSize(asset, amount);
        Map<String, Object> payload = Map.of(
                "side", "buy",
                "order_type", "limit_order",
                "market", asset + "_INR",
                "price_per_unit", 0.0, // Market order, but CoinDCX uses limit
                "total_quantity", amount,
                "timestamp", System.currentTimeMillis()
        );
        return postPrivate("/exchange/v1/orders/create", payload).thenApply(response -> Map.of("status", "ok"));
    }

    public CompletableFuture<Map<String, Object>> placeSellOrder(String asset, double amount, double slippage) {
        amount = roundSize(asset, amount);
        Map<String, Object> payload = Map.of(
                "side", "sell",
                "order_type", "market",
                "market", asset + "_INR",
                "price_per_unit", 0.0,
                "total_quantity", amount,
                "timestamp", System.currentTimeMillis()
        );
        return postPrivate("/exchange/v1/orders/create", payload).thenApply(response -> Map.of("status", "ok"));
    }

    public CompletableFuture<Map<String, Object>> placeLimitSellOrder(String asset, double amount, double slippage) {
        amount = roundSize(asset, amount);
        Map<String, Object> payload = Map.of(
                "side", "sell",
                "order_type", "limit_order",
                "market", asset + "_INR",
                "price_per_unit", 0.0,
                "total_quantity", amount,
                "timestamp", System.currentTimeMillis()
        );
        return postPrivate("/exchange/v1/orders/create", payload).thenApply(response -> Map.of("status", "ok"));
    }

    public CompletableFuture<Map<String, Object>> getUserState() {
        long timestamp = System.currentTimeMillis();
        return postPrivate("/exchange/v1/users/balances", Map.of("timestamp", timestamp)).thenApply(response -> {
            try {
                List<Map<String, Object>> balances = objectMapper.readValue(response, List.class);
                double totalBalance = 0.0;
                double balanceINR = 0.0;
                for (Map<String, Object> bal : balances) {
                    String currency = (String) bal.get("currency");
                    double qty = (Double) bal.get("balance");
                    double lockedBalance = (Double) bal.get("locked_balance");
                    if ("INR".equals(currency)) {
                        balanceINR = qty;
                    } else {
                        double currentPrice = getCurrentPrice(currency);
                        totalBalance = totalBalance + (qty * currentPrice);
                    }
                    // For other currencies, could add to positions if needed
                }
                return Map.of("balance", balanceINR, "total_value", totalBalance, "positions", new ArrayList<>());
            } catch (Exception e) {
                logger.error("Error parsing balances", e);
                return Map.of("balance", 0.0, "total_value", 0.0, "positions", new ArrayList<>());
            }
        });
    }

    public double getCurrentPrice(String asset) {
        List<Map<String, Object>> candles = getCandles(asset, "1m", 1).join();
        Object object = candles.get(0).get("close");
        if (object instanceof Number) {
            return ((Number) object).doubleValue();
        }
        return 0.0;
    }

    public CompletableFuture<List<Map<String, Object>>> getCandles(String asset, String interval, int count) {
        String market = asset + "_INR";
        String endpoint = "/market_data/candles?pair=I-" + market + "&interval=" + interval + "&limit=" + count;
        return getPublic(endpoint).thenApply(response -> {
            List<Map<String, Object>> trades = null;
            try {
                trades = objectMapper.readValue(response, List.class);
            } catch (Exception e) {
                logger.error("Error parsing balances", e);
            }
            return trades;
        });
    }

    public CompletableFuture<Object> getConvertedPrice() {
        long timestamp = System.currentTimeMillis();
        String endpoint = "/exchange/v1/derivatives/futures/data/conversions";
        return getPrivate(endpoint, null).thenApply(response -> {
            double inrPrice = 0.0;
            try {
                List<Map<String, Object>> trades = objectMapper.readValue(response, List.class);
                for (Map<String, Object> trade : trades) {
                    inrPrice = (Double) trade.get("conversion_price");
                }
            } catch (Exception e) {
                logger.error("Error parsing balances", e);
            }
            return inrPrice;
        });
    }
    // Other methods can be implemented similarly
}
