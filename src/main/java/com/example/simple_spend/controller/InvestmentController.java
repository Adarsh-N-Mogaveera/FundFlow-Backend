package com.example.simple_spend.controller;

import com.example.simple_spend.config.JwtUtil;
import com.example.simple_spend.model.Investment;
import com.example.simple_spend.model.User;
import com.example.simple_spend.repository.InvestmentRepository;
import com.example.simple_spend.repository.UserRepository;
import com.example.simple_spend.service.MarketDataService;
import com.example.simple_spend.service.MarketDataService.AssetMarketDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/investments")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:8081"})
public class InvestmentController {

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MarketDataService marketDataService;

    private User resolveAuthenticatedUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("Missing or invalid Authorization header");
        }
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User session profile not found"));
    }

    @GetMapping
    public List<Investment> getUserInvestments(@RequestHeader("Authorization") String authHeader) {
        User user = resolveAuthenticatedUser(authHeader);
        List<Investment> holdings = investmentRepository.findByUserId(user.getId());

        // Automatically refresh current live market prices for accurate unrealized P&L
        for (Investment h : holdings) {
            try {
                AssetMarketDTO quote = marketDataService.getMarketQuote(h.getSymbol());
                if (quote != null && quote.currentPrice > 0) {
                    h.setCurrentPrice(BigDecimal.valueOf(quote.currentPrice));
                    investmentRepository.save(h);
                }
            } catch (Exception ignored) {
                // Keep existing cached price if network call fails
            }
        }

        return holdings;
    }

    @PostMapping
    public Investment createInvestment(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Investment investment) {

        User user = resolveAuthenticatedUser(authHeader);
        investment.setUser(user);

        if (investment.getId() == null || investment.getId().isEmpty()) {
            investment.setId("inv-" + UUID.randomUUID().toString());
        }

        // Look up live current price if missing
        if (investment.getCurrentPrice() == null) {
            AssetMarketDTO quote = marketDataService.getMarketQuote(investment.getSymbol());
            if (quote != null && quote.currentPrice > 0) {
                investment.setCurrentPrice(BigDecimal.valueOf(quote.currentPrice));
            } else {
                investment.setCurrentPrice(investment.getAvgBuyPrice());
            }
        }

        return investmentRepository.save(investment);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Investment> updateInvestment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id,
            @RequestBody Investment details) {

        User user = resolveAuthenticatedUser(authHeader);

        return investmentRepository.findById(id)
                .map(holding -> {
                    if (!holding.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(403).<Investment>build();
                    }
                    holding.setQuantity(details.getQuantity());
                    holding.setAvgBuyPrice(details.getAvgBuyPrice());
                    if (details.getCurrentPrice() != null) {
                        holding.setCurrentPrice(details.getCurrentPrice());
                    }
                    return ResponseEntity.ok(investmentRepository.save(holding));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestment(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {

        User user = resolveAuthenticatedUser(authHeader);

        return investmentRepository.findById(id)
                .map(holding -> {
                    if (!holding.getUser().getId().equals(user.getId())) {
                        return ResponseEntity.status(403).<Void>build();
                    }
                    investmentRepository.delete(holding);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}