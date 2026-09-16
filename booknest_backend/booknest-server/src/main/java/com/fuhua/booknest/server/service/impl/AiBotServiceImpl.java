package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.AiBotConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.dto.AiBotSaveDTO;
import com.fuhua.booknest.pojo.entity.AiBot;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.AiBotBriefVO;
import com.fuhua.booknest.pojo.vo.AiBotVO;
import com.fuhua.booknest.server.ai.AiBotChatClientFactory;
import com.fuhua.booknest.server.mapper.AiBotMapper;
import com.fuhua.booknest.server.mapper.PostCommentMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.AiBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 评论区 AI 机器人服务实现。
 *
 * <p>触发索引（机器人名 → 机器人ID）在进程内按 {@code TRIGGER_INDEX_TTL_MS} 缓存：
 * 评论是高频写入路径，每条评论都去查一次「全站可用机器人」不划算；
 * 而审核、启停、删除、改名都会立即让缓存失效，所以并不会读到过期的可用性。</p>
 */
@Service
@Slf4j
public class AiBotServiceImpl implements AiBotService {

    @Autowired
    private AiBotMapper aiBotMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PostCommentMapper postCommentMapper;
    @Autowired
    private AiBotChatClientFactory chatClientFactory;

    /** 触发索引：机器人名（小写）→ 机器人ID */
    private volatile Map<String, String> triggerIndex;
    private volatile long triggerIndexLoadedAt;

    /* ============================ 用户端 ============================ */

