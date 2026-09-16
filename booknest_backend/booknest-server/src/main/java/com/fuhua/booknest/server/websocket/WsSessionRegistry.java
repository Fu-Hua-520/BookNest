package com.fuhua.booknest.server.websocket;

import com.fuhua.booknest.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket 本地会话注册表 + 跨实例在线态。
 *
 * <p><b>职责边界</b>：这里只管「本节点有哪些连接」这件纯本地的事，
 * 以及把这件事实同步到 Redis 供其它节点查询。它<b>不做序列化</b> ——
 * 帧由 {@link ChatWebSocketHandler} 生成好以后以字符串形式传进来，
 * 这样跨实例转发可以原样透传，不会出现「解析一次再序列化一次」造成的格式漂移
 * （{@code LocalDateTime} 是最容易被这种漂移改坏的类型）。</p>
 *
 * <p><b>为什么要单独抽出来</b>：原先会话表是 {@code ChatWebSocketHandler} 里的一个
 * {@code static Map}。跨实例投递需要「别的实例收到广播后也往自己的本地连接推」，
 * 如果推送逻辑还锁在 handler 里，就会形成
 * handler → relay（要推送时回指 handler）→ handler 的循环依赖。
 * 抽成独立组件后依赖是单向的：handler → registry，relay → registry，listener → registry。</p>
 *
 * <p><b>Redis 降级约定</b>（与项目铁律一致）：在线态写坏 / 查询失败一律只记 WARN 并
 * 退化为「仅凭本节点判断」，绝不让 Redis 异常冒泡到业务 —— 在线态只是显示用的派生数据，
 * 消息本身的事实来源始终是 MySQL 的 {@code chat_message} 表。</p>
 */
@Component
@Slf4j
public class WsSessionRegistry {

    /**
     * 本节点的在线用户会话映射：userId -> Set&lt;WebSocketSession&gt;
     *
     * <p>同一用户多端（多标签页 / 多设备）连接各自独立，互不覆盖。
     * 用 Set 而不是 List 是因为 WebSocketSession 的 equals 基于连接身份，
     * 重复加入同一连接应当是幂等的。</p>
     */
    private static final Map<String, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 本节点身份：主机名 + 短随机后缀。
     * 两个用途：作为在线态 ZSET 的 member（区分不同实例），
     * 以及作为广播消息里的 {@code from} 字段（识别出自己发的消息并跳过，避免重复推送）。
     */
    private final String nodeId;

    public WsSessionRegistry(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.nodeId = buildNodeId();
    }

    /** 本节点身份，供 relay 打标、listener 识别自环消息 */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * 登记一条新连接，并把本节点在该用户在线态里的时间戳推到当前时刻。
     */
    public void register(String userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        touchPresence(userId);
    }

    /**
     * 注销一条连接。
     *
     * <p>只有当本节点已无该用户的任何连接时才摘掉本节点在在线态里的成员 ——
     * 否则同一用户在同一个节点的第二个连接会被误判为离线。
     * 这与原先 handler 里「仅当集合为空才 remove 映射」的写法保持一致。</p>
     */
    public void unregister(String userId, WebSocketSession session) {
        if (userId == null || session == null) {
            return;
        }
        Set<WebSocketSession> sessions = SESSIONS.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty() && SESSIONS.remove(userId, sessions)) {
            clearPresence(userId);
        }
    }

    /**
     * 续期在线态：把本节点在该用户 ZSET 里的 score 更新为当前时间戳。
     *
     * <p>由建连与每一次心跳帧驱动。心跳是「定时发」的（前端约 25 秒一次，见
     * {@link ChatWebSocketHandler} 对 ping 的处理），所以不需要额外的续期线程 ——
     * 本项目没有、也不允许有调度器。</p>
     */
    public void touchPresence(String userId) {
        if (userId == null) {
            return;
        }
        try {
            String key = RedisConstant.WS_ONLINE + userId;
            stringRedisTemplate.opsForZSet().add(key, nodeId, System.currentTimeMillis());
            stringRedisTemplate.expire(key, RedisConstant.WS_ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("在线态续期失败，降级为仅本地判断 user={}: {}", userId, ex.getMessage());
        }
    }

    /**
     * 判断用户是否在线（跨实例）。
     *
     * <p>先走本地快路径；本地没有该用户的连接时才查 Redis，并顺手把
     * 「score 已过期」的节点成员摘掉 —— 这就是崩溃实例的幽灵成员被回收的时机，
     * 因此不需要任何后台任务。</p>
     *
     * @return 是否在线；Redis 不可用时退化为「仅本节点判断」
     */
    public boolean isUserOnline(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        if (hasLocalOpenSession(userId)) {
            return true;
        }
        try {
            String key = RedisConstant.WS_ONLINE + userId;
            long staleBefore = System.currentTimeMillis() - RedisConstant.WS_ONLINE_TTL_SECONDS * 1000L;
            stringRedisTemplate.opsForZSet().removeRangeByScore(key, Double.NEGATIVE_INFINITY, staleBefore);
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            return size != null && size > 0;
        } catch (Exception ex) {
            log.warn("在线态查询失败，降级为不在线 user={}: {}", userId, ex.getMessage());
            return false;
        }
    }

    /**
     * 向某用户的全部本地连接推送一帧文本。
     *
     * @param userId 接收方用户ID
     * @param frame  已经生成好的帧内容（原样发送，不再二次处理）
     */
    public void pushFrameToUser(String userId, String frame) {
        if (userId == null || userId.isEmpty() || frame == null) {
            return;
        }
        Set<WebSocketSession> sessions = SESSIONS.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sendFrame(session, frame);
        }
    }

    /**
     * 向一批用户的本地连接推送同一帧，可排除某个用户（群聊里发送方已通过 ACK 拿到消息）。
     *
     * @param userIds       接收方用户ID集合
     * @param excludeUserId 需要跳过的用户ID，可为 null
     * @param frame         已经生成好的帧内容
     */
    public void pushFrameToUsers(Collection<String> userIds, String excludeUserId, String frame) {
        if (userIds == null || userIds.isEmpty() || frame == null) {
            return;
        }
        for (String userId : userIds) {
            if (userId == null || userId.isEmpty() || userId.equals(excludeUserId)) {
                continue;
            }
            pushFrameToUser(userId, frame);
        }
    }

    /**
     * 向单个会话发送一帧文本。
     *
     * <p>心跳 {@code pong} 与业务 JSON 帧都走这里 —— 对 WebSocket 而言它们同样是文本帧，
     * 区别只在内容，没必要分成两条路径。</p>
     */
    public void sendFrame(WebSocketSession session, String frame) {
        if (session == null || !session.isOpen() || frame == null) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(frame));
        } catch (Exception ex) {
            log.error("WebSocket 消息发送失败: {}", ex.getMessage());
        }
    }

    /** 本节点是否有该用户的、仍处于打开状态的连接 */
    private boolean hasLocalOpenSession(String userId) {
        Set<WebSocketSession> sessions = SESSIONS.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                return true;
            }
        }
        return false;
    }

    private void clearPresence(String userId) {
        try {
            stringRedisTemplate.opsForZSet().remove(RedisConstant.WS_ONLINE + userId, nodeId);
        } catch (Exception ex) {
            log.warn("在线态清理失败 user={}: {}", userId, ex.getMessage());
        }
    }

    /** 主机名 + 8 位随机后缀；主机名取不到时退化为纯随机，只要节点间互不相同即可 */
    private static String buildNodeId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            host = "node";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
