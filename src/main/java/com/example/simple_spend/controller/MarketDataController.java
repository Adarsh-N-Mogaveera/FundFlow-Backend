package com.example.simple_spend.controller;

import com.example.simple_spend.service.MarketDataService;
import com.example.simple_spend.service.MarketDataService.AssetMarketDTO;
import com.example.simple_spend.model.Investment;
import com.example.simple_spend.model.User;
import com.example.simple_spend.repository.InvestmentRepository;
import com.example.simple_spend.repository.UserRepository;
import com.example.simple_spend.config.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/market")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:8081"})
public class MarketDataController {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/assets")
    public List<AssetMarketDTO> getMarketAssets() {
        return marketDataService.getMarketAssets();
    }

    @GetMapping("/quote/{symbol}")
    public ResponseEntity<AssetMarketDTO> getMarketQuote(@PathVariable String symbol) {
        AssetMarketDTO quote = marketDataService.getMarketQuote(symbol);
        return ResponseEntity.ok(quote);
    }

    @PostMapping("/sync-portfolio")
    public ResponseEntity<List<Investment>> syncUserPortfolio(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }

        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            return ResponseEntity.status(404).build();
        }

        List<Investment> holdings = investmentRepository.findByUserId(user.getId());
        for (Investment holding : holdings) {
            AssetMarketDTO liveQuote = marketDataService.getMarketQuote(holding.getSymbol());
            if (liveQuote != null && liveQuote.currentPrice > 0) {
                holding.setCurrentPrice(BigDecimal.valueOf(liveQuote.currentPrice));
                investmentRepository.save(holding);
            }
        }

        return ResponseEntity.ok(holdings);
    }
}