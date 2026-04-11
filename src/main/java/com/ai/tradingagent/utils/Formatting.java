package com.ai.tradingagent.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Utility helpers for consistently formatting numeric values.
 */
public class Formatting {

    public static Double formatNumber(Object value, int decimals) {
        if (value instanceof Number) {
            double v = ((Number) value).doubleValue();
            return Math.round(v * Math.pow(10, decimals)) / Math.pow(10, decimals);
        }
        return null;
    }

    public static Double formatSize(Object value) {
        return formatNumber(value, 6);
    }

    public static Double safeFloat(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    public static Double roundOrNone(Object value, int decimals) {
        Double numeric = safeFloat(value);
        if (numeric == null) {
            return null;
        }
        return Math.round(numeric * Math.pow(10, decimals)) / Math.pow(10, decimals);
    }

    public static List<Double> roundSeries(List<?> series, int decimals) {
        List<Double> rounded = new java.util.ArrayList<>();
        if (series == null) {
            return rounded;
        }
        for (Object val : series) {
            Double numeric = safeFloat(val);
            rounded.add(numeric != null ? Math.round(numeric * Math.pow(10, decimals)) / Math.pow(10, decimals) : null);
        }
        return rounded;
    }

    public static class JsonDefaultSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object obj, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (obj instanceof LocalDateTime) {
                gen.writeString(((LocalDateTime) obj).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            } else if (obj instanceof Set) {
                gen.writeObject(new java.util.ArrayList<>((Set<?>) obj));
            } else {
                gen.writeString(obj.toString());
            }
        }
    }
}
