package com.fuhua.booknest.server.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置（通知消息的交换机、队列、绑定与 JSON 序列化）
 */
@Configuration
public class RabbitMQConfig {

    // 通知交换机名称
    public static final String EXCHANGE = "booknest.notification.topic";
    // 通知队列名称
    public static final String QUEUE = "booknest.notification.queue";
    // 通知路由键
    public static final String ROUTING_KEY = "booknest.notification";

    // 死信交换机名称
    public static final String DEAD_LETTER_EXCHANGE = "booknest.notification.dlx";
    // 死信队列名称
    public static final String DEAD_LETTER_QUEUE = "booknest.notification.dlx.queue";
    // 死信路由键
    public static final String DEAD_LETTER_ROUTING_KEY = "booknest.notification.dlx";

    /**
     * 声明主题交换机
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /**
     * 声明通知队列（绑定死信交换机，消费失败时消息进入死信队列）
     */
    @Bean
    public Queue notificationQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE);
        args.put("x-dead-letter-routing-key", DEAD_LETTER_ROUTING_KEY);
        return new Queue(QUEUE, true, false, false, args);
    }

    /**
     * 绑定交换机与队列
     */
    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(ROUTING_KEY);
    }

    /**
     * 声明死信交换机
     */
    @Bean
    public TopicExchange notificationDlx() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * 声明死信队列
     */
    @Bean
    public Queue notificationDlq() {
        return new Queue(DEAD_LETTER_QUEUE, true);
    }

    /**
     * 绑定死信队列与死信交换机
     */
    @Bean
    public Binding notificationDlqBinding(Queue notificationDlq, TopicExchange notificationDlx) {
        return BindingBuilder.bind(notificationDlq).to(notificationDlx).with(DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * 消息转换器：JSON 序列化（RabbitTemplate 发送时自动装配该唯一 Bean）
     */
    @Bean
    public MessageConverter messageConverter() {
        // 使用构造参数设置 trustedPackages，避免反序列化 com.fuhua.booknest.server.mq 包下的消息时抛异常
        return new Jackson2JsonMessageConverter("com.fuhua.booknest.server.mq");
    }

    /**
     * 监听容器工厂：为消费者配置 JSON 反序列化转换器
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                               MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
