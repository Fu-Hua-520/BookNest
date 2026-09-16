package com.fuhua.booknest.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import lombok.extern.slf4j.Slf4j;

/**
 * BookNest 书籍交流社区平台 - 后端启动入口
 *
 * <p>组件扫描范围覆盖整个 com.fuhua.booknest 基础包，
 * 以便加载 common、pojo 等兄弟模块中的 Bean。
 * {@link MapperScan} 显式指定 MyBatis Mapper 接口所在包。</p>
 *
 * <p>{@link EnableScheduling} 只为<b>一个</b>任务打开：{@code CountFlushJob}
 * 把 Redis 计数缓冲里的浏览/点赞/评论/收藏增量定时批量落回 MySQL。
 * 除此之外项目里没有定时任务 —— 计数之外的东西都直写数据库，
 * 不要往调度器里加新任务（隐藏的定时写入路径一旦挂掉很难被发现）。</p>
 */
@SpringBootApplication
@MapperScan("com.fuhua.booknest.server.mapper")
@ComponentScan(basePackages = "com.fuhua.booknest")
@EnableScheduling
@Slf4j
public class BookNestApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookNestApplication.class, args);
        log.info("BookNest 后端服务启动成功");
    }
}
