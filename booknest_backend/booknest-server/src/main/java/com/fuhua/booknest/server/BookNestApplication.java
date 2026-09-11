package com.fuhua.booknest.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

/**
 * BookNest 书籍交流社区平台 - 后端启动入口
 *
 * <p>组件扫描范围覆盖整个 com.fuhua.booknest 基础包，
 * 以便加载 common、pojo 等兄弟模块中的 Bean。
 * {@link MapperScan} 显式指定 MyBatis Mapper 接口所在包。</p>
 */
@SpringBootApplication
@MapperScan("com.fuhua.booknest.server.mapper")
@ComponentScan(basePackages = "com.fuhua.booknest")
@EnableAsync
// RAG 索引需要按周期自动重建（参见 PostEmbeddingServiceImpl#refreshTask）
@EnableScheduling
@Slf4j
public class BookNestApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookNestApplication.class, args);
        log.info("BookNest 后端服务启动成功");
    }
}
