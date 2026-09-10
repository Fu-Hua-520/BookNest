package com.fuhua.booknest.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fuhua.booknest.pojo.vo.ChatMsgVO;
import com.fuhua.booknest.server.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 私信 WebSocket 处理器
 * 负责私信消息的实时收发推送
 */
@Component
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /**
     * 在线用户会话映射：userId -> WebSocketSession
     */
    public static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    /**
     * 构造器：注入私信服务并初始化 JSON 序列化器（支持 LocalDateTime）
     */
    public ChatWebSocketHandler(ChatService chatService) {
        this.chatService = chatService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 建立连接后，将用户会话加入在线映射
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserId(session);
        if (userId != null) {
            SESSIONS.put(userId, session);
            log.info("用户 {} 建立 WebSocket 连接", userId);
        }
    }

    /**
     * 连接关闭后，将用户会话从在线映射移除
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserId(session);
        if (userId != null) {
            SESSIONS.remove(userId);
            log.info("用户 {} 断开 WebSocket 连接", userId);
        }
    }

    /**
     * 处理客户端文本消息
     * 消息格式：{"type":"SEND","receiverId":"xxx","content":"xxx"}
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String senderId = getUserId(session);
        if (senderId == null) {
            return;
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            log.warn("私信消息解析失败: {}", ex.getMessage());
            return;
        }
        if (node == null || !"SEND".equals(node.path("type").asText())) {
            return;
        }
        String receiverId = node.path("receiverId").asText(null);
        String content = node.path("content").asText(null);
        // 接收者或内容为空时忽略
        if (receiverId == null || receiverId.isEmpty() || content == null || content.isEmpty()) {
            return;
        }

        ChatMsgVO msgVO = chatService.saveAndPushMessage(senderId, receiverId, content);

        // 发送 ACK 回执给发送方
        sendToSession(session, wrapType("ACK", msgVO));

        // 接收方在线则推送消息（isMine 视角切换为 false）
        WebSocketSession receiverSession = SESSIONS.get(receiverId);
        if (receiverSession != null && receiverSession.isOpen()) {
            ChatMsgVO receiverVO = ChatMsgVO.builder()
                    .id(msgVO.getId())
                    .conversationId(msgVO.getConversationId())
                    .senderId(msgVO.getSenderId())
                    .senderName(msgVO.getSenderName())
                    .senderAvatar(msgVO.getSenderAvatar())
                    .receiverId(msgVO.getReceiverId())
                    .content(msgVO.getContent())
                    .isRead(msgVO.getIsRead())
                    .createTime(msgVO.getCreateTime())
                    .isMine(false)
                    .build();
            sendToSession(receiverSession, wrapType("MESSAGE", receiverVO));
        }
    }

    /**
     * 判断用户是否在线
     * @param userId 用户ID
     * @return 是否在线
     */
    public static boolean isUserOnline(String userId) {
        if (userId == null) {
            return false;
        }
        WebSocketSession session = SESSIONS.get(userId);
        return session != null && session.isOpen();
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
     * 向指定会话发送消息
     * @param session WebSocket会话
     * @param payload 消息负载
     */
    private void sendToSession(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));
        } catch (Exception ex) {
            log.error("WebSocket 消息发送失败: {}", ex.getMessage());
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
