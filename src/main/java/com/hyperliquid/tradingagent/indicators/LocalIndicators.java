package com.hyperliquid.tradingagent.indicators;

import java.util.*;

/**
 * Local technical indicator computation from OHLCV candle data.
 */
public class LocalIndicators {

    private static List<Double> closes(List<Map<String, Object>> candles) {
        List<Double> result = new ArrayList<>();
        for (Map<String, Object> c : candles) {
            result.add(safeDouble(c.get("close")));
        }
        return result;
    }

    private static List<Double> highs(List<Map<String, Object>> candles) {
        List<Double> result = new ArrayList<>();
        for (Map<String, Object> c : candles) {
            result.add(safeDouble(c.get("high")));
        }
        return result;
    }

    private static List<Double> lows(List<Map<String, Object>> candles) {
        List<Double> result = new ArrayList<>();
        for (Map<String, Object> c : candles) {
            result.add(safeDouble(c.get("low")));
        }
        return result;
    }

    private static List<Double> volumes(List<Map<String, Object>> candles) {
        List<Double> result = new ArrayList<>();
        for (Map<String, Object> c : candles) {
            result.add(safeDouble(c.get("volume")));
        }
        return result;
    }

    // EMA / SMA

    public static List<Double> sma(List<Double> values, int period) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (i < period - 1) {
                result.add(null);
            } else {
                double sum = 0;
                for (int j = i - period + 1; j <= i; j++) {
                    sum += values.get(j);
                }
                result.add(sum / period);
            }
        }
        return result;
    }

    public static List<Double> ema(List<Double> values, int period) {
        List<Double> result = new ArrayList<>();
        double k = 2.0 / (period + 1);
        Double prev = null;
        for (int i = 0; i < values.size(); i++) {
            if (i < period - 1) {
                result.add(null);
            } else if (i == period - 1) {
                prev = values.subList(0, period).stream().mapToDouble(Double::doubleValue).sum() / period;
                result.add(prev);
            } else {
                prev = values.get(i) * k + prev * (1 - k);
                result.add(prev);
            }
        }
        return result;
    }

    // RSI

    public static List<Double> rsi(List<Map<String, Object>> candles, int period) {
        List<Double> closes = closes(candles);
        if (closes.size() < period + 1) {
            return Collections.nCopies(closes.size(), null);
        }

        List<Double> deltas = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            deltas.add(closes.get(i) - closes.get(i - 1));
        }

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < period; i++) {
            result.add(null);
        }

        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();
        for (int i = 0; i < period; i++) {
            double d = deltas.get(i);
            gains.add(Math.max(d, 0));
            losses.add(Math.abs(Math.min(d, 0)));
        }
        double avgGain = gains.stream().mapToDouble(Double::doubleValue).sum() / period;
        double avgLoss = losses.stream().mapToDouble(Double::doubleValue).sum() / period;

        if (avgLoss == 0) {
            result.add(100.0);
        } else {
            double rs = avgGain / avgLoss;
            result.add(Math.round((100.0 - (100.0 / (1.0 + rs))) * 10000) / 10000.0);
        }

        for (int i = period; i < deltas.size(); i++) {
            double gain = Math.max(deltas.get(i), 0);
            double loss = Math.abs(Math.min(deltas.get(i), 0));
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            if (avgLoss == 0) {
                result.add(100.0);
            } else {
                double rs = avgGain / avgLoss;
                result.add(Math.round((100.0 - (100.0 / (1.0 + rs))) * 10000) / 10000.0);
            }
        }

        return result;
    }

    // MACD

    public static Map<String, List<Double>> macd(List<Map<String, Object>> candles, int fast, int slow, int signal) {
        List<Double> closes = closes(candles);
        List<Double> emaFast = ema(closes, fast);
        List<Double> emaSlow = ema(closes, slow);

        List<Double> macdLine = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            Double f = emaFast.get(i);
            Double s = emaSlow.get(i);
            if (f != null && s != null) {
                macdLine.add(Math.round((f - s) * 1000000) / 1000000.0);
            } else {
                macdLine.add(null);
            }
        }

        // Signal line
        List<Double> validMacd = new ArrayList<>();
        for (Double v : macdLine) {
            if (v != null) validMacd.add(v);
        }
        List<Double> signalLineRaw = validMacd.size() >= signal ? ema(validMacd, signal) : Collections.nCopies(validMacd.size(), null);

        List<Double> signalLine = new ArrayList<>();
        int pad = macdLine.size() - validMacd.size();
        for (int i = 0; i < pad; i++) signalLine.add(null);
        signalLine.addAll(signalLineRaw);

        List<Double> histogram = new ArrayList<>();
        for (int i = 0; i < macdLine.size(); i++) {
            Double m = macdLine.get(i);
            Double s = signalLine.get(i);
            if (m != null && s != null) {
                histogram.add(Math.round((m - s) * 1000000) / 1000000.0);
            } else {
                histogram.add(null);
            }
        }

        return Map.of("macd", macdLine, "signal", signalLine, "histogram", histogram);
    }

    // ATR

    public static List<Double> atr(List<Map<String, Object>> candles, int period) {
        if (candles.size() < 2) {
            return Collections.nCopies(candles.size(), null);
        }

        List<Double> trueRanges = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            double h = safeDouble(candles.get(i).get("high"));
            double l = safeDouble(candles.get(i).get("low"));
            double prevC = safeDouble(candles.get(i - 1).get("close"));
            double tr = Math.max(h - l, Math.max(Math.abs(h - prevC), Math.abs(l - prevC)));
            trueRanges.add(tr);
        }

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < period; i++) result.add(null);
        if (trueRanges.size() < period) {
            return Collections.nCopies(candles.size(), null);
        }

        double avg = trueRanges.subList(0, period).stream().mapToDouble(Double::doubleValue).sum() / period;
        result.add(Math.round(avg * 1000000) / 1000000.0);

        for (int i = period; i < trueRanges.size(); i++) {
            avg = (avg * (period - 1) + trueRanges.get(i)) / period;
            result.add(Math.round(avg * 1000000) / 1000000.0);
        }

        return result;
    }

    // Bollinger Bands

    public static Map<String, List<Double>> bbands(List<Map<String, Object>> candles, int period, double stdDev) {
        List<Double> closes = closes(candles);
        List<Double> middle = sma(closes, period);
        List<Double> upper = new ArrayList<>();
        List<Double> lower = new ArrayList<>();

        for (int i = 0; i < closes.size(); i++) {
            if (middle.get(i) == null) {
                upper.add(null);
                lower.add(null);
            } else {
                List<Double> window = closes.subList(Math.max(0, i - period + 1), i + 1);
                double mean = middle.get(i);
                double variance = window.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / period;
                double sd = Math.sqrt(variance);
                upper.add(Math.round((mean + stdDev * sd) * 1000000) / 1000000.0);
                lower.add(Math.round((mean - stdDev * sd) * 1000000) / 1000000.0);
            }
        }

        return Map.of("upper", upper, "middle", middle, "lower", lower);
    }

    // Stochastic RSI

    public static Map<String, List<Double>> stochRsi(List<Map<String, Object>> candles, int rsiPeriod, int stochPeriod, int kSmooth, int dSmooth) {
        List<Double> rsiVals = rsi(candles, rsiPeriod);
        List<Double> validRsi = new ArrayList<>();
        for (Double v : rsiVals) {
            if (v != null) validRsi.add(v);
        }

        List<Double> stochKRaw = new ArrayList<>();
        for (int i = 0; i < validRsi.size(); i++) {
            if (i < stochPeriod - 1) {
                stochKRaw.add(null);
            } else {
                List<Double> window = validRsi.subList(i - stochPeriod + 1, i + 1);
                double lo = Collections.min(window);
                double hi = Collections.max(window);
                if (hi == lo) {
                    stochKRaw.add(50.0);
                } else {
                    stochKRaw.add(Math.round((validRsi.get(i) - lo) / (hi - lo) * 100 * 100) / 100.0);
                }
            }
        }

        List<Double> validK = new ArrayList<>();
        for (Double v : stochKRaw) {
            if (v != null) validK.add(v);
        }
        List<Double> kLine = validK.size() >= kSmooth ? sma(validK, kSmooth) : Collections.nCopies(validK.size(), null);
        List<Double> validKSmoothed = new ArrayList<>();
        for (Double v : kLine) {
            if (v != null) validKSmoothed.add(v);
        }
        List<Double> dLine = validKSmoothed.size() >= dSmooth ? sma(validKSmoothed, dSmooth) : Collections.nCopies(validKSmoothed.size(), null);

        List<Double> fullK = new ArrayList<>();
        int padK = rsiVals.size() - validRsi.size() + (validRsi.size() - validK.size()) + (validK.size() - kLine.size());
        for (int i = 0; i < padK; i++) fullK.add(null);
        fullK.addAll(kLine);

        List<Double> fullD = new ArrayList<>();
        for (int i = 0; i < rsiVals.size() - dLine.size(); i++) fullD.add(null);
        fullD.addAll(dLine);

        return Map.of("k", fullK, "d", fullD);
    }

    // ADX

    public static List<Double> adx(List<Map<String, Object>> candles, int period) {
        if (candles.size() < period + 1) {
            return Collections.nCopies(candles.size(), null);
        }

        List<Double> plusDmList = new ArrayList<>();
        List<Double> minusDmList = new ArrayList<>();
        List<Double> trList = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            double h = safeDouble(candles.get(i).get("high"));
            double l = safeDouble(candles.get(i).get("low"));
            double prevH = safeDouble(candles.get(i - 1).get("high"));
            double prevL = safeDouble(candles.get(i - 1).get("low"));
            double prevC = safeDouble(candles.get(i - 1).get("close"));

            double plusDm = Math.max(h - prevH, 0);
            if (plusDm > prevL - l) plusDm = 0;
            double minusDm = Math.max(prevL - l, 0);
            if (minusDm > h - prevH) minusDm = 0;
            double tr = Math.max(h - l, Math.max(Math.abs(h - prevC), Math.abs(l - prevC)));

            plusDmList.add(plusDm);
            minusDmList.add(minusDm);
            trList.add(tr);
        }

        if (trList.size() < period) {
            return Collections.nCopies(candles.size(), null);
        }

        double atrVal = trList.subList(0, period).stream().mapToDouble(Double::doubleValue).sum();
        double plusDmSmooth = plusDmList.subList(0, period).stream().mapToDouble(Double::doubleValue).sum();
        double minusDmSmooth = minusDmList.subList(0, period).stream().mapToDouble(Double::doubleValue).sum();

        List<Double> dxList = new ArrayList<>();
        double plusDi = (plusDmSmooth / atrVal) * 100;
        double minusDi = (minusDmSmooth / atrVal) * 100;
        double diSum = plusDi + minusDi;
        dxList.add(Math.abs(plusDi - minusDi) / diSum * 100);

        for (int i = period; i < trList.size(); i++) {
            atrVal = atrVal - (atrVal / period) + trList.get(i);
            plusDmSmooth = plusDmSmooth - (plusDmSmooth / period) + plusDmList.get(i);
            minusDmSmooth = minusDmSmooth - (minusDmSmooth / period) + minusDmList.get(i);

            plusDi = (plusDmSmooth / atrVal) * 100;
            minusDi = (minusDmSmooth / atrVal) * 100;
            diSum = plusDi + minusDi;
            dxList.add(Math.abs(plusDi - minusDi) / diSum * 100);
        }

        List<Double> result = new ArrayList<>();
        for (int i = 0; i < period * 2; i++) result.add(null);
        if (dxList.size() >= period) {
            double adxVal = dxList.subList(0, period).stream().mapToDouble(Double::doubleValue).sum() / period;
            result.add(Math.round(adxVal * 10000) / 10000.0);
            for (int i = period; i < dxList.size(); i++) {
                adxVal = (adxVal * (period - 1) + dxList.get(i)) / period;
                result.add(Math.round(adxVal * 10000) / 10000.0);
            }
        }

        while (result.size() < candles.size()) {
            result.add(0, null);
        }
        return result.subList(0, candles.size());
    }

    // OBV

    public static List<Double> obv(List<Map<String, Object>> candles) {
        List<Double> closes = closes(candles);
        List<Double> volumes = volumes(candles);
        List<Double> result = new ArrayList<>();
        result.add(0.0);
        for (int i = 1; i < closes.size(); i++) {
            if (closes.get(i) > closes.get(i - 1)) {
                result.add(result.get(result.size() - 1) + volumes.get(i));
            } else if (closes.get(i) < closes.get(i - 1)) {
                result.add(result.get(result.size() - 1) - volumes.get(i));
            } else {
                result.add(result.get(result.size() - 1));
            }
        }
        return result;
    }

    // VWAP

    public static List<Double> vwap(List<Map<String, Object>> candles) {
        double cumVol = 0.0;
        double cumTpVol = 0.0;
        List<Double> result = new ArrayList<>();
        for (Map<String, Object> c : candles) {
            double tp = (safeDouble(c.get("high")) + safeDouble(c.get("low")) + safeDouble(c.get("close"))) / 3.0;
            cumVol += safeDouble(c.get("volume"));
            cumTpVol += tp * safeDouble(c.get("volume"));
            if (cumVol > 0) {
                result.add(Math.round(cumTpVol / cumVol * 1000000) / 1000000.0);
            } else {
                result.add(null);
            }
        }
        return result;
    }

    // High-level helper

    public static Map<String, List<Double>> computeAll(List<Map<String, Object>> candles) {
        if (candles == null || candles.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Double> closes = closes(candles);
        List<Double> ema20 = ema(closes, 20);
        List<Double> ema50 = ema(closes, 50);
        List<Double> rsi7 = rsi(candles, 7);
        List<Double> rsi14 = rsi(candles, 14);
        Map<String, List<Double>> macdData = macd(candles, 12, 26, 9);
        List<Double> atr3 = atr(candles, 3);
        List<Double> atr14 = atr(candles, 14);
        Map<String, List<Double>> bbandsData = bbands(candles, 20, 2.0);
        List<Double> adx = adx(candles, 14);
        List<Double> obv = obv(candles);
        List<Double> vwap = vwap(candles);

        return Map.ofEntries(
                Map.entry("ema20", ema20),
                Map.entry("ema50", ema50),
                Map.entry("rsi7", rsi7),
                Map.entry("rsi14", rsi14),
                Map.entry("macd", macdData.get("macd")),
                Map.entry("macd_signal", macdData.get("signal")),
                Map.entry("macd_histogram", macdData.get("histogram")),
                Map.entry("atr3", atr3),
                Map.entry("atr14", atr14),
                Map.entry("bbands_upper", bbandsData.get("upper")),
                Map.entry("bbands_middle", bbandsData.get("middle")),
                Map.entry("bbands_lower", bbandsData.get("lower")),
                Map.entry("adx", adx),
                Map.entry("obv", obv),
                Map.entry("vwap", vwap)
        );
    }

    public static List<Double> lastN(List<Double> series, int n) {
        List<Double> valid = new ArrayList<>();
        for (Double v : series) {
            if (v != null) valid.add(v);
        }
        return valid.subList(Math.max(0, valid.size() - n), valid.size());
    }

    public static Double latest(List<Double> series) {
        if (series != null && !series.isEmpty()) {
            for (int i = series.size() - 1; i >= 0; i--) {
                if (series.get(i) != null) {
                    return series.get(i);
                }
            }
        }
        return Double.valueOf("0");
    }

    private static double safeDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return 0.0;
    }
}
