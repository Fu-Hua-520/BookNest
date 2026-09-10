package com.fuhua.booknest.server.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * zvector 向量库配置属性（对应 application.yml 中 booknest.zvector 段）
 *
 * <p>zvector 为阿里云 AnalyticDB PostgreSQL 的向量检索扩展，通过 PostgreSQL JDBC 访问。</p>
 */
@Component
@ConfigurationProperties(prefix = "booknest.zvector")
@Data
public class ZVectorProperties {

    // 是否启用向量库（默认 false，未配置 AnalyticDB 时不影响应用启动）
    private Boolean enabled;

    // AnalyticDB PostgreSQL JDBC 连接地址
    private String jdbcUrl;

    // 数据库用户名
    private String username;

    // 数据库密码
    private String password;

    // 向量集合表名
    private String collection;

    // 相似度检索返回的最大条数
    private Integer topK;

    // 相似度阈值（范围 [0,1]，越大越相似）
    private Double similarityThreshold;
}
