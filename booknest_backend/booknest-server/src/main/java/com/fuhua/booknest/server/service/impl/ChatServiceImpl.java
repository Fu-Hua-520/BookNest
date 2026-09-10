package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.ChatConversation;
import com.fuhua.booknest.pojo.entity.ChatMsg;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.ChatConversationVO;
import com.fuhua.booknest.pojo.vo.ChatMsgVO;
import com.fuhua.booknest.server.mapper.ChatMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    @Autowired
    private ChatMapper chatMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public List<ChatConversationVO> getConversations(String currentUserId) {
        List<ChatConversation> conversations = chatMapper.findUserConversations(currentUserId);
        List<ChatConversationVO> result = new ArrayList<>();
        if (conversations == null) {
            return result;
        }
        for (ChatConversation conversation : conversations) {
            // 对方用户ID：会话中与自己不同的那一方
            String peerId = currentUserId.equals(conversation.getUser1Id())
                    ? conversation.getUser2Id() : conversation.getUser1Id();
            User peer = userMapper.getUserById(peerId);
            int unread = chatMapper.countUnreadInConversation(conversation.getId(), currentUserId);
            result.add(ChatConversationVO.builder()
                    .id(conversation.getId())
                    .peerId(peerId)
                    .peerName(peer == null ? null : peer.getUsername())
                    .peerAvatar(peer == null ? null : peer.getAvatar())
                    .lastMessage(conversation.getLastMessage())
                    .lastMsgAt(conversation.getLastMsgAt())
                    .unreadCount(unread)
                    .build());
        }
        return result;
    }

    @Override
    public String getOrCreateConversationId(String currentUserId, String targetUserId) {
        // 字典序确定 user1 为较小者，user2 为较大者，保证用户对唯一
        String user1Id;
        String user2Id;
        if (currentUserId.compareTo(targetUserId) <= 0) {
            user1Id = currentUserId;
            user2Id = targetUserId;
        } else {
            user1Id = targetUserId;
            user2Id = currentUserId;
        }
        ChatConversation existing = chatMapper.findConversationByUsers(user1Id, user2Id);
        if (existing != null) {
            return existing.getId();
        }
        ChatConversation conversation = ChatConversation.builder()
                .id(UUID.randomUUID().toString())
                .user1Id(user1Id)
                .user2Id(user2Id)
                .createTime(LocalDateTime.now())
                .build();
        chatMapper.insertConversation(conversation);
        return conversation.getId();
    }

    @Override
    public List<ChatMsgVO> getMessages(String conversationId, String currentUserId, int page, int pageSize) {
        // 校验会话归属，防止越权访问
        ChatConversation conversation = chatMapper.findConversationById(conversationId);
        if (conversation == null
                || !(currentUserId.equals(conversation.getUser1Id()) || currentUserId.equals(conversation.getUser2Id()))) {
            throw new BaseException("无权访问该会话");
        }
        int offset = (page - 1) * pageSize;
        List<ChatMsg> messages = chatMapper.findMessages(conversationId, offset, pageSize);
        if (messages != null) {
            // SQL 按时间倒序取最近，反转为正序展示
            Collections.reverse(messages);
        }
        List<ChatMsgVO> result = new ArrayList<>();
        if (messages != null) {
            for (ChatMsg message : messages) {
                result.add(buildMsgVO(message, currentUserId));
            }
        }
        return result;
    }

    @Override
    public void markAsRead(String conversationId, String currentUserId) {
        chatMapper.markConversationAsRead(conversationId, currentUserId);
    }

    @Override
    public int getUnreadCount(String userId) {
        return chatMapper.countUnread(userId);
    }

    @Override
    public ChatMsgVO saveAndPushMessage(String senderId, String receiverId, String content) {
        String conversationId = getOrCreateConversationId(senderId, receiverId);
        ChatMsg message = ChatMsg.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(senderId)
                .receiverId(receiverId)
                .content(content)
                .isRead(0)
                .createTime(LocalDateTime.now())
                .build();
        chatMapper.insertMessage(message);

        // 更新会话最后一条消息预览（超过 50 字截断加省略号）
        String preview = content == null ? null : (content.length() > 50 ? content.substring(0, 50) + "…" : content);
        chatMapper.updateLastMessage(conversationId, preview, message.getCreateTime());

        return buildMsgVO(message, senderId);
    }

    /**
     * 组装消息 VO
     * @param msg 消息实体
     * @param currentUserId 当前用户ID
     * @return 消息 VO
     */
    private ChatMsgVO buildMsgVO(ChatMsg msg, String currentUserId) {
        User sender = userMapper.getUserById(msg.getSenderId());
        return ChatMsgVO.builder()
                .id(msg.getId())
                .conversationId(msg.getConversationId())
                .senderId(msg.getSenderId())
                .senderName(sender == null ? null : sender.getUsername())
                .senderAvatar(sender == null ? null : sender.getAvatar())
                .receiverId(msg.getReceiverId())
                .content(msg.getContent())
                .isRead(msg.getIsRead())
                .createTime(msg.getCreateTime())
                .isMine(currentUserId.equals(msg.getSenderId()))
                .build();
    }
}
