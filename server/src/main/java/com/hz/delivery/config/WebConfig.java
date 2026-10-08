package com.hz.delivery.config;

import com.hz.delivery.common.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final BizProperties bizProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/client/**", "/api/admin/**")
                .excludePathPatterns(
                        // C 端免登录
                        "/api/client/auth/**",
                        "/api/client/public/**",
                        // 后台登录
                        "/api/admin/auth/login",
                        "/api/admin/auth/captcha"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = bizProperties.getCorsAllowedOrigins().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"*".equals(s))
                .toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", Constants.HEADER_TOKEN)
                .exposedHeaders("Content-Disposition")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
