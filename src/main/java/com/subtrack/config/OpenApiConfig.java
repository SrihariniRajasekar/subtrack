package com.subtrack.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI subtrackOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SubTrack API")
                        .description("Subscription billing microservice - manages customers, plans, "
                                + "subscriptions, and billing cycles, including day-based proration "
                                + "when a plan changes mid-cycle.")
                        .version("v0.0.1"));
    }
}
