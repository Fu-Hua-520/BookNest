package com.fuhua.booknest.server.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateConfigurer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ 配置（通知消息的交换机、队列、绑定与 JSON 序列化）
 */
@Configuration
@Slf4j
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
     * 把 publisher confirm / returns 回调挂到 {@link RabbitTemplate} 上。
     *
     * <p>只有 application.yml 里开了 {@code publisher-confirm-type} 与
     * {@code publisher-returns}，这两个回调才会被触发；光开开关不挂回调等于白开，
     * 因为默认回调实现是「什么都不做」。</p>
     *
     * <p><b>两个容易踩的坑：</b></p>
     * <ul>
     *   <li><b>必须用 {@link RabbitTemplateConfigurer} 来建模板。</b>
     *       自动装配里 {@code rabbitTemplate} 是
     *       {@code @ConditionalOnMissingBean(RabbitOperations.class)} ——
     *       我们自己 {@code @Bean} 一个 {@code RabbitTemplate} 会把官方的整个换掉，
     *       连带 {@code configurer.configure(...)} 一起不执行。而 yml 里的
     *       {@code spring.rabbitmq.template.mandatory}、retry（发送端）正是
     *       在那个 configurer 里应用的。所以「自定义 RabbitTemplate」最常见的暗坑是：
     *       yml 写了 {@code mandatory: true}，实际压根没生效，路由不到队列的消息被 broker
     *       静默丢弃（官方默认 {@code mandatory=false}）。这里显式借 configurer 配置，
     *       保证 yml 被真正应用。</li>
     *   <li><b>不定义 {@code RabbitTemplateConfigurer} 这个 Bean</b>，直接注入自动装配好的那个。</li>
     * </ul>
     *
     * <p>这里<b>只记日志、不做补偿重发</b>。通知是辅助功能，丢一条就是少一条提醒；
     * 为它引入本地消息表 / 定时补偿，是把「少一条通知」升级成一套需要运维的机制，
     * 收益不成比例。加 confirm 的首要目的是<b>让丢消息可见</b> ——
     * 原先丢了完全无声无息，现在至少日志里有据可查。</p>
     *
     * <p>回调在 RabbitMQ 的 IO 线程上执行，绝不能在里面做阻塞操作（发 HTTP、查库）。</p>
     */
    @Bean
    public RabbitTemplate rabbitTemplate(RabbitTemplateConfigurer configurer,
                                         ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate();
        // 先让 Boot 应用 yml 里的 template.*（mandatory / retry / receive-timeout 等）
        configurer.configure(template, connectionFactory);

        // ① 交换机确认：消息到底有没有被 broker 收下
        template.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if (ack) {
                return;
            }
            // 走到这里说明 broker 明确拒收（磁盘满 / 队列不存在 / 内部错误），消息已经丢了
            log.error("MQ 消息未被交换机确认（已丢失）: id={}, cause={}",
                    correlationData == null ? "unknown" : correlationData.getId(), cause);
        });

        // ② 路由失败回退：到了交换机，但没有任何队列匹配这个 routingKey
        template.setReturnsCallback(returned -> log.error(
                "MQ 消息路由失败（已丢失，检查队列绑定）: exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));

        return template;
    }

    /**
     * 监听容器工厂：为消费者配置 JSON 反序列化转换器与并发参数。
     *
     * <p>用 {@link SimpleRabbitListenerContainerFactoryConfigurer} 而不是 new 一个裸工厂：
     * Spring Boot 的 yml 配置（acknowledge-mode / retry / prefetch / concurrency）
     * 是<b>先</b>应用到配置器持有的工厂上的。自己 new 一个工厂再 {@code @Bean} 出去，
     * 会把 yml 里那一整段配置全部覆盖掉 —— 重试策略静默失效是最容易踩的坑。
     * 所以这里接受 Boot 装配好的配置器，只补它还需要的东西。</p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${spring.rabbitmq.listener.simple.concurrency:1}") int concurrency,
            @Value("${spring.rabbitmq.listener.simple.max-concurrency:1}") int maxConcurrency,
            @Value("${spring.rabbitmq.listener.simple.prefetch:250}") int prefetch) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // 先让 Boot 把 yml 里的 listener.simple.* 全部灌进来（含 retry、acknowledge-mode）
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        // 再把并发参数显式写死一份：不依赖 yml 是否被完整读取，日志里也好排查
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(Math.max(concurrency, maxConcurrency));
        factory.setPrefetchCount(prefetch);
        return factory;
    }
}
