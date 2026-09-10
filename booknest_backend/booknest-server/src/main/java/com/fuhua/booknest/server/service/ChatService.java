package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.ChatConversationVO;
import com.fuhua.booknest.pojo.vo.ChatMsgVO;

import java.util.List;

public interface ChatService {

    /**
     * 查询当前用户的会话列表
     * @param currentUserId 当前用户ID
     * @return 会话列表
     */
    List<ChatConversationVO> getConversations(String currentUserId);

    /**
     * 获取或创建与目标用户的会话ID
     * @param currentUserId 当前用户ID
     * @param targetUserId 目标用户ID
     * @return 会话ID
     */
    String getOrCreateConversationId(String currentUserId, String targetUserId);

    /**
     * 分页查询会话消息（返回正序）
     * @param conversationId 会话ID
     * @param currentUserId 当前用户ID
     * @param page 页码（从1开始）
     * @param pageSize 每页条数
     * @return 消息列表
     */
    List<ChatMsgVO> getMessages(String conversationId, String currentUserId, int page, int pageSize);

    /**
     * 标记会话内消息已读
     * @param conversationId 会话ID
     * @param currentUserId 当前用户ID
     */
    void markAsRead(String conversationId, String currentUserId);

    /**
     * 查询用户未读消息总数
     * @param userId 用户ID
     * @return 未读消息数
     */
    int getUnreadCount(String userId);

    /**
     * 保存消息并推送给接收方
     * @param senderId 发送者用户ID
     * @param receiverId 接收者用户ID
     * @param content 消息内容
     * @return 发送方视角的消息 VO
     */
    ChatMsgVO saveAndPushMessage(String senderId, String receiverId, String content);
}
