package com.fuhua.booknest.server.config;

import com.fuhua.booknest.server.agent.BookTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 对话配置类。
 *
 * <p>Spring AI 只自动装配 {@link ChatClient.Builder}（原型作用域），并<b>不</b>自动装配
 * {@link ChatClient} 本身，因此在此显式装配一个单例 ChatClient。</p>
 *
 * <p><b>为什么必须在这里一次性注册工具：</b>{@code ChatClient.Builder} 是原型 Bean，
 * 而被单例注入后整个应用生命周期只解析一次、共用同一个实例；同时
 * {@code defaultTools(Object...)} 是<b>追加</b>语义（内部执行
 * {@code toolCallbacks.addAll(...)}，从不替换）。若在每次请求里调用
 * {@code builder.defaultTools(...).build()}，同一个 Builder 上的工具回调会随请求数
 * 线性累积，最终导致发给模型的请求携带 4N 份重复的函数定义。故工具只在此处注册一次。</p>
 */
@Configuration
public class AIConfig {

    /**
     * 装配全局唯一的 ChatClient（已注册 AI 助手业务工具）。
     *
     * @param builder   Spring AI 自动装配的 ChatClient.Builder（原型作用域）
     * @param bookTools AI 助手业务工具集
     * @return 单例 ChatClient
     */
    @Bean
    public ChatClient bookNestChatClient(ChatClient.Builder builder, BookTools bookTools) {
        return builder.defaultTools(bookTools).build();
    }
}
