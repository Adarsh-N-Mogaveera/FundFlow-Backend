package com.example.simple_spend.controller;

import com.example.simple_spend.model.Expense;
import com.example.simple_spend.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = "http://localhost:8081")
public class ExpenseController {

    @Autowired
    private ExpenseRepository expenseRepository;

    @GetMapping
    public List<Expense> getAllExpenses() {
        return expenseRepository.findAll();
    }

    @PostMapping
    public Expense createExpense(@RequestBody Expense expense) {
        if (expense.getId() == null || expense.getId().isEmpty()) {
            expense.setId(UUID.randomUUID().toString());
        }
        return expenseRepository.save(expense);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Expense> updateExpense(@PathVariable String id, @RequestBody Expense expenseDetails) {
        return expenseRepository.findById(id)
                .map(expense -> {
                    expense.setAmount(expenseDetails.getAmount());
                    expense.setCategory(expenseDetails.getCategory());
                    expense.setDate(expenseDetails.getDate());
                    expense.setDescription(expenseDetails.getDescription());
                    return ResponseEntity.ok(expenseRepository.save(expense));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable String id) {
        if (!expenseRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        expenseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
