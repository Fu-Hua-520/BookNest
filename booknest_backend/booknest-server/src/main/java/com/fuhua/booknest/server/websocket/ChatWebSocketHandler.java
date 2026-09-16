package com.fuhua.booknest.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fuhua.booknest.pojo.vo.ChatMsgVO;
import com.fuhua.booknest.server.service.ChatGroupService;
import com.fuhua.booknest.server.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 私信 / 群聊 WebSocket 处理器
 *
 * <p>上行帧契约（type 决定走哪条链路）：</p>
 * <pre>
 *   私聊 {"type":"CHAT","receiverId":"xxx","content":"xxx","msgType":"TEXT|IMAGE"}
 *   群聊 {"type":"GROUP","groupId":"xxx","content":"xxx","msgType":"TEXT|IMAGE"}
 * </pre>
 * <p>下行帧：ACK 回执发给发送方，MESSAGE / GROUP_MESSAGE 推给接收方（对象字段展平到同层）。</p>
 *
 * <h3>跨实例投递</h3>
 * <p>接收方的连接可能握在<b>别的实例</b>上，本地会话表查不到。所以推送分两步：</p>
 * <ol>
 *   <li><b>本地先推</b>（{@link WsSessionRegistry#pushFrameToUser}）—— 同实例的接收方立刻收到，
 *       且 Redis 不可用时这条路径照常工作；</li>
 *   <li><b>再广播</b>（{@link ChatPushRelay}）—— 其它实例收到后推给自己的本地连接。</li>
 * </ol>
 * <p>本实例也会收到自己的广播，但凭 {@code from} 字段跳过，因此不会重复推送。
 * ACK 不走广播：发送方必然连在本实例上，直接回本连接即可。</p>
 */
@Component
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /** 上行消息类型：发送私信。前端契约使用 CHAT，兼容早期文档里的 SEND */
    private static final String TYPE_CHAT = "CHAT";
    private static final String TYPE_SEND = "SEND";
    /** 上行消息类型：发送群消息 */
    private static final String TYPE_GROUP = "GROUP";

    /** 下行消息类型 */
    private static final String DOWN_ACK = "ACK";
    private static final String DOWN_PRIVATE = "MESSAGE";
    private static final String DOWN_GROUP = "GROUP_MESSAGE";

    /** 心跳帧：纯文本 ping / pong，不走 JSON 解析 */
    private static final String PING = "ping";
    private static final String PONG = "pong";

    private final ChatService chatService;
    private final ChatGroupService chatGroupService;
    private final WsSessionRegistry registry;
    private final ChatPushRelay relay;
    private final ObjectMapper objectMapper;

    /**
     * 构造器：注入私信/群聊服务、会话注册表与跨实例广播中继，并初始化 JSON 序列化器（支持 LocalDateTime）
     */
    public ChatWebSocketHandler(ChatService chatService, ChatGroupService chatGroupService,
                                WsSessionRegistry registry, ChatPushRelay relay) {
        this.chatService = chatService;
        this.chatGroupService = chatGroupService;
        this.registry = registry;
        this.relay = relay;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 建立连接后，将用户会话加入本地在线映射，并在 Redis 侧登记在线态
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserId(session);
        if (userId != null) {
            registry.register(userId, session);
            log.info("用户 {} 建立 WebSocket 连接", userId);
        }
    }

    /**
     * 连接关闭后，将用户会话从本地在线映射移除；本节点已无该用户连接时一并摘掉在线态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserId(session);
        if (userId != null) {
            registry.unregister(userId, session);
            log.info("用户 {} 断开 WebSocket 连接", userId);
        }
    }

    /**
     * 处理客户端文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String senderId = getUserId(session);
        if (senderId == null) {
            return;
        }
        String payload = message.getPayload();
        // 心跳帧：前端每 25s 发一次纯文本 ping，这里直接回 pong。
        // 必须拦在 JSON 解析之前，否则 readTree("ping") 抛异常并刷 WARN 日志。
        // 顺带用它续期在线态 —— 心跳是定时发的，正好可以当在线态的心跳，省掉一个续期线程。
        if (payload != null && PING.equalsIgnoreCase(payload.trim())) {
            registry.touchPresence(senderId);
            registry.sendFrame(session, PONG);
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(payload);
        } catch (Exception ex) {
            log.warn("聊天消息解析失败: {}", ex.getMessage());
            return;
        }
        // 类型不匹配时静默 return 会让前端「点了发送没反应却毫无线索」，
        // 这里显式记一条 WARN，便于排查协议不一致。
        String type = node == null ? "" : node.path("type").asText();
        String content = node.path("content").asText(null);
        if (content == null || content.isEmpty()) {
            return;
        }
        String msgType = node.path("msgType").asText(null);

        if (TYPE_GROUP.equals(type)) {
            handleGroupMessage(session, senderId, node.path("groupId").asText(null), content, msgType);
            return;
        }
        if (TYPE_CHAT.equals(type) || TYPE_SEND.equals(type)) {
            handlePrivateMessage(session, senderId, node.path("receiverId").asText(null), content, msgType);
            return;
        }
        log.warn("忽略未知的聊天消息类型: {}", type);
    }

    /** 私聊：落库 → 回 ACK → 推送接收方所有在线端（本地直推 + 跨实例广播） */
    private void handlePrivateMessage(WebSocketSession session, String senderId, String receiverId,
                                      String content, String msgType) {
        if (receiverId == null || receiverId.isEmpty()) {
            return;
        }
        ChatMsgVO msgVO;
        try {
            msgVO = chatService.saveAndPushMessage(senderId, receiverId, content, msgType);
        } catch (Exception ex) {
            // 落库失败（如超过长度限制）必须回执给发送方，否则前端会一直停在乐观气泡上
            log.warn("私信保存失败 sender={} receiver={}: {}", senderId, receiverId, ex.getMessage());
            sendToSession(session, wrapType(DOWN_ACK, errorPayload(ex.getMessage())));
            return;
        }

        // ACK 直接回本连接：发送方必然连在本实例上，绕经 Redis 只会多一次往返
        sendToSession(session, wrapType(DOWN_ACK, msgVO));

        // 下行帧只序列化一次，本地推送与跨实例广播共用同一份字符串
        String frame = toJson(wrapType(DOWN_PRIVATE, chatService.toReceiverView(msgVO)));
        // 先本地、后广播：Redis 不可用时，与本发送方同实例的接收方依然能实时收到
        registry.pushFrameToUser(receiverId, frame);
        relay.publishPrivate(receiverId, frame);
    }

    /** 群聊：落库 → 回 ACK → 广播给除发送者外的全部群成员（多端逐端推送） */
    private void handleGroupMessage(WebSocketSession session, String senderId, String groupId,
                                    String content, String msgType) {
        if (groupId == null || groupId.isEmpty()) {
            return;
        }
        ChatMsgVO msgVO;
        try {
            msgVO = chatService.saveAndPushGroupMessage(senderId, groupId, content, msgType);
        } catch (Exception ex) {
            log.warn("群消息保存失败 sender={} group={}: {}", senderId, groupId, ex.getMessage());
            sendToSession(session, wrapType(DOWN_ACK, errorPayload(ex.getMessage())));
            return;
        }

        sendToSession(session, wrapType(DOWN_ACK, msgVO));

        List<String> memberIds = chatGroupService.listMemberIds(groupId);
        if (memberIds.isEmpty()) {
            return;
        }
        // 发送方自己已经通过 ACK 拿到消息，不再重复推送 —— 本地推送与广播都要排除他
        String frame = toJson(wrapType(DOWN_GROUP, chatService.toReceiverView(msgVO)));
        registry.pushFrameToUsers(memberIds, senderId, frame);
        relay.publishGroup(memberIds, senderId, frame);
    }

    /** 失败回执：前端据此撤销乐观气泡并提示 */
    private Map<String, Object> errorPayload(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", false);
        map.put("message", message == null ? "消息发送失败" : message);
        return map;
    }

    /**
     * 从会话属性中获取用户ID
     * @param session WebSocket会话
     * @return 用户ID（不存在返回 null）
     */
    private String getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId == null ? null : userId.toString();
    }

    /**
     * 向指定会话发送消息（序列化后交给注册表统一发送）
     * @param session WebSocket会话
     * @param payload 消息负载
     */
    private void sendToSession(WebSocketSession session, Object payload) {
        registry.sendFrame(session, toJson(payload));
    }

    /**
     * 序列化载荷。
     * @return JSON 字符串；序列化失败返回 null（注册表的发送方法会把 null 当「不发送」处理）
     */
    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            log.error("WebSocket 消息序列化失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 包装消息：先放 type 字段，对象类型将字段展开到同层级，非对象类型放入 data 字段
     * @param type 消息类型
     * @param data 消息数据
     * @return 包装后的消息负载
     */
    private Map<String, Object> wrapType(String type, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        if (data == null) {
            return result;
        }
        if (data instanceof Map) {
            result.putAll((Map<String, Object>) data);
        } else if (data instanceof CharSequence || data instanceof Number || data instanceof Boolean) {
            result.put("data", data);
        } else {
            // 对象：序列化为 Map 后展开字段
            Map<String, Object> fields = objectMapper.convertValue(data, Map.class);
            result.putAll(fields);
        }
        return result;
    }
}
