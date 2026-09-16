package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.ChatConstant;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.ChatConversation;
import com.fuhua.booknest.pojo.entity.ChatGroup;
import com.fuhua.booknest.pojo.entity.ChatMsg;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.ChatConversationVO;
import com.fuhua.booknest.pojo.vo.ChatMsgVO;
import com.fuhua.booknest.server.mapper.ChatGroupMapper;
import com.fuhua.booknest.server.mapper.ChatMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 私信 / 群聊实现
 *
 * <p>列表类接口一律「先批量取容器，再批量取里面的人」：会话列表和历史消息都要展示对方的
 * 昵称与头像，逐条查用户是这个模块原来最大的 N+1 来源（50 个会话 = 50 次查询，
 * 一页 50 条消息 = 50 次查询）。这里统一收敛成 {@link #loadUsers(List)} 一次 IN 查询。</p>
 */
@Service
@Slf4j
public class ChatServiceImpl implements ChatService {

    /** 会话预览长度上限 */
    private static final int PREVIEW_MAX_LENGTH = 50;

    @Autowired
    private ChatMapper chatMapper;
    @Autowired
    private ChatGroupMapper chatGroupMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public List<ChatConversationVO> getConversations(String currentUserId) {
        List<ChatConversation> conversations = chatMapper.findUserConversations(currentUserId);
        List<ChatConversationVO> result = new ArrayList<>();
        if (conversations == null || conversations.isEmpty()) {
            return result;
        }

        // 对方的用户ID：会话中与自己不同的那一方
        List<String> peerIds = new ArrayList<>(conversations.size());
        for (ChatConversation conversation : conversations) {
            peerIds.add(peerIdOf(conversation, currentUserId));
        }
        // 一次 IN 查询取回全部对方资料（原来是循环里逐条 getUserById）
        Map<String, User> peers = loadUsers(peerIds);
        // 一次 group by 取回全部会话的未读数（原来是循环里逐条 countUnreadInConversation）
        Map<String, Integer> unreadByConversation = loadUnreadGroupByConversation(currentUserId);

        for (ChatConversation conversation : conversations) {
            String peerId = peerIdOf(conversation, currentUserId);
            User peer = peers.get(peerId);
            Integer unread = unreadByConversation.get(conversation.getId());
            result.add(ChatConversationVO.builder()
                    .id(conversation.getId())
                    .peerId(peerId)
                    .peerName(peer == null ? null : peer.getUsername())
                    .peerAvatar(peer == null ? null : peer.getAvatar())
                    .lastMessage(conversation.getLastMessage())
                    .lastMsgAt(conversation.getLastMsgAt())
                    .unreadCount(unread == null ? 0 : unread)
                    .build());
        }
        return result;
    }

    @Override
    public String getOrCreateConversationId(String currentUserId, String targetUserId) {
        // 目标用户校验：为空、为自己、或不存在均拒绝
        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new BaseException("目标用户不存在");
        }
        if (targetUserId.equals(currentUserId)) {
            throw new BaseException("不能给自己发私信");
        }
        if (userMapper.getUserById(targetUserId) == null) {
            throw new BaseException("目标用户不存在");
        }
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
    public List<ChatMsgVO> getMessages(String channelId, String currentUserId, int page, int pageSize) {
        // 分页参数兜底：页码最小为 1，每页条数限制在 [1, 100]
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 50;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }

        // 归属校验：先按私聊会话找，找不到再按群找。
        // 两条链路都必须校验「当前用户确实在这个渠道里」，否则任何登录用户只要拿到
        // 会话/群 ID 就能读走别人的聊天记录。
        ChatConversation conversation = chatMapper.findConversationById(channelId);
        String groupName = null;
        if (conversation != null) {
            boolean participant = currentUserId.equals(conversation.getUser1Id())
                    || currentUserId.equals(conversation.getUser2Id());
            if (!participant) {
                throw new BaseException("无权访问该会话");
            }
        } else {
            ChatGroup group = chatGroupMapper.findGroupById(channelId);
            if (group == null || chatGroupMapper.findMember(channelId, currentUserId) == null) {
                throw new BaseException("无权访问该会话");
            }
            groupName = group.getName();
        }

        int offset = (page - 1) * pageSize;
        List<ChatMsg> messages = chatMapper.findMessages(channelId, offset, pageSize);
        if (messages != null) {
            // SQL 按时间倒序取最近，反转为正序展示
            Collections.reverse(messages);
        }
        List<ChatMsgVO> result = new ArrayList<>();
        if (messages != null && !messages.isEmpty()) {
            // 这两个「只取一轮」消掉了整页的 N+1：
            //   · 群名只查一次（历史遗留，本来就在）
            //   · 发送者资料改成一次 IN 查询 —— 原来每条消息都要 getUserById，
            //     同一页里同一个人反复出现时更是重复查同一个用户
            List<String> senderIds = new ArrayList<>(messages.size());
            for (ChatMsg message : messages) {
                senderIds.add(message.getSenderId());
            }
            Map<String, User> senders = loadUsers(senderIds);
            for (ChatMsg message : messages) {
                result.add(buildMsgVO(message, currentUserId, groupName, senders.get(message.getSenderId())));
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
        // 私聊未读（chat_message.receiver_id + is_read）
        int privateUnread = chatMapper.countUnread(userId);
        // 群聊未读（群成员表的已读位点之后的消息）。群消息 receiver_id 为 NULL，
        // 因此天生不会被上面的私聊统计重复计入。
        int groupUnread = chatGroupMapper.countUnreadGroups(userId);
        return privateUnread + groupUnread;
    }

    @Override
    public ChatMsgVO saveAndPushMessage(String senderId, String receiverId, String content, String msgType) {
        // 消息内容长度上限加固
        if (content != null && content.length() > 2000) {
            throw new BaseException("消息内容过长");
        }
        String conversationId = getOrCreateConversationId(senderId, receiverId);
        ChatMsg message = ChatMsg.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(senderId)
                .receiverId(receiverId)
                // 私聊不落 group_id，渠道类型由 group_id 是否为空推导
                .groupId(null)
                .content(content)
                .msgType(ChatConstant.normalizeMsgType(msgType))
                .isRead(0)
                .createTime(LocalDateTime.now())
                .build();
        chatMapper.insertMessage(message);

        chatMapper.updateLastMessage(conversationId, buildPreview(message), message.getCreateTime());
        // 单条消息，发送者就是当前用户：直接单查一次即可，不必为一条记录走批量
        return buildMsgVO(message, senderId, null, userMapper.getUserById(senderId));
    }

    @Override
    public ChatMsgVO saveAndPushGroupMessage(String senderId, String groupId, String content, String msgType) {
        if (content != null && content.length() > 2000) {
            throw new BaseException("消息内容过长");
        }
        ChatGroup group = chatGroupMapper.findGroupById(groupId);
        if (group == null) {
            throw new BaseException("群聊不存在");
        }
        if (chatGroupMapper.findMember(groupId, senderId) == null) {
            throw new BaseException("你不在该群聊中");
        }

        ChatMsg message = ChatMsg.builder()
                .id(UUID.randomUUID().toString())
                // 群 ID 同时充当渠道 ID：历史消息查询与私聊共用一条 SQL
                .conversationId(groupId)
                .senderId(senderId)
                // 群消息没有单一接收者，receiver_id 留空
                .receiverId(null)
                .groupId(groupId)
                .content(content)
                .msgType(ChatConstant.normalizeMsgType(msgType))
                .isRead(0)
                .createTime(LocalDateTime.now())
                .build();
        chatMapper.insertMessage(message);

        chatGroupMapper.updateLastMessage(groupId, buildPreview(message), message.getCreateTime());
        return buildMsgVO(message, senderId, group.getName(), userMapper.getUserById(senderId));
    }

    @Override
    public ChatMsgVO toReceiverView(ChatMsgVO vo) {
        if (vo == null) {
            return null;
        }
        return ChatMsgVO.builder()
                .id(vo.getId())
                .conversationId(vo.getConversationId())
                .senderId(vo.getSenderId())
                .senderName(vo.getSenderName())
                .senderAvatar(vo.getSenderAvatar())
                .receiverId(vo.getReceiverId())
                .groupId(vo.getGroupId())
                .groupName(vo.getGroupName())
                .chatType(vo.getChatType())
                .msgType(vo.getMsgType())
                .content(vo.getContent())
                .isRead(vo.getIsRead())
                .createTime(vo.getCreateTime())
                .isMine(false)
                .build();
    }

    /**
     * 生成会话/群列表里的最后一条消息预览。
     * 图片消息不该把一长串 URL 顶到列表里，统一显示为「[图片]」。
     */
    private String buildPreview(ChatMsg message) {
        if (ChatConstant.MSG_IMAGE.equals(message.getMsgType())) {
            return "[图片]";
        }
        String content = message.getContent();
        if (content == null) {
            return null;
        }
        return content.length() > PREVIEW_MAX_LENGTH ? content.substring(0, PREVIEW_MAX_LENGTH) + "…" : content;
    }

    /**
     * 组装消息 VO
     * @param msg 消息实体
     * @param currentUserId 当前用户ID（用于判定 isMine）
     * @param groupName 群名称，私聊传 null
     * @param sender 发送者资料；由调用方批量预取后传入，本方法<b>不再自己查库</b>
     * @return 消息 VO
     */
    private ChatMsgVO buildMsgVO(ChatMsg msg, String currentUserId, String groupName, User sender) {
        boolean isGroup = msg.getGroupId() != null && !msg.getGroupId().isEmpty();
        return ChatMsgVO.builder()
                .id(msg.getId())
                .conversationId(msg.getConversationId())
                .senderId(msg.getSenderId())
                .senderName(sender == null ? null : sender.getUsername())
                .senderAvatar(sender == null ? null : sender.getAvatar())
                .receiverId(msg.getReceiverId())
                .groupId(msg.getGroupId())
                .groupName(groupName)
                .chatType(isGroup ? ChatConstant.TYPE_GROUP : ChatConstant.TYPE_PRIVATE)
                .msgType(msg.getMsgType() == null ? ChatConstant.MSG_TEXT : msg.getMsgType())
                .content(msg.getContent())
                .isRead(msg.getIsRead())
                .createTime(msg.getCreateTime())
                .isMine(currentUserId.equals(msg.getSenderId()))
                .build();
    }

    /** 会话里的「对方」：与自己不同的那一方 */
    private String peerIdOf(ChatConversation conversation, String currentUserId) {
        return currentUserId.equals(conversation.getUser1Id())
                ? conversation.getUser2Id() : conversation.getUser1Id();
    }

    /**
     * 批量取用户资料，返回「用户ID → 用户」。
     *
     * <p>去重、空集合兜底（{@code where id in ()} 是语法错误）、IN 查询与建索引
     * 统一交给 {@link UserMapper#mapByIds}，这里只保留一个语义化的名字，
     * 免得每个 Service 各抄一份边界处理、改的时候漏掉几处。</p>
     *
     * @param userIds 用户ID列表（可含重复、可为 null）
     * @return 用户ID到用户实体的映射；查不到的 ID 不会出现在 Map 里
     */
    private Map<String, User> loadUsers(List<String> userIds) {
        return userMapper.mapByIds(userIds);
    }

    /**
     * 一次 group by 取回「会话ID → 未读数」。
     * 没有未读的会话不会出现在结果里，调用方按 0 处理。
     */
    private Map<String, Integer> loadUnreadGroupByConversation(String userId) {
        Map<String, Integer> counts = new HashMap<>();
        List<Map<String, Object>> rows = chatMapper.countUnreadGroupByConversation(userId);
        if (rows == null) {
            return counts;
        }
        for (Map<String, Object> row : rows) {
            Object conversationId = row.get("conversationId");
            Object unreadCount = row.get("unreadCount");
            if (conversationId == null) {
                continue;
            }
            counts.put(String.valueOf(conversationId),
                    unreadCount == null ? 0 : ((Number) unreadCount).intValue());
        }
        return counts;
    }
}
