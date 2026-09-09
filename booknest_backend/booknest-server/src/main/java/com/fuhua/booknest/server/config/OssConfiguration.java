package com.fuhua.booknest.server.config;

import com.fuhua.booknest.server.properties.AliOssProperties;
import com.fuhua.booknest.server.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置类，装配 AliOssUtil 工具 Bean
 */
@Configuration
@Slf4j
public class OssConfiguration {

    /**
     * 装配阿里云 OSS 工具类
     * @param props OSS 配置属性
     * @return AliOssUtil 实例
     */
    @Bean
    public AliOssUtil aliOssUtil(AliOssProperties props) {
        log.info("初始化阿里云 OSS 工具类，endpoint: {}", props.getEndpoint());
        return new AliOssUtil(
                props.getEndpoint(),
                props.getAccessKeyId(),
                props.getAccessKeySecret(),
                props.getBucketName()
        );
    }
}
