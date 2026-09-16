package com.fuhua.booknest.server.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化配置 —— 缓存对象（List/Set/Map/VO）走 JSON。
 *
 * <p>为什么需要它：Spring Boot 默认给的 {@code RedisTemplate<Object,Object>} 用的是
 * JDK 序列化，写进 Redis 的是一串二进制。已有的 {@code StringRedisTemplate}
 * 应付限流计数足够，但缓存列表/集合时没法读、也没法在 redis-cli 里排查。</p>
 *
 * <h3>⚠️ 缓存用的 ObjectMapper 绝不能注册成 Bean（踩过的坑，务必读完再改）</h3>
 * <p>缓存必须写类型信息（{@code @class}），HTTP 响应体必须<u>不</u>写 —— 两者诉求相反。
 * 直觉做法是「声明一个独立的 {@code ObjectMapper} Bean 给 Redis 用」，但那会**一次性打挂整个 HTTP 层**：</p>
 * <ol>
 *   <li>Spring Boot 的 {@code jacksonObjectMapper} 带 {@code @ConditionalOnMissingBean}，
 *       一旦容器里已经有任何 {@code ObjectMapper} Bean，它就<b>直接退避、不再创建</b>；</li>
 *   <li>于是带 {@code activateDefaultTyping} 的这个 mapper 成了容器里<b>唯一</b>的
 *       {@code ObjectMapper}；</li>
 *   <li>Spring MVC 的 {@code MappingJackson2HttpMessageConverter} 是按类型注入
 *       {@code ObjectMapper} 的，于是 HttpMessageConverter 拿到了它。</li>
 * </ol>
 * <p>症状（都已在运行时复现过）：</p>
 * <ul>
 *   <li><b>登录等一切 POST 全挂</b>：前端不会在请求体里写 {@code @class}，反序列化直接报
 *       {@code Could not resolve subtype of ... UserLoginDTO: missing type id property '@class'}；</li>
 *   <li><b>所有列表接口「查得到但显示不出来」</b>：响应变成
 *       {@code {"@class":"...Result","data":["java.util.ArrayList",[...]]}}，
 *       前端把 {@code data} 当数组用就拿到一个「首元素是字符串的二元数组」，列表全空
 *       （数据其实一条没少，所以极其容易误判成「后端查不出数据」）。</li>
 * </ul>
 * <p>所以这里<b>刻意不把 {@code buildRedisObjectMapper()} 注册成 Bean</b>，只作为
 * {@link #redisTemplate} 内部的私有构造细节。这样 Boot 的 {@code jacksonObjectMapper}
 * 会正常创建并继续服务 HTTP，两个 mapper 各管一段、互不污染。</p>
 * <p>另外注意：{@code @Bean} 方法参数是<b>按类型</b>解析的，参数名 {@code redisObjectMapper}
 * 只在「多个候选且无 @Primary」时才作为兜底匹配 —— 不能指望靠参数名把 Bean 选对。</p>
 *
 * <p>这个 mapper 自身的几个取舍：</p>
 * <ul>
 *   <li><b>开 {@code activateDefaultTyping}（写入类型信息）</b>：缓存里存的多半是
 *       {@code List<Tag>}、{@code Set<String>} 这类泛型擦除后的类型。不写类型信息，
 *       反序列化出来会变成 {@code List<LinkedHashMap>}，强转 {@code List<Tag>} 直接
 *       在运行时抛 ClassCastException（编译期完全看不出来）。</li>
 *   <li><b>注册 {@code JavaTimeModule} 并关掉时间戳输出</b>：实体里有
 *       {@code LocalDateTime}（Tag.createTime、BarVO.createTime 等），
 *       不注册 JSR310 模块会直接抛 {@code InvalidDefinitionException}。</li>
 *   <li><b>{@code NON_NULL} 不输出 null</b>：缓存体积小一圈，语义上也更干净。</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    /**
     * 通用 RedisTemplate：key 用字符串、value 用 JSON，方便 redis-cli 直接看。
     *
     * <p>与 {@code StringRedisTemplate} 并存：限流那种纯整数值仍用后者
     * （它读写的是裸数字，不需要 JSON 包装，也少一层序列化开销）；
     * 计数缓冲同样用它的 {@code opsForHash()} 走裸字符串，见 {@code CountBuffer}。</p>
     *
     * <p>注意这里<b>不接收 {@code ObjectMapper} 参数</b>：一旦这么做，就等于把容器里
     * 那个 ObjectMapper 绑进来（而它本该是 Boot 给 HTTP 用的那个）。缓存专用的 mapper
     * 由 {@link #buildRedisObjectMapper()} 就地构造，见类注释。</p>
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * 构造缓存专用的 ObjectMapper。
     *
     * <p><b>刻意是 private 方法而不是 {@code @Bean}</b>：它一旦成为 Bean，就会顶掉
     * Spring Boot 的 {@code jacksonObjectMapper}（{@code @ConditionalOnMissingBean}），
     * 进而让 MVC 的 HttpMessageConverter 用上带类型的 mapper，把整个 HTTP 层打挂。
     * 详见类注释里的症状清单。</p>
     */
    private ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 实体是 private 字段 + getter/setter（Lombok @Data），
        // 只认字段可见性，避免依赖 bean 属性命名推断
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // 写入 @class 类型信息，保证反序列化回原始类型而非 LinkedHashMap
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        mapper.registerModule(new JavaTimeModule());
        // LocalDateTime 序列化成 ISO 字符串而不是 [2026,9,15,0,14] 数组
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 反序列化时忽略 JSON 里多出来的字段：VO 加字段不会让旧缓存直接爆掉
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
