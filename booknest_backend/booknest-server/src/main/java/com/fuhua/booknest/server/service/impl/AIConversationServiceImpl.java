package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.AIConversation;
import com.fuhua.booknest.pojo.entity.AIMessage;
import com.fuhua.booknest.server.mapper.AIConversationMapper;
import com.fuhua.booknest.server.mapper.AIMessageMapper;
import com.fuhua.booknest.server.service.AIConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * AI 会话管理服务实现
 */
@Service
@Slf4j
public class AIConversationServiceImpl implements AIConversationService {

    /** 默认模型 */
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /** 会话正常状态 */
    private static final int STATUS_NORMAL = 1;

    /** 一轮问答增加的消息数（用户消息 + 助手消息） */
    private static final int REPLY_MESSAGE_DELTA = 2;

    @Autowired
    private AIConversationMapper aiConversationMapper;
    @Autowired
    private AIMessageMapper aiMessageMapper;

    /**
     * 新建会话
     * @param userId 用户ID
     * @return 会话ID
     */
    @Override
    public String createConversation(String userId) {
        LocalDateTime now = LocalDateTime.now();
        AIConversation conversation = AIConversation.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(null)
                .model(DEFAULT_MODEL)
                .messageCount(0)
                .status(STATUS_NORMAL)
                .createTime(now)
                .updateTime(now)
                .build();
        aiConversationMapper.insert(conversation);
        return conversation.getId();
    }

    /**
     * 查询用户会话列表
     * @param userId 用户ID
     * @return 会话列表
     */
    @Override
    public List<AIConversation> getUserConversations(String userId) {
        return aiConversationMapper.selectByUserId(userId);
    }

    /**
     * 查询会话消息列表（校验归属）
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 消息列表（正序）
     */
    @Override
    public List<AIMessage> getConversationMessages(String conversationId, String userId) {
        checkOwnership(conversationId, userId);
        return aiMessageMapper.selectByConversationId(conversationId);
    }

    /**
     * 更新会话标题
     * @param conversationId 会话ID
     * @param title 新标题
     */
    @Override
    public void updateConversationTitle(String conversationId, String title) {
        aiConversationMapper.update(AIConversation.builder()
                .id(conversationId)
                .title(title)
                .updateTime(LocalDateTime.now())
                .build());
    }

    /**
     * 删除会话（软删除）
     * @param conversationId 会话ID
     * @param userId 用户ID
     */
    @Override
    public void deleteConversation(String conversationId, String userId) {
        checkOwnership(conversationId, userId);
        aiConversationMapper.softDelete(conversationId);
    }

    /**
     * 新增消息
     * @param message 消息实体
     */
    @Override
    public void addMessage(AIMessage message) {
        aiMessageMapper.insert(message);
    }

    /**
     * 获取会话上下文消息（最近 limit 条，反转为正序）
     * @param conversationId 会话ID
     * @param limit 条数
     * @return 正序消息列表
     */
    @Override
    public List<AIMessage> getContextMessages(String conversationId, int limit) {
        List<AIMessage> messages = aiMessageMapper.getRecentMessages(conversationId, limit);
        if (messages != null && messages.size() > 1) {
            // Mapper 按创建时间倒序返回，反转后即为正序（对话时间线）
            Collections.reverse(messages);
        }
        return messages;
    }

    /**
     * 回复完成后更新会话元信息
     * @param conversationId 会话ID
     * @param title 建议标题（仅当会话标题为空时生效）
     */
    @Override
    public void updateConversationAfterReply(String conversationId, String title) {
        AIConversation current = aiConversationMapper.selectById(conversationId);
        if (current == null) {
            log.warn("更新会话元信息失败，会话不存在: {}", conversationId);
            return;
        }
        int messageCount = (current.getMessageCount() == null ? 0 : current.getMessageCount()) + REPLY_MESSAGE_DELTA;
        // 仅当标题为空（新会话）时才设置，避免覆盖用户自定义标题
        String newTitle = current.getTitle() == null ? title : current.getTitle();
        aiConversationMapper.update(AIConversation.builder()
                .id(conversationId)
                .title(newTitle)
                .messageCount(messageCount)
                .updateTime(LocalDateTime.now())
                .build());
    }

    /**
     * 校验会话归属，防止越权访问
     * @param conversationId 会话ID
     * @param userId 用户ID
     */
    private void checkOwnership(String conversationId, String userId) {
        AIConversation conversation = aiConversationMapper.selectById(conversationId);
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw new BaseException("无权访问该会话");
        }
    }
}
