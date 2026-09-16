package com.fuhua.booknest.server.ai;

import com.fuhua.booknest.common.constant.AiBotConstant;
import com.fuhua.booknest.pojo.entity.AiBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态 {@link ChatClient} 工厂。
 *
 * <p>每个 AI 机器人都有自己的一套「厂商 + base-url + 模型 + API Key」，
 * 这些东西是用户在运行时填进来的，不可能在启动时写死在 application.yml 里，
 * 所以这里按需构造：{@link OpenAiApi} → {@link OpenAiChatModel} → {@link ChatClient}，
 * 全部通过 Spring AI 的 <b>OpenAI 兼容</b>模块完成（DeepSeek 与百炼都走 OpenAI 协议，
 * 不引入任何厂商专用 SDK）。</p>
 *
 * <p><b>路径拼接：</b>{@code OpenAiApi} 的默认补全路径是
 * {@code /v1/chat/completions}，所以 {@code baseUrl} 必须以
 * <b>不带 {@code /v1}</b> 的形式传入，否则会拼成
 * {@code .../v1/v1/chat/completions} → 404。厂商与 base-url 的对应关系见
 * {@link AiBotConstant#PROVIDER_BASE_URL}。</p>
 *
 * <p><b>为什么要缓存：</b>构造 {@code OpenAiChatModel} 会连带建一套 RestClient/WebClient
 * 与重试模板，每次评论都新建代价偏高；而配置极少变动，所以按
 * {@code id + 厂商 + base-url + 模型 + Key指纹} 做缓存，Key 或模型一改指纹就变，
 * 自然拿到新实例。缓存放不下时整体清空重建，避免无界增长。</p>
 */
@Component
@Slf4j
public class AiBotChatClientFactory {

    /** 配置指纹 → ChatClient */
    private final Map<String, ChatClient> cache = new ConcurrentHashMap<>();

    /**
     * 取得（或构造）某个机器人对应的 ChatClient。
     *
     * @param bot 机器人配置（必须已带 apiKey / baseUrl / model）
     * @return 可用于发起对话的 ChatClient
     */
    public ChatClient getChatClient(AiBot bot) {
        if (bot == null || bot.getApiKey() == null || bot.getApiKey().isBlank()
                || bot.getBaseUrl() == null || bot.getBaseUrl().isBlank()
                || bot.getModel() == null || bot.getModel().isBlank()) {
            throw new IllegalArgumentException("机器人模型配置不完整（base-url / model / api-key 均不能为空）");
        }
        String key = fingerprint(bot);
        ChatClient cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        ChatClient created = build(bot);
        // 缓存上限保护：达到上限直接清空，比引入 LRU 依赖简单，且这种量级下重建成本可接受
        if (cache.size() >= AiBotConstant.CHAT_CLIENT_CACHE_LIMIT) {
            log.warn("AI 机器人 ChatClient 缓存达到上限 {}，整体清空重建", AiBotConstant.CHAT_CLIENT_CACHE_LIMIT);
            cache.clear();
        }
        cache.put(key, created);
        log.info("构建 AI 机器人 ChatClient: botId={}, provider={}, model={}, baseUrl={}",
                bot.getId(), bot.getProvider(), bot.getModel(), bot.getBaseUrl());
        return created;
    }

    /**
     * 清掉某个机器人的缓存（改配置 / 换 Key / 停用 / 删除后调用）。
     * 指纹以 botId 开头，因此前缀匹配即可。
     *
     * @param botId 机器人ID
     */
    public void evict(String botId) {
        if (botId == null) {
            return;
        }
        cache.keySet().removeIf(k -> k.startsWith(botId + "#"));
    }

    /* ------------------------- 内部实现 ------------------------- */

    /**
     * 构造 ChatClient。
     * <p>不指定 toolCallingManager 等可选参数 —— Spring AI 1.0.9 的 Builder 在
     * 私有构造里已经填好了默认的重试模板、观测注册表与工具执行判定器，
     * {@code build()} 也会在 toolCallingManager 为空时回落到内置实现。</p>
     */
    private ChatClient build(AiBot bot) {
        // 请求超时必须显式设置：Spring AI 底层 RestClient 默认没有读超时，
        // 对方接口卡住会把线程池线程一直占着，把后续所有 @ 请求都堵死。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(AiBotConstant.REPLY_TIMEOUT_SECONDS));

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(bot.getBaseUrl())
                .apiKey(bot.getApiKey())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(bot.getModel())
                .temperature(resolveTemperature(bot))
                .maxTokens(resolveMaxTokens(bot))
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        return ChatClient.builder(chatModel).build();
    }

    private Double resolveTemperature(AiBot bot) {
        if (bot.getTemperature() == null) {
            return AiBotConstant.DEFAULT_TEMPERATURE;
        }
        return bot.getTemperature().doubleValue();
    }

    private Integer resolveMaxTokens(AiBot bot) {
        return bot.getMaxTokens() == null ? AiBotConstant.DEFAULT_MAX_TOKENS : bot.getMaxTokens();
    }

    /**
     * 配置指纹：同样配置的机器人共用实例；任一配置变化指纹即变。
     * API Key 只参与 hashCode，不落日志、不落缓存键明文。
     */
    private String fingerprint(AiBot bot) {
        return bot.getId() + "#" + bot.getProvider() + "#" + bot.getBaseUrl() + "#" + bot.getModel()
                + "#" + Integer.toHexString(String.valueOf(bot.getApiKey()).hashCode());
    }
}
