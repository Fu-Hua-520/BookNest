package com.fuhua.booknest.server.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fuhua.booknest.server.interceptor.JwtTokenAdminInterceptor;
import com.fuhua.booknest.server.interceptor.JwtTokenUserInterceptor;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import lombok.extern.slf4j.Slf4j;

/**
 * Web MVC配置类
 * 注册拦截器、配置OpenAPI文档、CORS等
 *
 * 基于 Spring Boot 3.x，使用 SpringDoc OpenAPI 替代 Swagger2
 */
@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    /**
     * 注册自定义拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册JWT拦截器...");

        // 注册用户端拦截器
        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/user/**")
                .addPathPatterns("/ai/**")
                .addPathPatterns("/post/**")
                .addPathPatterns("/booklist/**")
                .addPathPatterns("/category/**")
                .addPathPatterns("/tag/**")
                .addPathPatterns("/follow/**")
                .addPathPatterns("/notification/**")
                .addPathPatterns("/chat/**")
                // 文件上传需登录，防止匿名刷 OSS 流量
                .addPathPatterns("/common/**")
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/user/register")
                .excludePathPatterns("/user/password/**");

        // 注册管理后台拦截器
        // /admin/login 必须排除，否则登录接口自身会被要求先登录，形成死循环
        registry.addInterceptor(jwtTokenAdminInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login");

        log.info("JWT拦截器注册完成");
    }

    /**
     * 配置 CORS（跨域资源共享）
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("开始配置CORS跨域...");

        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type", "Cache-Control", "Connection")
                .allowCredentials(true)
                .maxAge(3600);

        log.info("CORS跨域配置完成");
    }

    /**
     * 扩展消息转换器，确保使用 UTF-8 编码
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("开始扩展消息转换器（UTF-8编码）...");

        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false);
        converters.add(0, stringConverter);

        log.info("消息转换器扩展完成");
    }

    /**
     * 配置 OpenAPI 文档（替代 Swagger2 Docket）
     */
    @Bean
    public OpenAPI customOpenAPI() {
        log.info("开始构建OpenAPI文档...");

        return new OpenAPI()
                .info(new Info()
                        .title("BookNest 书籍交流社区平台接口文档")
                        .version("1.0")
                        .description("BookNest 后端 API 接口文档 - 基于 Spring Boot 3.x")
                        .contact(new Contact()
                                .name("BookNest Team")
                                .email("support@booknest.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    /**
     * 设置静态资源映射
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        log.info("开始配置静态资源映射...");

        // SpringDoc OpenAPI UI 资源映射
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        log.info("静态资源映射配置完成");
    }
}
