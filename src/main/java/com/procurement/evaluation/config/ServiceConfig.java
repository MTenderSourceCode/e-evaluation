package com.procurement.evaluation.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.procurement.evaluation.utils.JsonUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ComponentScan(basePackages = "com.procurement.evaluation.service")
public class ServiceConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        final ObjectMapper jackson2ObjectMapper = new ObjectMapper();
        jackson2ObjectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return jackson2ObjectMapper;
    }

    @Bean
    public JsonUtil jsonUtil(final ObjectMapper objectMapper) {
        return new JsonUtil(objectMapper);
    }
}
