package com.example.simple_spend.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

@Service
public class AiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=";

    /**
     * Backward-compatible alias method called by ExpenseController.java
     */
    public String parseExpenseWithAi(String rawText) {
        return parseFinancialIntentWithAi(rawText);
    }

    /**
     * Dual-purpose AI parser capable of processing both EXPENSE and INVESTMENT natural language intents.
     */
    public String parseFinancialIntentWithAi(String rawText) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("YOUR_ACTUAL_GEMINI_API_KEY_PASTED_HERE")) {
            System.err.println("Gemini API key not set. Using local rule-based intent fallback.");
            return createFallbackResponse(rawText);
        }

        try {
            String targetUrl = GEMINI_API_URL + apiKey;
            HttpClient client = HttpClient.newHttpClient();

            String systemPrompt = "You are a backend financial parsing engine. Analyze the text and classify the intent as either 'EXPENSE' or 'INVESTMENT'.\n"
                    + "Today's date is " + LocalDate.now().toString() + ".\n\n"
                    + "If intent is EXPENSE (e.g. 'spent $30 on lunch', 'paid electric bill'):\n"
                    + "Categories allowed: 'food', 'transport', 'housing', 'entertainment', 'shopping', 'health', 'utilities', 'other'.\n"
                    + "Respond with exact JSON structure:\n"
                    + "{\n"
                    + "  \"intent\": \"EXPENSE\",\n"
                    + "  \"amount\": 0.00,\n"
                    + "  \"category\": \"string\",\n"
                    + "  \"description\": \"Clean title\",\n"
                    + "  \"date\": \"YYYY-MM-DD\"\n"
                    + "}\n\n"
                    + "If intent is INVESTMENT (e.g. 'bought 5 shares of Nifty at 24000', 'invested $500 in S&P 500 ETF'):\n"
                    + "Respond with exact JSON structure:\n"
                    + "{\n"
                    + "  \"intent\": \"INVESTMENT\",\n"
                    + "  \"symbol\": \"Ticker symbol or asset code (e.g. ^NSEI, VOO, RELIANCE, AAPL, SCHD)\",\n"
                    + "  \"name\": \"Full asset name\",\n"
                    + "  \"quantity\": 1.0,\n"
                    + "  \"avgBuyPrice\": 0.00,\n"
                    + "  \"type\": \"INDEX\" or \"STOCK\" or \"SIP\",\n"
                    + "  \"purchaseDate\": \"YYYY-MM-DD\"\n"
                    + "}\n\n"
                    + "Return ONLY valid JSON with no markdown formatting or commentary outside the JSON object.\n"
                    + "Text to parse: " + rawText;

            JSONObject jsonBody = new JSONObject();
            JSONObject contents = new JSONObject();
            JSONObject parts = new JSONObject();
            parts.put("text", systemPrompt);
            contents.put("parts", new JSONArray().put(parts));
            jsonBody.put("contents", new JSONArray().put(contents));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Gemini API error status " + response.statusCode() + ": " + response.body());
                return createFallbackResponse(rawText);
            }

            JSONObject responseObject = new JSONObject(response.body());
            String aiRawResult = responseObject.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text").trim();

            return cleanJsonResult(aiRawResult);

        } catch (Exception e) {
            System.err.println("AI Parser exception: " + e.getMessage());
            return createFallbackResponse(rawText);
        }
    }

    private String cleanJsonResult(String raw) {
        String clean = raw.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }

    private String createFallbackResponse(String rawText) {
        String lower = rawText.toLowerCase();
        boolean isInvest = lower.contains("buy") || lower.contains("bought") || lower.contains("invest") || lower.contains("shares");

        if (isInvest) {
            return "{\"intent\": \"INVESTMENT\", \"symbol\": \"^NSEI\", \"name\": \"Nifty 50 Index Fund\", \"quantity\": 1.0, \"avgBuyPrice\": 24350.50, \"type\": \"INDEX\", \"purchaseDate\": \"" + LocalDate.now() + "\"}";
        } else {
            return "{\"intent\": \"EXPENSE\", \"amount\": 0.00, \"category\": \"other\", \"description\": \"" + rawText.replace("\"", "\\\"") + "\", \"date\": \"" + LocalDate.now() + "\"}";
        }
    }
}