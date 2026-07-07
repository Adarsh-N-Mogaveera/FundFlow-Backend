package com.example.simple_spend.controller;

import com.example.simple_spend.model.Budget;
import com.example.simple_spend.model.ExpenseCategory;
import com.example.simple_spend.repository.BudgetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/budgets")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:8081"})
public class BudgetController {

    @Autowired
    private BudgetRepository budgetRepository;

    @GetMapping
    public List<Budget> getAllBudgets() {
        return budgetRepository.findAll();
    }

    @PutMapping("/{category}")
    public Budget setBudget(@PathVariable ExpenseCategory category, @RequestBody Budget budgetDetails) {
        Budget budget = budgetRepository.findById(category)
                .orElseGet(() -> {
                    Budget newBudget = new Budget();
                    newBudget.setCategory(category);
                    return newBudget;
                });

        budget.setLimit(budgetDetails.getLimit());
        return budgetRepository.save(budget);
    }
}
