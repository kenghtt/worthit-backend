package com.worthit.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthit.backend.filter.ApiRateLimitFilter;
import com.worthit.backend.service.ClientIpResolver;
import com.worthit.backend.service.InMemoryRateLimiter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfiguration {

    @Bean
    ApiRateLimitFilter apiRateLimitFilter(ApiRateLimitProperties properties,
                                          ClientIpResolver clientIpResolver,
                                          InMemoryRateLimiter rateLimiter,
                                          ObjectMapper objectMapper) {
        return new ApiRateLimitFilter(properties, clientIpResolver, rateLimiter, objectMapper);
    }

    @Bean
    FilterRegistrationBean<ApiRateLimitFilter> disableAutomaticRateLimitFilterRegistration(
            ApiRateLimitFilter filter) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}