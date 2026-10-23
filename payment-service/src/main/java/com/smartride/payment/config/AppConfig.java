package com.smartride.payment.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder b) {
        return b.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(5)).build();
    }
}
