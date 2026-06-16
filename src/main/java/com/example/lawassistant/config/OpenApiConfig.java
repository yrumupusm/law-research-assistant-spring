package com.example.lawassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lawResearchAssistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Law Research Assistant API")
                        .version("0.1.0")
                        .description("""
                                RAG-style legal research assistant API.
                                The service retrieves related law articles and returns cited answers with diagnostics.
                                """)
                        .license(new License().name("Private use")));
    }
}
