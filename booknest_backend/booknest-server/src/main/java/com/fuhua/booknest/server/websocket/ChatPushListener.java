package com.fuhua.booknest.server.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天帧跨实例广播的订阅侧。
 *
 * <p>收到广播后只做一件事：把帧 JSON <b>原样</b>推给本节点持有的对应连接。
 * 不做反序列化重建 —— 帧在发送方就已经序列化好，跨实例透传能保证各实例
 * 推出去的内容逐字节一致（避免 {@code LocalDateTime}、数字精度这类在往返中被改写的风险）。</p>
 *
 * <p>本节点自己发出的广播会被 {@code from} 字段识别出来并跳过：发送路径上已经做过本地推送，
 * 跳过它才不会重复推。</p>
 */
@Component
@Slf4j
public class ChatPushListener implements MessageListener {

    private final WsSessionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatPushListener(WsSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // Redis 监听回调里抛异常会让该订阅后续的回调一起失效，所以整段兜住
        try {
            handle(new String(message.getBody(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.warn("聊天广播消费失败: {}", ex.getMessage());
        }
    }

    private void handle(String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);

        // 自己发的广播：发送路径已做过本地推送，跳过
        if (registry.getNodeId().equals(node.path(ChatPushRelay.FIELD_FROM).asText(""))) {
            return;
        }

        String frame = node.path(ChatPushRelay.FIELD_FRAME).asText(null);
        if (frame == null || frame.isEmpty()) {
            return;
        }

        String kind = node.path(ChatPushRelay.FIELD_KIND).asText("");
        if (ChatPushRelay.KIND_PRIVATE.equals(kind)) {
            registry.pushFrameToUser(node.path(ChatPushRelay.FIELD_TARGET).asText(null), frame);
            return;
        }
        if (ChatPushRelay.KIND_GROUP.equals(kind)) {
            registry.pushFrameToUsers(
                    readIds(node.path(ChatPushRelay.FIELD_TARGETS)),
                    node.path(ChatPushRelay.FIELD_EXCLUDE).asText(null),
                    frame);
            return;
        }
        log.warn("忽略未知的聊天广播类型: {}", kind);
    }

    /** 把广播信封里的 targets 数组读成列表（非数组 / 空数组都得到空列表） */
    private List<String> readIds(JsonNode arrayNode) {
        List<String> ids = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                String id = item.asText(null);
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
