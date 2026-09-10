package com.fuhua.booknest.server.config;

import com.fuhua.booknest.server.properties.ZVectorProperties;
import com.fuhua.booknest.server.vectorstore.ZVectorStore;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * zvector 向量库配置类。
 *
 * <p>仅在 {@code booknest.zvector.enabled=true} 时装配（默认 false，未配置 AnalyticDB
 * 时不影响应用启动）。装配链路：{@link HikariDataSource}（连接 AnalyticDB PostgreSQL）
 * → {@link JdbcTemplate} → {@link ZVectorStore}。</p>
 */
@Configuration
@Slf4j
@ConditionalOnProperty(prefix = "booknest.zvector", name = "enabled", havingValue = "true")
public class ZVectorConfig {

    /**
     * 装配 AnalyticDB PostgreSQL 数据源（独立于主业务 MySQL 数据源，避免混淆）。
     *
     * @param properties zvector 配置属性
     * @return HikariCP 数据源
     */
    @Bean
    public HikariDataSource zVectorDataSource(ZVectorProperties properties) {
        // jdbcUrl 为空时给出告警，提示配置缺失（后续建表/检索将失败）
        if (properties.getJdbcUrl() == null || properties.getJdbcUrl().isBlank()) {
            log.warn("booknest.zvector.enabled=true 但 jdbc-url 为空，zvector 向量库无法正常使用，请检查 ZVECTOR_JDBC_URL 环境变量");
        }
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setPoolName("booknest-zvector-pool");
        return dataSource;
    }

    /**
     * 装配 zvector 专用 JdbcTemplate。
     *
     * @param dataSource AnalyticDB PostgreSQL 数据源
     * @return JdbcTemplate 实例
     */
    @Bean
    public JdbcTemplate zVectorJdbcTemplate(HikariDataSource zVectorDataSource) {
        return new JdbcTemplate(zVectorDataSource);
    }

    /**
     * 装配自研 zvector VectorStore。
     *
     * @param jdbcTemplate   AnalyticDB PostgreSQL JDBC 模板
     * @param embeddingModel DashScope embedding 模型（Spring AI 自动装配）
     * @param properties     zvector 配置属性
     * @return ZVectorStore 实例
     */
    @Bean
    public ZVectorStore zVectorStore(JdbcTemplate zVectorJdbcTemplate,
                                     EmbeddingModel embeddingModel,
                                     ZVectorProperties properties) {
        return new ZVectorStore(zVectorJdbcTemplate, embeddingModel, properties);
    }
}
