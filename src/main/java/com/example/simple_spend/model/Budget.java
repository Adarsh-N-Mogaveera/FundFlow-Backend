package com.example.simple_spend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;

    @Column(name = "amount_limit", nullable = false)
    private BigDecimal amountLimit; // Renamed variable to avoid SQL conflicts

    public Budget() {}

    public ExpenseCategory getCategory() { return category; }
    public void setCategory(ExpenseCategory category) { this.category = category; }

    public BigDecimal getLimit() { return amountLimit; }
    public void setLimit(BigDecimal amountLimit) { this.amountLimit = amountLimit; }
}
