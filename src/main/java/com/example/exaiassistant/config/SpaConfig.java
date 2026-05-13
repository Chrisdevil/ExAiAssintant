package com.example.exaiassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
public class SpaConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve Vite-built frontend from classpath:static/
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
