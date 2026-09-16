package com.fuhua.booknest.server.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuhua.booknest.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聊天帧跨实例广播中继（发布侧）。
 *
 * <p><b>它解决什么</b>：会话表是进程内的，用户 A 连在实例 1、用户 B 连在实例 2 时，
 * 实例 1 通过本地表找不到 B 的连接，消息就推不过去。这里把「该推给谁 + 推什么帧」
 * 发到 Redis 频道，所有实例都订阅，各自只推给自己本地持有的连接。</p>
 *
 * <p><b>为什么发送方所在实例也要发这条广播</b>：投递<b>不</b>依赖 Redis 的「回环」
 * （即发出去再收回来）。调用方会<b>先做本地推送、再调用这里广播</b>，
 * 本实例收到自己的广播时凭 {@code from} 字段跳过。这样：</p>
 * <ul>
 *   <li>Redis 不可用时，本地及同实例的接收方依然能实时收到（因为本地推送已经发生）；
 *       代价只是「连在其它实例上的接收方少一次实时推送，下次拉会话时会从 MySQL 读到」；</li>
 *   <li>不会因为「本地推 + 回环再推」而重复推送。</li>
 * </ul>
 *
 * <p><b>异常约定</b>：广播失败只记 WARN，不向上抛。聊天消息的事实来源是 MySQL，
 * 频道只是实时性的加速手段 —— 这正是项目铁律里「Redis 只是加速层」在消息链路上的体现。</p>
 */
@Component
@Slf4j
public class ChatPushRelay {

    /** 广播类型：私信，只推给一个用户 */
    public static final String KIND_PRIVATE = "P";
    /** 广播类型：群聊，推给一组用户（可排除发送方） */
    public static final String KIND_GROUP = "G";

    /** 信封字段名。帧本体以 JSON 字符串放进 {@link #FIELD_FRAME}，透传不做二次解析 */
    static final String FIELD_FROM = "from";
    static final String FIELD_KIND = "kind";
    static final String FIELD_TARGET = "target";
    static final String FIELD_TARGETS = "targets";
    static final String FIELD_EXCLUDE = "exclude";
    static final String FIELD_FRAME = "frame";

    private final StringRedisTemplate stringRedisTemplate;
    private final WsSessionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatPushRelay(StringRedisTemplate stringRedisTemplate, WsSessionRegistry registry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.registry = registry;
    }

    /**
     * 广播一条私信帧。
     *
     * @param receiverId 接收方用户ID
     * @param frameJson  已序列化好的下行帧
     */
    public void publishPrivate(String receiverId, String frameJson) {
        if (receiverId == null || receiverId.isEmpty() || frameJson == null) {
            return;
        }
        Map<String, Object> envelope = baseEnvelope(KIND_PRIVATE, frameJson);
        envelope.put(FIELD_TARGET, receiverId);
        publish(envelope);
    }

    /**
     * 广播一条群聊帧。
     *
     * @param memberIds     群成员ID（发送时快照；订阅方不再各自回查成员表，省掉每实例一次查询）
     * @param excludeUserId 需要跳过的用户ID（发送方已通过 ACK 拿到消息），可为 null
     * @param frameJson     已序列化好的下行帧
     */
    public void publishGroup(Collection<String> memberIds, String excludeUserId, String frameJson) {
        if (memberIds == null || memberIds.isEmpty() || frameJson == null) {
            return;
        }
        Map<String, Object> envelope = baseEnvelope(KIND_GROUP, frameJson);
        envelope.put(FIELD_TARGETS, memberIds);
        if (excludeUserId != null) {
            envelope.put(FIELD_EXCLUDE, excludeUserId);
        }
        publish(envelope);
    }

    private Map<String, Object> baseEnvelope(String kind, String frameJson) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        // 先放 from：订阅方要靠它认出自己发的消息并跳过，避免与本地推送重复
        envelope.put(FIELD_FROM, registry.getNodeId());
        envelope.put(FIELD_KIND, kind);
        envelope.put(FIELD_FRAME, frameJson);
        return envelope;
    }

    private void publish(Map<String, Object> envelope) {
        try {
            stringRedisTemplate.convertAndSend(RedisConstant.WS_PUSH_CHANNEL,
                    objectMapper.writeValueAsString(envelope));
        } catch (Exception ex) {
            log.warn("聊天帧跨实例广播失败（本实例投递不受影响，远端实例将在下次拉取时从库里读到）: {}",
                    ex.getMessage());
        }
    }
}
