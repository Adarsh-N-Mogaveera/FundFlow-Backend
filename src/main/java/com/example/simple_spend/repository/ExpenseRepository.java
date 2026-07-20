package com.example.simple_spend.repository;

import com.example.simple_spend.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, String> {
    // Fetches ONLY expenses belonging to a specific user within a month
    List<Expense> findByUserIdAndDateBetween(String userId, LocalDate startDate, LocalDate endDate);

    // Fallback find all items belonging to a user
    List<Expense> findByUserId(String userId);
}
