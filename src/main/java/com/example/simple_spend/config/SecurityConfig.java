package com.example.simple_spend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class SecurityConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // This permits your specific frontend port to communicate with the server
        config.setAllowedOrigins(Arrays.asList("http://localhost:8081", "http://localhost:5173"));

        // Allows all common standard REST request methods
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allows headers like Content-Type so JSON text strings parse cleanly
        config.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
