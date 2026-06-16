package com.example.lawassistant.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class RestClientTimeoutConfig {

    @Bean
    RestClientCustomizer restClientTimeoutCustomizer(
            @Value("${app.http.timeout-seconds:${app.llm.timeout-seconds:30}}") int timeoutSeconds
    ) {
        return builder -> {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
            requestFactory.setConnectTimeout(timeout);
            requestFactory.setReadTimeout(timeout);
            builder.requestFactory(requestFactory);
        };
    }
}
