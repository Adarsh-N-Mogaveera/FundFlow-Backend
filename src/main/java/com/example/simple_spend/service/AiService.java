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

    @Value("${gemini.api.key}")
    private String apiKey;

    // Globally stable v1 endpoint
//    private static final String GEMINI_API_URL = "[https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=](https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=)";
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-3.5-flash:generateContent?key=";

    public String parseExpenseWithAi(String rawText) {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("YOUR_ACTUAL_GEMINI_API_KEY_PASTED_HERE")) {
            System.err.println("CRITICAL ERROR: 'gemini.api.key' is not configured inside application.properties!");
            return createFallbackResponse(rawText);
        }

        try {
            String targetUrl = GEMINI_API_URL + apiKey;
            HttpClient client = HttpClient.newHttpClient();

            // Clear, strict prompt demanding raw JSON structure.
            String systemPrompt = "You are a backend financial parsing engine. Parse this raw text input into a structured expense JSON object.\n"
                    + "Available categories are exactly: 'food', 'transport', 'housing', 'entertainment', 'shopping', 'health', 'utilities', 'other'.\n"
                    + "Today's date is " + LocalDate.now().toString() + ". Use this today reference point to calculate correct historical dates relative to it (e.g. '3rd of this month' or 'yesterday').\n"
                    + "You MUST respond with a valid JSON object matching this exact structure, with no extra explanations or text outside the JSON:\n"
                    + "{\n"
                    + "  \"amount\": 0.00,\n"
                    + "  \"category\": \"string\",\n"
                    + "  \"description\": \"Clean title case description\",\n"
                    + "  \"date\": \"YYYY-MM-DD\"\n"
                    + "}\n"
                    + "Text to parse: " + rawText;

            // Simplified standard payload structure to bypass all engine configuration blockers
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
                System.err.println("Gemini API returned error " + response.statusCode() + ": " + response.body());
                return createFallbackResponse(rawText);
            }

            JSONObject responseObject = new JSONObject(response.body());
            String aiRawResult = responseObject.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text").trim();

            // Strips out any backticks automatically if Gemini formats its text output
            return cleanJsonResult(aiRawResult);

        } catch (Exception e) {
            System.err.println("AI Parser failed with exception: " + e.getMessage());
            return createFallbackResponse(rawText);
        }
    }

    private String cleanJsonResult(String raw) {
        String clean = raw.trim();
        // Remove markdown block wraps if present
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
        return "{\"amount\": 0.00, \"category\": \"other\", \"description\": \"" + rawText.replace("\"", "\\\"") + "\", \"date\": \"" + LocalDate.now().toString() + "\"}";
    }
}