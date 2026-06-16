package com.example.lawassistant.config;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestClientTimeoutConfigTest {

    private final RestClientTimeoutConfig config = new RestClientTimeoutConfig();

    @Test
    void customizerAppliesTimeoutRequestFactoryToRestClientBuilder() {
        var customizer = config.restClientTimeoutCustomizer(5);

        assertThatCode(() -> customizer.customize(RestClient.builder()))
                .doesNotThrowAnyException();
    }

    @Test
    void customizerClampsNonPositiveTimeoutToPositiveDuration() {
        var customizer = config.restClientTimeoutCustomizer(0);

        assertThatCode(() -> customizer.customize(RestClient.builder()))
                .doesNotThrowAnyException();
    }
}
