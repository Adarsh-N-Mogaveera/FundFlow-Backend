package com.example.simple_spend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("https://fund-flow-frontend-zeta.vercel.app/") // Paste your actual Vercel domain here
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");

    }
}
