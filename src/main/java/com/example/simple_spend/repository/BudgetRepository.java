package com.example.simple_spend.repository;

import com.example.simple_spend.model.Budget;
import com.example.simple_spend.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, String> {
    List<Budget> findByUserId(String userId);

    // Helper to find a budget by user and category together
    Optional<Budget> findByUserIdAndCategory(String userId, ExpenseCategory category);
}


