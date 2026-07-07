package com.example.simple_spend.repository;

import com.example.simple_spend.model.Budget;
import com.example.simple_spend.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, ExpenseCategory> {
}

