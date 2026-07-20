package com.example.simple_spend.controller;

import com.example.simple_spend.model.Budget;
import com.example.simple_spend.model.ExpenseCategory;
import com.example.simple_spend.model.User;
import com.example.simple_spend.repository.BudgetRepository;
import com.example.simple_spend.repository.UserRepository;
import com.example.simple_spend.config.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:8081"})
public class BudgetController {

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

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
    public List<Budget> getAllBudgets(@RequestHeader("Authorization") String authHeader) {
        User user = resolveAuthenticatedUser(authHeader);
        return budgetRepository.findByUserId(user.getId());
    }

    @PutMapping("/{category}")
    public Budget setBudget(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable ExpenseCategory category,
            @RequestBody Budget budgetDetails) {

        User user = resolveAuthenticatedUser(authHeader);

        Budget budget = budgetRepository.findByUserIdAndCategory(user.getId(), category)
                .orElseGet(() -> {
                    Budget newBudget = new Budget();
                    newBudget.setCategory(category);
                    newBudget.setUser(user);
                    return newBudget;
                });

        budget.setLimit(budgetDetails.getLimit());
        return budgetRepository.save(budget);
    }
}