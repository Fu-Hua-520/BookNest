package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.entity.AIConversation;
import com.fuhua.booknest.pojo.entity.AIMessage;

import java.util.List;

/**
 * AI 会话管理服务接口
 */
public interface AIConversationService {

    /**
     * 新建会话（UUID id、默认模型、初始状态），返回会话ID
     * @param userId 用户ID
     * @return 会话ID
     */
    String createConversation(String userId);

    /**
     * 查询用户会话列表（正常状态，按更新时间倒序）
     * @param userId 用户ID
     * @return 会话列表
     */
    List<AIConversation> getUserConversations(String userId);

    /**
     * 查询会话消息列表（校验归属，防止越权访问）
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @return 消息列表（正序）
     */
    List<AIMessage> getConversationMessages(String conversationId, String userId);

    /**
     * 更新会话标题（仅更新 title 与 update_time，先校验会话归属）
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @param title 新标题
     */
    void updateConversationTitle(String conversationId, String userId, String title);

    /**
     * 校验会话归属，防止越权访问
     * @param conversationId 会话ID
     * @param userId 用户ID
     */
    void checkOwnership(String conversationId, String userId);

    /**
     * 删除会话（校验归属后软删除 status=0）
     * @param conversationId 会话ID
     * @param userId 用户ID
     */
    void deleteConversation(String conversationId, String userId);

    /**
     * 新增消息
     * @param message 消息实体
     */
    void addMessage(AIMessage message);

    /**
     * 获取会话上下文消息（最近 limit 条，反转为正序）作为对话记忆
     * @param conversationId 会话ID
     * @param limit 条数
     * @return 正序消息列表
     */
    List<AIMessage> getContextMessages(String conversationId, int limit);

    /**
     * 回复完成后更新会话元信息：消息数 +2（一问一答），标题为空时取首条用户消息前 20 字
     * @param conversationId 会话ID
     * @param title 建议标题（仅当会话标题为空时生效）
     */
    void updateConversationAfterReply(String conversationId, String title);
}