    @Override
    @Transactional
    public AiBotVO apply(AiBotSaveDTO dto) {
        String ownerId = requireLogin();

        String name = normalizeName(dto.getName());
        String provider = requireProvider(dto.getProvider());
        String apiKey = requireApiKey(dto.getApiKey());

        // 名称即触发词，重名会让 @ 产生歧义，必须全站唯一
        if (aiBotMapper.selectByName(name) != null) {
            throw new BaseException("机器人名称「" + name + "」已被占用，换一个吧");
        }
        // 单用户数量上限：这个功能会调用第三方付费接口，不限量容易被当资源池刷
        int owned = aiBotMapper.countByOwner(ownerId);
        if (owned >= AiBotConstant.MAX_BOTS_PER_USER) {
            throw new BaseException("最多只能创建 " + AiBotConstant.MAX_BOTS_PER_USER + " 个机器人");
        }

        AiBot bot = AiBot.builder()
                .id(UUID.randomUUID().toString())
                .ownerId(ownerId)
                .name(name)
                .avatar(trimToNull(dto.getAvatar()))
                .description(trimToNull(dto.getDescription()))
                .provider(provider)
                .baseUrl(AiBotConstant.PROVIDER_BASE_URL.get(provider))
                .model(resolveModel(provider, dto.getModel()))
                .apiKey(apiKey)
                .systemPrompt(trimToNull(dto.getSystemPrompt()))
                .temperature(resolveTemperature(dto.getTemperature()))
                .maxTokens(resolveMaxTokens(dto.getMaxTokens()))
                // 新建一律待审：审核的是「这套 Key + 这个提示词」，不能自己放行
                .auditStatus(AiBotConstant.AUDIT_PENDING)
                .rejectReason(null)
                .enabled(AiBotConstant.ENABLED)
                .replyCount(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        aiBotMapper.insert(bot);
        log.info("AI 机器人创建申请：id={}, name={}, ownerId={}, provider={}, model={}",
                bot.getId(), name, ownerId, provider, bot.getModel());
        return toVO(bot);
    }

    @Override
    public List<AiBotVO> listMine() {
        String ownerId = requireLogin();
        List<AiBot> bots = aiBotMapper.listByOwner(ownerId);
        List<AiBotVO> result = new ArrayList<>();
        if (bots == null) {
            return result;
        }
        for (AiBot bot : bots) {
            result.add(toVO(bot));
        }
        return result;
    }

    @Override
    public AiBotVO getMine(String id) {
        return toVO(requireOwned(id));
    }

    @Override
    @Transactional
    public AiBotVO update(String id, AiBotSaveDTO dto) {
        AiBot existing = requireOwned(id);

        String name = normalizeName(dto.getName());
        if (!name.equals(existing.getName())) {
            AiBot sameName = aiBotMapper.selectByName(name);
            if (sameName != null && !sameName.getId().equals(id)) {
                throw new BaseException("机器人名称「" + name + "」已被占用，换一个吧");
            }
        }
        String provider = requireProvider(dto.getProvider());
        String model = resolveModel(provider, dto.getModel());

        // 更新对象只填要改的字段：Mapper 里 null 即「不覆盖」，
        // 但 apiKey 例外 —— 前端不回传明文，留空代表沿用原 Key，这里显式塞回原值。
        AiBot update = AiBot.builder()
                .id(id)
                .name(name)
                .avatar(trimToNull(dto.getAvatar()))
                .description(trimToNull(dto.getDescription()))
                .provider(provider)
                .baseUrl(AiBotConstant.PROVIDER_BASE_URL.get(provider))
                .model(model)
                .apiKey(resolveApiKeyForUpdate(dto.getApiKey(), existing))
                .systemPrompt(trimToNull(dto.getSystemPrompt()))
                .temperature(resolveTemperature(dto.getTemperature()))
                .maxTokens(resolveMaxTokens(dto.getMaxTokens()))
                // 改配置 → 退回待审；驳回原因一并清空，避免旧理由挂在新配置上
                .auditStatus(AiBotConstant.AUDIT_PENDING)
                .rejectReason(null)
                .enabled(dto.getEnabled() == null
                        ? existing.getEnabled()
                        : (dto.getEnabled() ? AiBotConstant.ENABLED : AiBotConstant.DISABLED))
                .updateTime(LocalDateTime.now())
                .build();
        aiBotMapper.update(update);

        invalidateTriggerIndex();
        chatClientFactory.evict(id);
        log.info("AI 机器人配置更新，退回待审：id={}, name={}", id, name);
        return toVO(requireExisting(id));
    }

    @Override
    @Transactional
    public void delete(String id) {
        doDelete(requireOwned(id));
    }

    @Override
    @Transactional
    public void deleteByAdmin(String id) {
        // 管理端不受归属限制：用户把 Key 填错、或机器人发过违规内容时，
        // 管理员得能直接清掉，而不是反过来求创建者自己去删
        doDelete(requireExisting(id));
    }

    /**
     * 真正落库删除。归属校验由调用方完成 —— 用户端校验 owner，管理端不校验。
     *
     * <p>历史回复一并清掉：机器人从列表消失后，那些评论既解释不清也没人管得了。</p>
     *
     * @param bot 已确认存在的机器人
     */
    private void doDelete(AiBot bot) {
        String id = bot.getId();
        int removed = postCommentMapper.countByBotId(id);
        postCommentMapper.deleteByBotId(id);
        aiBotMapper.deleteById(id);
        invalidateTriggerIndex();
        chatClientFactory.evict(id);
        log.info("AI 机器人已删除：id={}, name={}, 连带删除历史回复 {} 条", id, bot.getName(), removed);
    }

    @Override
    public AiBotVO setEnabled(String id, boolean enabled) {
        requireOwned(id);
        aiBotMapper.updateEnabled(id, enabled ? AiBotConstant.ENABLED : AiBotConstant.DISABLED);
        invalidateTriggerIndex();
        log.info("AI 机器人{}：id={}", enabled ? "启用" : "停用", id);
        return toVO(requireExisting(id));
    }

    @Override
    public List<AiBotBriefVO> listAvailable() {
        List<AiBot> bots = aiBotMapper.listEnabledApproved();
        List<AiBotBriefVO> result = new ArrayList<>();
        if (bots == null) {
            return result;
        }
        for (AiBot bot : bots) {
            result.add(AiBotBriefVO.builder()
                    .id(bot.getId())
                    .name(bot.getName())
                    .avatar(bot.getAvatar())
                    .description(bot.getDescription())
                    .providerLabel(AiBotConstant.PROVIDER_LABEL.get(bot.getProvider()))
                    .model(bot.getModel())
                    .replyCount(bot.getReplyCount())
                    .build());
        }
        return result;
    }

    /* ============================ 管理端 ============================ */

    @Override
    public List<AiBotVO> listForAudit(Integer auditStatus) {
        List<AiBot> bots = aiBotMapper.listForAudit(auditStatus);
        List<AiBotVO> result = new ArrayList<>();
        if (bots == null) {
            return result;
        }
        // 同一个作者可能一次提多个，昵称查一次就够
        Map<String, String> nameCache = new LinkedHashMap<>();
        for (AiBot bot : bots) {
            String ownerName = nameCache.computeIfAbsent(bot.getOwnerId(), this::lookupUserName);
            result.add(toVO(bot, ownerName));
        }
        return result;
    }

    @Override
    public AiBotVO audit(String id, boolean approve, String rejectReason) {
        AiBot bot = requireExisting(id);
        String reason = trimToNull(rejectReason);
        if (!approve && reason == null) {
            throw new BaseException("驳回时必须填写原因");
        }
        aiBotMapper.audit(id, approve ? AiBotConstant.AUDIT_APPROVED : AiBotConstant.AUDIT_REJECTED,
                approve ? null : reason);
        invalidateTriggerIndex();
        log.info("AI 机器人审核：id={}, name={}, approve={}", id, bot.getName(), approve);
        return toVO(requireExisting(id));
    }

    /* ============================ 运行期 ============================ */

    @Override
    public List<AiBot> matchTriggered(String content) {
        if (content == null || content.isEmpty() || content.indexOf(AiBotConstant.TRIGGER_PREFIX.charAt(0)) < 0) {
            return Collections.emptyList();
        }
        Map<String, String> index = triggerIndexOf();
        if (index.isEmpty()) {
            return Collections.emptyList();
        }

        // 命中的机器人ID（保持 @ 出现的先后顺序，LinkedHashMap 去重）
        Map<String, Boolean> hitIds = new LinkedHashMap<>();
        for (int at = content.indexOf(AiBotConstant.TRIGGER_PREFIX.charAt(0)); at >= 0;
                at = content.indexOf(AiBotConstant.TRIGGER_PREFIX.charAt(0), at + 1)) {
            String botId = matchAt(content, at, index);
            if (botId != null) {
                hitIds.putIfAbsent(botId, Boolean.TRUE);
                if (hitIds.size() >= AiBotConstant.MAX_TRIGGERS_PER_COMMENT) {
                    break;
                }
            }
        }
        if (hitIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiBot> bots = aiBotMapper.selectByIds(new ArrayList<>(hitIds.keySet()));
        if (bots == null || bots.isEmpty()) {
            return Collections.emptyList();
        }
        // 再兜一次可用性：索引有 TTL，期间机器人可能刚被停用 / 驳回 / 删除
        List<AiBot> result = new ArrayList<>();
        for (AiBot bot : bots) {
            if (isUsable(bot)) {
                result.add(bot);
            }
        }
        return result;
    }

    @Override
    public AiBot getById(String id) {
        return id == null ? null : aiBotMapper.selectById(id);
    }

    /* ============================ 内部：触发词匹配 ============================ */

    /**
     * 从 {@code content} 的第 {@code at} 个字符（必须是 @）开始做最长前缀匹配。
     *
     * <p>先按最长可能的名字长度截一段尾串，再从长到短逐个前缀去索引里查。
     * 从长到短是必要的：有两个机器人「小书」和「小书虫」时，
     * 「@小书虫你好」必须命中「小书虫」而不是先命中「小书」。</p>
     *
     * @param content 评论正文
     * @param at      @ 所在下标
     * @param index   机器人名（小写）→ ID
     * @return 命中的机器人ID；没命中返回 null
     */
    private String matchAt(String content, int at, Map<String, String> index) {
        int tailEnd = Math.min(content.length(), at + 1 + AiBotConstant.NAME_MAX_LEN);
        String tail = content.substring(at + 1, tailEnd);
        int maxLen = Math.min(tail.length(), AiBotConstant.NAME_MAX_LEN);
        for (int len = maxLen; len >= AiBotConstant.NAME_MIN_LEN; len--) {
            String candidate = tail.substring(0, len).toLowerCase();
            String botId = index.get(candidate);
            if (botId != null) {
                return botId;
            }
        }
        return null;
    }

    /** 取触发索引（超过 TTL 就重建） */
    private Map<String, String> triggerIndexOf() {
        Map<String, String> cached = triggerIndex;
        if (cached != null && System.currentTimeMillis() - triggerIndexLoadedAt < AiBotConstant.TRIGGER_INDEX_TTL_MS) {
            return cached;
        }
        synchronized (this) {
            if (triggerIndex != null
                    && System.currentTimeMillis() - triggerIndexLoadedAt < AiBotConstant.TRIGGER_INDEX_TTL_MS) {
                return triggerIndex;
            }
            Map<String, String> rebuilt = new LinkedHashMap<>();
            List<AiBot> bots = aiBotMapper.listEnabledApproved();
            if (bots != null) {
                for (AiBot bot : bots) {
                    if (bot.getName() != null && bot.getId() != null) {
                        // 名称大小写不敏感匹配（MySQL 的 utf8mb4_unicode_ci 本身也不区分大小写）
                        rebuilt.put(bot.getName().toLowerCase(), bot.getId());
                    }
                }
            }
            triggerIndex = Collections.unmodifiableMap(rebuilt);
            triggerIndexLoadedAt = System.currentTimeMillis();
            log.debug("AI 机器人触发索引已重建，可用机器人 {} 个", rebuilt.size());
            return triggerIndex;
        }
    }

    /** 审核 / 启停 / 增删改后立即让触发索引失效，不必等 TTL */
    private void invalidateTriggerIndex() {
        triggerIndex = null;
    }

    /** 机器人当前是否可用于触发 */
    private boolean isUsable(AiBot bot) {
        return bot != null
                && Integer.valueOf(AiBotConstant.AUDIT_APPROVED).equals(bot.getAuditStatus())
                && Integer.valueOf(AiBotConstant.ENABLED).equals(bot.getEnabled());
    }

    /* ============================ 内部：校验与转换 ============================ */

    private String requireLogin() {
        String userId = BaseContext.getCurrentId();
        if (userId == null || userId.isBlank()) {
            throw new BaseException("请先登录");
        }
        return userId;
    }

    private AiBot requireExisting(String id) {
        AiBot bot = id == null ? null : aiBotMapper.selectById(id);
        if (bot == null) {
            throw new BaseException("机器人不存在");
        }
        return bot;
    }

    /** 取机器人并校验归属：只能操作自己创建的 */
    private AiBot requireOwned(String id) {
        AiBot bot = requireExisting(id);
        String userId = requireLogin();
        if (!userId.equals(bot.getOwnerId())) {
            throw new BaseException("只能操作自己创建的机器人");
        }
        return bot;
    }

    private String normalizeName(String raw) {
        String name = trimToNull(raw);
        if (name == null) {
            throw new BaseException("机器人名称不能为空");
        }
        if (!AiBotConstant.isValidName(name)) {
            throw new BaseException("机器人名称只能包含中文、字母、数字、下划线和连字符，长度 "
                    + AiBotConstant.NAME_MIN_LEN + "~" + AiBotConstant.NAME_MAX_LEN + " 位");
        }
        return name;
    }

    private String requireProvider(String raw) {
        String provider = AiBotConstant.normalizeProvider(raw);
        if (provider == null) {
            throw new BaseException("暂不支持该模型厂商，目前可选：DeepSeek、阿里云百炼");
        }
        return provider;
    }

    private String requireApiKey(String raw) {
        String key = trimToNull(raw);
        if (key == null) {
            throw new BaseException("API Key 不能为空");
        }
        if (key.length() > AiBotConstant.API_KEY_MAX_LEN) {
            throw new BaseException("API Key 过长");
        }
        return key;
    }

    /** 编辑时 Key 留空 = 沿用原值 */
    private String resolveApiKeyForUpdate(String raw, AiBot existing) {
        String key = trimToNull(raw);
        return key == null ? existing.getApiKey() : requireApiKey(key);
    }

    private String resolveModel(String provider, String raw) {
        String model = trimToNull(raw);
        return model == null ? AiBotConstant.PROVIDER_DEFAULT_MODEL.get(provider) : model;
    }

    private BigDecimal resolveTemperature(Double raw) {
        double value = raw == null ? AiBotConstant.DEFAULT_TEMPERATURE : raw;
        if (value < 0 || value > 2) {
            throw new BaseException("温度取值范围是 0~2");
        }
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private Integer resolveMaxTokens(Integer raw) {
        int value = raw == null ? AiBotConstant.DEFAULT_MAX_TOKENS : raw;
        if (value < AiBotConstant.MAX_TOKENS_MIN || value > AiBotConstant.MAX_TOKENS_MAX) {
            throw new BaseException("单条回复上限取值范围是 "
                    + AiBotConstant.MAX_TOKENS_MIN + "~" + AiBotConstant.MAX_TOKENS_MAX);
        }
        return value;
    }

    private String lookupUserName(String userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.getUserById(userId);
        return user == null ? null : user.getUsername();
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        return v.isEmpty() ? null : v;
    }

    private AiBotVO toVO(AiBot bot) {
        return toVO(bot, lookupUserName(bot.getOwnerId()));
    }

    private AiBotVO toVO(AiBot bot, String ownerName) {
        List<String> modelOptions = AiBotConstant.PROVIDER_MODELS.getOrDefault(
                bot.getProvider(), Collections.emptyList());
        return AiBotVO.builder()
                .id(bot.getId())
                .ownerId(bot.getOwnerId())
                .ownerName(ownerName)
                .name(bot.getName())
                .avatar(bot.getAvatar())
                .description(bot.getDescription())
                .provider(bot.getProvider())
                .providerLabel(AiBotConstant.PROVIDER_LABEL.get(bot.getProvider()))
                .baseUrl(bot.getBaseUrl())
                .model(bot.getModel())
                .apiKeyMasked(maskApiKey(bot.getApiKey()))
                .apiKeySet(bot.getApiKey() != null && !bot.getApiKey().isBlank())
                .systemPrompt(bot.getSystemPrompt())
                .temperature(bot.getTemperature() == null ? null : bot.getTemperature().doubleValue())
                .maxTokens(bot.getMaxTokens())
                .auditStatus(bot.getAuditStatus())
                .rejectReason(bot.getRejectReason())
                .enabled(bot.getEnabled())
                .replyCount(bot.getReplyCount())
                .createTime(bot.getCreateTime())
                .updateTime(bot.getUpdateTime())
                .modelOptions(modelOptions)
                .build();
    }

    /**
     * API Key 脱敏：保留前 4 后 4 位。
     * 明文永不外发 —— 创建者自己填的也一样，避免前端页面成为密钥泄露面。
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
