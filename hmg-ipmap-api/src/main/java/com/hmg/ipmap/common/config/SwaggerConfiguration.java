package com.hmg.ipmap.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "IPMap API",
                        version = "1.0",
                        description = "HMG IP Map API Documentation"),
        servers = {@Server(url = "http://localhost:8080", description = "Local Server")})
public class SwaggerConfiguration {
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder().group("user-api").pathsToMatch("/api/v1/users/**").build();
    }

    @Bean
    public GroupedOpenApi locationApi() {
        return GroupedOpenApi.builder()
                .group("location-api")
                .pathsToMatch("/api/v1/locations/**")
                .build();
    }

    @Bean
    public GroupedOpenApi ipMappingApi() {
        return GroupedOpenApi.builder()
                .group("ip-mapping-api")
                .pathsToMatch("/api/v1/ip-mappings/**", "/api/v1/ip-location")
                .build();
    }

    @Bean
    public GroupedOpenApi fileImportsApi() {
        return GroupedOpenApi.builder()
                .group("file-imports-api")
                .pathsToMatch("/api/v1/file-imports/**")
                .build();
    }
}
