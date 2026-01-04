package com.yufei.ptw.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置Swagger静态资源访问
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("配置Swagger静态资源访问");
        // Swagger UI资源
        registry.addResourceHandler("/swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    /**
     * 全局CORS跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("全局CORS跨域配置");
        registry.addMapping("/api/**") // 允许跨域访问的路径
                .allowedOriginPatterns("*") // 允许的源（使用allowedOriginPatterns代替allowedOrigins以支持通配符）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的HTTP方法
                .allowedHeaders("*") // 允许的请求头
                .allowCredentials(true) // 允许携带凭证
                .maxAge(3600); // 预检请求的缓存时间（秒）
    }
}