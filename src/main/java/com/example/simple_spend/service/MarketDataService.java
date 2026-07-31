package com.example.simple_spend.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketDataService {

    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes cache TTL
    private final ConcurrentHashMap<String, CachedAssetData> cache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static class AssetMarketDTO {
        public String symbol;
        public String name;
        public String category;
        public double currentPrice;
        public double cagr1Y;
        public double cagr3Y;
        public double cagr5Y;
        public double dailyChange;
        public String riskLevel;
        public String description;

        public AssetMarketDTO(String symbol, String name, String category, double currentPrice,
                              double cagr1Y, double cagr3Y, double cagr5Y, double dailyChange,
                              String riskLevel, String description) {
            this.symbol = symbol;
            this.name = name;
            this.category = category;
            this.currentPrice = currentPrice;
            this.cagr1Y = cagr1Y;
            this.cagr3Y = cagr3Y;
            this.cagr5Y = cagr5Y;
            this.dailyChange = dailyChange;
            this.riskLevel = riskLevel;
            this.description = description;
        }
    }

    private static class CachedAssetData {
        long timestamp;
        AssetMarketDTO data;

        CachedAssetData(AssetMarketDTO data) {
            this.timestamp = System.currentTimeMillis();
            this.data = data;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
    }

    private static final Map<String, AssetMarketDTO> BASE_CATALOG = new LinkedHashMap<>();

    static {
        BASE_CATALOG.put("^NSEI", new AssetMarketDTO("^NSEI", "Nifty 50 Index Fund", "TIER1_INDEX", 24350.50, 14.2, 13.8, 15.1, +0.65, "Low", "Top 50 companies in India. Primary foundation for long-term compounding SIPs."));
        BASE_CATALOG.put("VOO", new AssetMarketDTO("VOO", "S&P 500 Index ETF", "TIER1_INDEX", 512.30, 18.5, 12.4, 14.6, +0.42, "Low", "Top 500 US companies. Gold standard global wealth builder."));
        BASE_CATALOG.put("RELIANCE", new AssetMarketDTO("RELIANCE", "Reliance Industries", "TIER2_BLUECHIP", 2980.00, 16.8, 14.1, 17.3, +1.12, "Moderate", "Energy, retail & telecom giant with dominant domestic market share."));
        BASE_CATALOG.put("AAPL", new AssetMarketDTO("AAPL", "Apple Inc.", "TIER2_BLUECHIP", 224.50, 21.0, 15.6, 22.8, -0.15, "Moderate", "Global tech pillar with vast consumer ecosystem & strong cash reserves."));
        BASE_CATALOG.put("SCHD", new AssetMarketDTO("SCHD", "US Dividend Equity ETF", "TIER3_DIVIDEND", 82.40, 11.4, 10.2, 11.9, +0.28, "Low", "High-dividend growth stocks designed for cash-flow reinvestment compounding."));
    }

    public List<AssetMarketDTO> getMarketAssets() {
        List<AssetMarketDTO> result = new ArrayList<>();
        for (String symbol : BASE_CATALOG.keySet()) {
            result.add(getMarketQuote(symbol));
        }
        return result;
    }

    public AssetMarketDTO getMarketQuote(String symbol) {
        String cleanSymbol = symbol.toUpperCase().trim();
        CachedAssetData cached = cache.get(cleanSymbol);

        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }

        // Fetch live quote from financial API with fallback safety
        AssetMarketDTO fetched = fetchLiveFromApi(cleanSymbol);
        if (fetched != null) {
            cache.put(cleanSymbol, new CachedAssetData(fetched));
            return fetched;
        }

        // Return base catalog default if external network call fails or ticker is not found
        if (BASE_CATALOG.containsKey(cleanSymbol)) {
            return BASE_CATALOG.get(cleanSymbol);
        }

        // Fallback generic object for unknown ticker
        return new AssetMarketDTO(cleanSymbol, cleanSymbol + " Holding", "TIER2_BLUECHIP", 100.00, 12.0, 11.5, 12.5, 0.0, "Moderate", "Tracked asset holding.");
    }

    private AssetMarketDTO fetchLiveFromApi(String symbol) {
        try {
            // Yahoo Finance v8 chart API endpoint for range=5y with 1d interval
            String targetUrl = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol + "?interval=1d&range=5y";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return null;
            }

            JSONObject root = new JSONObject(response.body());
            JSONObject chart = root.getJSONObject("chart");
            JSONArray results = chart.getJSONArray("result");
            if (results.isEmpty()) return null;

            JSONObject resultObj = results.getJSONObject(0);
            JSONObject meta = resultObj.getJSONObject("meta");

            double currentPrice = meta.optDouble("regularMarketPrice", 0.0);
            double previousClose = meta.optDouble("chartPreviousClose", currentPrice);
            double dailyChange = previousClose > 0 ? ((currentPrice - previousClose) / previousClose) * 100.0 : 0.0;

            // Extract close price history to compute real 1Y, 3Y, and 5Y CAGRs
            JSONObject indicators = resultObj.getJSONObject("indicators");
            JSONArray quoteArr = indicators.getJSONArray("quote");
            JSONArray closePrices = quoteArr.getJSONObject(0).getJSONArray("close");

            List<Double> validPrices = new ArrayList<>();
            for (int i = 0; i < closePrices.length(); i++) {
                if (!closePrices.isNull(i)) {
                    validPrices.add(closePrices.getDouble(i));
                }
            }

            double cagr1Y = calculateCagr(validPrices, 252, currentPrice, 14.0);
            double cagr3Y = calculateCagr(validPrices, 252 * 3, currentPrice, 13.0);
            double cagr5Y = calculateCagr(validPrices, 252 * 5, currentPrice, 15.0);

            AssetMarketDTO template = BASE_CATALOG.getOrDefault(symbol, new AssetMarketDTO(
                    symbol, symbol, "TIER2_BLUECHIP", currentPrice, cagr1Y, cagr3Y, cagr5Y, dailyChange, "Moderate", "Tracked asset."
            ));

            return new AssetMarketDTO(
                    symbol,
                    template.name,
                    template.category,
                    Math.round(currentPrice * 100.0) / 100.0,
                    Math.round(cagr1Y * 10.0) / 10.0,
                    Math.round(cagr3Y * 10.0) / 10.0,
                    Math.round(cagr5Y * 10.0) / 10.0,
                    Math.round(dailyChange * 100.0) / 100.0,
                    template.riskLevel,
                    template.description
            );

        } catch (Exception e) {
            // Fail-safe silent return on timeout or parsing variance
            return null;
        }
    }

    private double calculateCagr(List<Double> priceHistory, int tradingDaysAgo, double currentPrice, double defaultFallback) {
        if (priceHistory.size() <= tradingDaysAgo) {
            return defaultFallback;
        }
        int pastIndex = priceHistory.size() - 1 - tradingDaysAgo;
        if (pastIndex < 0) pastIndex = 0;

        double pastPrice = priceHistory.get(pastIndex);
        if (pastPrice <= 0 || currentPrice <= 0) return defaultFallback;

        double years = tradingDaysAgo / 252.0;
        double cagr = (Math.pow(currentPrice / pastPrice, 1.0 / years) - 1.0) * 100.0;
        return Double.isNaN(cagr) || Double.isInfinite(cagr) ? defaultFallback : cagr;
    }
}