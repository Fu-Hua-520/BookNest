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
     * 分页查询渠道消息（返回正序）
     *
     * channelId 语义：私聊传会话 ID，群聊传群 ID。两者都以 chat_message.conversation_id
     * 落库，所以查询 SQL 完全共用；归属校验按渠道类型分别走会话成员 / 群成员。
     *
     * @param channelId 渠道 ID（会话 ID 或群 ID）
     * @param currentUserId 当前用户ID
     * @param page 页码（从1开始）
     * @param pageSize 每页条数
     * @return 消息列表
     */
    List<ChatMsgVO> getMessages(String channelId, String currentUserId, int page, int pageSize);

    /**
     * 标记会话内消息已读（仅私聊；群聊已读走群成员表的已读位点）
     * @param conversationId 会话ID
     * @param currentUserId 当前用户ID
     */
    void markAsRead(String conversationId, String currentUserId);

    /**
     * 查询用户未读消息总数（私聊未读 + 所有群未读）
     * @param userId 用户ID
     * @return 未读消息数
     */
    int getUnreadCount(String userId);

    /**
     * 保存私信消息并返回发送方视角的消息 VO
     * @param senderId 发送者用户ID
     * @param receiverId 接收者用户ID
     * @param content 消息内容（文本，或图片 URL）
     * @param msgType 消息类型 TEXT / IMAGE
     * @return 发送方视角的消息 VO
     */
    ChatMsgVO saveAndPushMessage(String senderId, String receiverId, String content, String msgType);

    /**
     * 保存群消息并返回发送方视角的消息 VO
     * @param senderId 发送者用户ID
     * @param groupId 群 ID
     * @param content 消息内容（文本，或图片 URL）
     * @param msgType 消息类型 TEXT / IMAGE
     * @return 发送方视角的消息 VO
     */
    ChatMsgVO saveAndPushGroupMessage(String senderId, String groupId, String content, String msgType);

    /**
     * 按接收方视角复制一份消息 VO（isMine 置为 false）
     * @param vo 发送方视角的消息 VO
     * @return 接收方视角的消息 VO
     */
    ChatMsgVO toReceiverView(ChatMsgVO vo);
}
