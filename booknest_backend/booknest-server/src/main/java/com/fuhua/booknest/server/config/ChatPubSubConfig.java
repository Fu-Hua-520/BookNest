package com.fuhua.booknest.server.config;

import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.server.websocket.ChatPushListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 聊天帧跨实例广播的订阅容器。
 *
 * <p>{@link RedisMessageListenerContainer} 本身是 {@code SmartLifecycle}，
 * 容器刷新时自动启动并订阅 {@link RedisConstant#WS_PUSH_CHANNEL}，
 * 不需要（也不应该）再加任何调度代码。</p>
 *
 * <p>订阅用的是与缓存同一个 {@link RedisConnectionFactory}；Pub/Sub 的连接是独立的，
 * 不会占用缓存命令的连接池。Redis 不可用时容器会自行重连，
 * 期间的广播会退化为「仅本地投递」（见 {@code ChatPushRelay} 的异常约定）。</p>
 */
@Configuration
public class ChatPubSubConfig {

    @Bean
    public RedisMessageListenerContainer chatPushListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatPushListener chatPushListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(chatPushListener, new ChannelTopic(RedisConstant.WS_PUSH_CHANNEL));
        return container;
    }
}
