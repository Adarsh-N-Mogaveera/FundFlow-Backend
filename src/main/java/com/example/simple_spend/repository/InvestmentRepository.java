package com.example.simple_spend.repository;

import com.example.simple_spend.model.Investment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, String> {
    List<Investment> findByUserId(String userId);
}