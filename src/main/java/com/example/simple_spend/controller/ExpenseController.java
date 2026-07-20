package com.example.simple_spend.controller;

import com.example.simple_spend.model.Expense;
import com.example.simple_spend.model.User;
import com.example.simple_spend.repository.ExpenseRepository;
import com.example.simple_spend.repository.UserRepository;
import com.example.simple_spend.config.JwtUtil;
import com.example.simple_spend.service.AiService;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:8081"})
public class ExpenseController {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AiService aiService;

    private User resolveAuthenticatedUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User profile session not found"));
    }

    @GetMapping
    public List<Expense> getExpenses(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {

        User user = resolveAuthenticatedUser(authHeader);

        if (year != null && month != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate startDate = yearMonth.atDay(1);
            LocalDate endDate = yearMonth.atEndOfMonth();
            return expenseRepository.findByUserIdAndDateBetween(user.getId(), startDate, endDate);
        }

        return expenseRepository.findByUserId(user.getId());
    }

    @PostMapping
    public Expense createExpense(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Expense expense) {

        User user = resolveAuthenticatedUser(authHeader);
        expense.setUser(user);

        if (expense.getId() == null || expense.getId().isEmpty()) {
            expense.setId(UUID.randomUUID().toString());
        }
        return expenseRepository.save(expense);
    }

    /**
     * Endpoint to parse and extract structured parameters using live LLM reasoning
     */
    @PostMapping("/ai")
    public ResponseEntity<?> createExpenseWithAi(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody String rawText) {

        User user = resolveAuthenticatedUser(authHeader);

        // Clean rawText by removing quotes if wrapped as a JSON literal string from HTTP clients
        String cleanText = rawText.trim();
        if (cleanText.startsWith("\"") && cleanText.endsWith("\"")) {
            cleanText = cleanText.substring(1, cleanText.length() - 1);
        }

        // Fetch parsed JSON response directly from the AI service
        String parsedJsonString = aiService.parseExpenseWithAi(cleanText);

        try {
            JSONObject parsedJson = new JSONObject(parsedJsonString);
            Expense expense = new Expense();

            // 1. Safely extract decimal amount
            java.math.BigDecimal amountVal = java.math.BigDecimal.ZERO;
            if (parsedJson.has("amount")) {
                try {
                    amountVal = parsedJson.getBigDecimal("amount");
                } catch (Exception ex) {
                    try {
                        amountVal = java.math.BigDecimal.valueOf(parsedJson.getDouble("amount"));
                    } catch (Exception ex2) {
                        // Remain Zero
                    }
                }
            }
            expense.setAmount(amountVal);

            // 2. Map parsed categories case-insensitively to secure against uppercase/lowercase Enum mismatches
            String categoryStr = parsedJson.optString("category", "other").trim();
            com.example.simple_spend.model.ExpenseCategory matchedCategory = null;

            for (com.example.simple_spend.model.ExpenseCategory cat : com.example.simple_spend.model.ExpenseCategory.values()) {
                if (cat.name().equalsIgnoreCase(categoryStr)) {
                    matchedCategory = cat;
                    break;
                }
            }

            if (matchedCategory == null) {
                // Check fallback to OTHER or other dynamically
                try {
                    matchedCategory = com.example.simple_spend.model.ExpenseCategory.valueOf("OTHER");
                } catch (Exception ex) {
                    try {
                        matchedCategory = com.example.simple_spend.model.ExpenseCategory.valueOf("other");
                    } catch (Exception ex2) {
                        matchedCategory = com.example.simple_spend.model.ExpenseCategory.values()[0];
                    }
                }
            }
            expense.setCategory(matchedCategory);

            // 3. Extract description
            expense.setDescription(parsedJson.optString("description", cleanText));

            // 4. Safe relative date parser
            LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(parsedJson.optString("date", LocalDate.now().toString()));
            } catch (Exception ex) {
                parsedDate = LocalDate.now();
            }
            expense.setDate(parsedDate);

            expense.setUser(user);

            if (expense.getId() == null || expense.getId().isEmpty()) {
                expense.setId(UUID.randomUUID().toString());
            }

            Expense savedExpense = expenseRepository.save(expense);
            return ResponseEntity.ok(savedExpense);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Parsing mapping violation. Error details: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Expense> updateExpense(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody Expense expenseDetails) {

        User user = resolveAuthenticatedUser(authHeader);

        return expenseRepository.findById(id)
                .map(expense -> {
                    if (!expense.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(403).<Expense>build();
                    }
                    expense.setAmount(expenseDetails.getAmount());
                    expense.setCategory(expenseDetails.getCategory());
                    expense.setDate(expenseDetails.getDate());
                    expense.setDescription(expenseDetails.getDescription());
                    return ResponseEntity.ok(expenseRepository.save(expense));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {

        User user = resolveAuthenticatedUser(authHeader);

        if (!expenseRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Expense expense = expenseRepository.findById(id).orElse(null);
        if (expense != null && !expense.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        expenseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}