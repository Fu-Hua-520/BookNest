package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.ChatConversation;
import com.fuhua.booknest.pojo.entity.ChatMsg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChatMapper {

    /**
     * 插入私信会话
     * @param conversation 会话实体
     */
    void insertConversation(ChatConversation conversation);

    /**
     * 根据用户对（字典序 user1Id < user2Id）查询会话
     * @param user1Id 用户1ID
     * @param user2Id 用户2ID
     * @return 会话实体（不存在返回 null）
     */
    ChatConversation findConversationByUsers(@Param("user1Id") String user1Id, @Param("user2Id") String user2Id);

    /**
     * 根据会话ID查询会话
     * @param id 会话ID
     * @return 会话实体（不存在返回 null）
     */
    ChatConversation findConversationById(@Param("id") String id);

    /**
     * 查询用户参与的所有会话（按最后消息时间倒序）
     * @param userId 用户ID
     * @return 会话列表
     */
    List<ChatConversation> findUserConversations(@Param("userId") String userId);

    /**
     * 更新会话最后一条消息预览
     * @param id 会话ID
     * @param lastMessage 最后消息预览
     * @param lastMsgAt 最后消息时间
     */
    void updateLastMessage(@Param("id") String id, @Param("lastMessage") String lastMessage, @Param("lastMsgAt") LocalDateTime lastMsgAt);

    /**
     * 插入消息（私聊 / 群聊共用：群聊时 groupId 有值、receiverId 为 null）
     * @param message 消息实体
     */
    void insertMessage(ChatMsg message);

    /**
     * 解散群时清理该群全部消息
     * @param groupId 群 ID
     */
    void deleteByGroupId(@Param("groupId") String groupId);

    /**
     * 分页查询会话消息（按发送时间倒序，取最近 offset/limit 条）
     * @param conversationId 会话ID
     * @param offset 偏移量
     * @param limit 条数
     * @return 消息列表
     */
    List<ChatMsg> findMessages(@Param("conversationId") String conversationId, @Param("offset") int offset, @Param("limit") int limit);

    /**
     * 统计用户未读消息总数
     * @param receiverId 接收者用户ID
     * @return 未读消息数
     */
    int countUnread(@Param("receiverId") String receiverId);

    /**
     * 统计会话内未读消息数
     * @param conversationId 会话ID
     * @param receiverId 接收者用户ID
     * @return 未读消息数
     */
    int countUnreadInConversation(@Param("conversationId") String conversationId, @Param("receiverId") String receiverId);

    /**
     * 按会话分组统计未读数：<b>一次</b>查完全部会话，替代「每个会话各查一次」。
     *
     * <p>原实现是「循环里逐条 countUnreadInConversation」，会话列表有 50 个会话就是 50 次 count(*)。
     * 这里换成一条 {@code group by}，查询数从 N 降到 1。</p>
     *
     * @param receiverId 接收者用户ID
     * @return [{ conversationId, unreadCount }]；无未读的会话不会出现在结果里（调用方按 0 处理）
     */
    List<Map<String, Object>> countUnreadGroupByConversation(@Param("receiverId") String receiverId);

    /**
     * 标记会话内消息已读
     * @param conversationId 会话ID
     * @param receiverId 接收者用户ID
     */
    void markConversationAsRead(@Param("conversationId") String conversationId, @Param("receiverId") String receiverId);
}
