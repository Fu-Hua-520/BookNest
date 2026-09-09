package com.fuhua.booknest.server.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置属性（对应 application.yml 中 booknest.alioss 段）
 */
@Component
@ConfigurationProperties(prefix = "booknest.alioss")
@Data
public class AliOssProperties {

    // OSS 地域节点，如 oss-cn-hangzhou.aliyuncs.com
    private String endpoint;

    // 访问密钥 ID
    private String accessKeyId;

    // 访问密钥 Secret
    private String accessKeySecret;

    // 存储桶名称
    private String bucketName;
}
