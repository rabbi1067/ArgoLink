package com.agrolink.app.demo;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps {@link DemoAuthFilter} out of the plain servlet chain. It runs only
 * inside the Spring Security chain (wired in {@code SecurityConfig}), where
 * the authentication it sets actually survives. Without this, Boot would run
 * it twice per request.
 */
@Configuration
public class DemoConfig {

    @Bean
    public FilterRegistrationBean<DemoAuthFilter> demoAuthFilterRegistration(DemoAuthFilter filter) {
        FilterRegistrationBean<DemoAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
