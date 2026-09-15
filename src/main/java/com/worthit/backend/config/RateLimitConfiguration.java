package com.worthit.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthit.backend.filter.ApiRateLimitFilter;
import com.worthit.backend.filter.RequestBodySizeFilter;
import com.worthit.backend.service.ClientIpResolver;
import com.worthit.backend.service.InMemoryRateLimiter;
import org.springframework.beans.factory.annotation.Value;
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
    RequestBodySizeFilter requestBodySizeFilter(
            @Value("${app.security.max-request-body-bytes:32768}") int maxBodyBytes,
            ObjectMapper objectMapper) {
        return new RequestBodySizeFilter(maxBodyBytes, objectMapper);
    }

    @Bean
    FilterRegistrationBean<ApiRateLimitFilter> disableAutomaticRateLimitFilterRegistration(
            ApiRateLimitFilter filter) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RequestBodySizeFilter> disableAutomaticRequestBodySizeFilterRegistration(
            RequestBodySizeFilter filter) {
        FilterRegistrationBean<RequestBodySizeFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}