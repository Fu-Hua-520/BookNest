package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.ChatConstant;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.dto.ChatGroupCreateDTO;
import com.fuhua.booknest.pojo.dto.ChatGroupUpdateDTO;
import com.fuhua.booknest.pojo.entity.ChatGroup;
import com.fuhua.booknest.pojo.entity.ChatGroupInvitation;
import com.fuhua.booknest.pojo.entity.ChatGroupMember;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.ChatGroupVO;
import com.fuhua.booknest.pojo.vo.GroupInvitationVO;
import com.fuhua.booknest.pojo.vo.GroupMemberVO;
import com.fuhua.booknest.server.mapper.ChatGroupInvitationMapper;
import com.fuhua.booknest.server.mapper.ChatGroupMapper;
import com.fuhua.booknest.server.mapper.ChatMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.ChatGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * 群聊服务
 *
 * 入群语义（重要，勿改回「直接加人」）：
 *   邀请他人  → 写 INVITE 待确认记录，被邀请人同意后才入群
 *   主动加入  → 写 JOIN_REQUEST 待审批记录，群主通过后才入群
 * 只有建群时的群主是天然成员，其余任何人入群都必须经过一次明确的确认动作。
 */
@Service
@Slf4j
public class ChatGroupServiceImpl implements ChatGroupService {

    /** 单群人数上限（含群主） */
    private static final int MAX_GROUP_MEMBERS = 200;

    /** 申请理由 / 邀请附言长度上限 */
    private static final int MESSAGE_MAX_LENGTH = 200;

    @Autowired
    private ChatGroupMapper chatGroupMapper;
    @Autowired
    private ChatGroupInvitationMapper chatGroupInvitationMapper;
    @Autowired
    private ChatMapper chatMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public ChatGroupVO createGroup(String ownerId, ChatGroupCreateDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new BaseException("群名称不能为空");
        }
        if (dto.getName().trim().length() > 30) {
            throw new BaseException("群名称不能超过 30 个字符");
        }
        // 去重 + 剔除自己：前端多选时可能把创建者一起带上
        List<String> inviteeIds = normalizeMemberIds(dto.getMemberIds(), ownerId);
        // 允许先建空群：既然进群要对方同意，就不该强制「必须先选人」。
        // 人数上限在这里不需要校验 —— 建群时只有群主一人是真实成员，
        // 被勾选的书友只是收到邀请，真正入群时会走 addMemberIfAbsent 再校验一次。
        for (String inviteeId : inviteeIds) {
            if (userMapper.getUserById(inviteeId) == null) {
                throw new BaseException("有书友不存在，请重新选择");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ChatGroup group = ChatGroup.builder()
                .id(UUID.randomUUID().toString())
                .name(dto.getName().trim())
                .avatar(dto.getAvatar())
                .ownerId(ownerId)
                .notice(dto.getNotice())
                .createTime(now)
                .build();
        chatGroupMapper.insertGroup(group);

        // 建群时只有群主直接入群；被勾选的书友收到的是「邀请」而不是「已被拉进来」
        chatGroupMapper.insertMember(ChatGroupMember.builder()
                .id(UUID.randomUUID().toString())
                .groupId(group.getId())
                .userId(ownerId)
                .role(ChatConstant.ROLE_OWNER)
                // 群主 last_read_at 直接置为建群时间，避免刚建群就出现未读
                .lastReadAt(now)
                .joinTime(now)
                .build());

        for (String inviteeId : inviteeIds) {
            insertInvitation(group.getId(), ownerId, inviteeId, ChatConstant.INVITE_TYPE_INVITE, null, now);
        }

        ChatGroupVO vo = buildGroupVO(group, ownerId, true);
        log.info("群聊已创建 groupId={}, ownerId={}, 发出邀请 {} 条",
                group.getId(), ownerId, inviteeIds.size());
        return vo;
    }

    @Override
    public List<ChatGroupVO> listMyGroups(String userId) {
        List<ChatGroup> groups = chatGroupMapper.listMyGroups(userId);
        List<ChatGroupVO> result = new ArrayList<>();
        if (groups == null) {
            return result;
        }
        for (ChatGroup group : groups) {
            result.add(buildGroupVO(group, userId, false));
        }
        return result;
    }

    @Override
    public ChatGroupVO getGroupDetail(String groupId, String userId) {
        ChatGroup group = requireGroup(groupId);
        requireMember(groupId, userId);
        return buildGroupVO(group, userId, true);
    }

    @Override
    public List<GroupMemberVO> listMembers(String groupId, String operatorId) {
        requireGroup(groupId);
        requireMember(groupId, operatorId);
        List<GroupMemberVO> members = chatGroupMapper.listMembers(groupId);
        return members == null ? new ArrayList<>() : members;
    }

    @Override
    @Transactional
    public ChatGroupVO inviteMembers(String groupId, String operatorId, List<String> userIds) {
        ChatGroup group = requireGroup(groupId);
        requireMember(groupId, operatorId);

        int currentCount = chatGroupMapper.countMembers(groupId);
        if (currentCount >= MAX_GROUP_MEMBERS) {
            throw new BaseException("群成员已达上限 " + MAX_GROUP_MEMBERS + " 人");
        }

        List<String> targets = normalizeMemberIds(userIds, operatorId);
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        for (String targetId : targets) {
            // 已在群里、或已经有一条待确认邀请，都直接跳过（多端重复点击是常态）
            if (chatGroupMapper.findMember(groupId, targetId) != null) {
                continue;
            }
            if (chatGroupInvitationMapper.findOne(groupId, targetId,
                    ChatConstant.INVITE_TYPE_INVITE, ChatConstant.INVITE_PENDING) != null) {
                continue;
            }
            if (userMapper.getUserById(targetId) == null) {
                continue;
            }
            insertInvitation(groupId, operatorId, targetId, ChatConstant.INVITE_TYPE_INVITE, null, now);
            sent += 1;
        }
        log.info("已发出入群邀请 {} 条 groupId={} operatorId={}", sent, groupId, operatorId);
        // 成员数此刻并未变化 —— 邀请只是「待确认」，真正入群发生在对方同意时
        return buildGroupVO(group, operatorId, true);
    }

    @Override
    @Transactional
    public void removeMember(String groupId, String operatorId, String targetUserId) {
        ChatGroup group = requireGroup(groupId);
        ChatGroupMember operator = requireMember(groupId, operatorId);

        if (targetUserId == null || targetUserId.isEmpty()) {
            throw new BaseException("请选择要移除的成员");
        }

        boolean isSelf = operatorId.equals(targetUserId);
        if (isSelf) {
            // 群主退群会让群失去归属，必须先解散；避免出现「无人群」
            if (ChatConstant.ROLE_OWNER.equals(operator.getRole())) {
                throw new BaseException("群主不能退出群聊，请先解散该群");
            }
        } else {
            // 移除他人只有群主可以操作
            if (!ChatConstant.ROLE_OWNER.equals(operator.getRole())) {
                throw new BaseException("只有群主可以移除成员");
            }
            if (targetUserId.equals(group.getOwnerId())) {
                throw new BaseException("不能移除群主");
            }
        }

        chatGroupMapper.deleteMember(groupId, targetUserId);
    }

    @Override
    public ChatGroupVO updateGroup(String groupId, String operatorId, ChatGroupUpdateDTO dto) {
        ChatGroup group = requireGroup(groupId);
        ChatGroupMember operator = requireMember(groupId, operatorId);
        if (!ChatConstant.ROLE_OWNER.equals(operator.getRole())) {
            throw new BaseException("只有群主可以修改群资料");
        }
        if (dto == null) {
            return buildGroupVO(group, operatorId, true);
        }
        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            group.setName(dto.getName().trim());
        }
        group.setNotice(dto.getNotice());
        group.setAvatar(dto.getAvatar());
        chatGroupMapper.updateGroup(group);
        return buildGroupVO(group, operatorId, true);
    }

    @Override
    @Transactional
    public void dissolveGroup(String groupId, String operatorId) {
        ChatGroup group = requireGroup(groupId);
        if (!group.getOwnerId().equals(operatorId)) {
            throw new BaseException("只有群主可以解散群聊");
        }
        // 先清消息再清成员关系与邀请记录，最后删群；顺序反过来会留下无法归属的孤儿数据
        chatMapper.deleteByGroupId(groupId);
        chatGroupMapper.deleteMembersByGroupId(groupId);
        chatGroupInvitationMapper.deleteByGroupId(groupId);
        chatGroupMapper.deleteGroup(groupId);
        log.info("群聊已解散 groupId={}, ownerId={}", groupId, operatorId);
    }

    @Override
    public void markGroupRead(String groupId, String userId) {
        if (chatGroupMapper.findMember(groupId, userId) == null) {
            throw new BaseException("你不在该群聊中");
        }
        chatGroupMapper.updateLastReadAt(groupId, userId, LocalDateTime.now());
    }

    @Override
    public List<String> listMemberIds(String groupId) {
        List<String> ids = chatGroupMapper.listMemberIds(groupId);
        return ids == null ? new ArrayList<>() : ids;
    }

    /* ==================== 邀请 / 加入申请 ==================== */

    @Override
    public List<GroupInvitationVO> listMyInvitations(String userId) {
        List<GroupInvitationVO> list = chatGroupInvitationMapper.listMyInvitations(userId);
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    @Transactional
    public String handleInvitation(String invitationId, String userId, boolean accept) {
        ChatGroupInvitation invitation = requireInvitation(invitationId);
        if (!ChatConstant.INVITE_TYPE_INVITE.equals(invitation.getType())) {
            throw new BaseException("该记录不是入群邀请");
        }
        if (!ChatConstant.INVITE_PENDING.equals(invitation.getStatus())) {
            throw new BaseException("该邀请已处理过");
        }
        if (!userId.equals(invitation.getInviteeId())) {
            throw new BaseException("只有被邀请人本人可以处理该邀请");
        }

        chatGroupInvitationMapper.updateStatus(invitationId,
                accept ? ChatConstant.INVITE_ACCEPTED : ChatConstant.INVITE_REJECTED,
                LocalDateTime.now());

        if (!accept) {
            log.info("入群邀请被拒绝 invitationId={} userId={}", invitationId, userId);
            return null;
        }
        addMemberIfAbsent(invitation.getGroupId(), userId);
        log.info("入群邀请已同意 invitationId={} userId={} groupId={}",
                invitationId, userId, invitation.getGroupId());
        return invitation.getGroupId();
    }

    @Override
    @Transactional
    public void applyJoin(String groupId, String userId, String message) {
        requireGroup(groupId);
        if (chatGroupMapper.findMember(groupId, userId) != null) {
            throw new BaseException("你已经是该群成员了");
        }
        if (chatGroupMapper.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            throw new BaseException("群成员已达上限 " + MAX_GROUP_MEMBERS + " 人");
        }
        if (chatGroupInvitationMapper.findOne(groupId, userId,
                ChatConstant.INVITE_TYPE_JOIN, ChatConstant.INVITE_PENDING) != null) {
            throw new BaseException("你已经申请过了，请等待群主审批");
        }
        insertInvitation(groupId, null, userId, ChatConstant.INVITE_TYPE_JOIN,
                normalizeMessage(message), LocalDateTime.now());
        log.info("收到入群申请 groupId={} userId={}", groupId, userId);
    }

    @Override
    public List<String> listMyPendingJoinGroupIds(String userId) {
        List<String> ids = chatGroupInvitationMapper.listPendingJoinGroupIds(userId);
        return ids == null ? new ArrayList<>() : ids;
    }

    @Override
    public int countPendingJoinRequests(String userId) {
        if (userId == null || userId.isEmpty()) {
            return 0;
        }
        return chatGroupInvitationMapper.countPendingJoinsForOwner(userId);
    }

    @Override
    public List<GroupInvitationVO> listJoinRequests(String groupId, String operatorId) {
        ChatGroup group = requireGroup(groupId);
        if (!operatorId.equals(group.getOwnerId())) {
            throw new BaseException("只有群主可以查看加入申请");
        }
        List<GroupInvitationVO> list = chatGroupInvitationMapper.listJoinRequests(groupId);
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    @Transactional
    public String approveJoin(String invitationId, String operatorId, boolean approve) {
        ChatGroupInvitation invitation = requireInvitation(invitationId);
        if (!ChatConstant.INVITE_TYPE_JOIN.equals(invitation.getType())) {
            throw new BaseException("该记录不是入群申请");
        }
        if (!ChatConstant.INVITE_PENDING.equals(invitation.getStatus())) {
            throw new BaseException("该申请已处理过");
        }
        ChatGroup group = requireGroup(invitation.getGroupId());
        if (!operatorId.equals(group.getOwnerId())) {
            throw new BaseException("只有群主可以审批加入申请");
        }

        chatGroupInvitationMapper.updateStatus(invitationId,
                approve ? ChatConstant.INVITE_ACCEPTED : ChatConstant.INVITE_REJECTED,
                LocalDateTime.now());

        if (!approve) {
            log.info("入群申请被拒绝 invitationId={} operatorId={}", invitationId, operatorId);
            return null;
        }
        addMemberIfAbsent(group.getId(), invitation.getInviteeId());
        log.info("入群申请已通过 invitationId={} userId={} groupId={}",
                invitationId, invitation.getInviteeId(), group.getId());
        return group.getId();
    }

    @Override
    public List<ChatGroupVO> searchGroups(String keyword, String userId) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<ChatGroup> groups = chatGroupMapper.searchByName(keyword.trim(), userId);
        List<ChatGroupVO> result = new ArrayList<>();
        if (groups == null) {
            return result;
        }
        for (ChatGroup group : groups) {
            result.add(buildGroupVO(group, userId, false));
        }
        return result;
    }

    /* ==================== 内部辅助方法 ==================== */

    /**
     * 让用户真正成为群成员。
     * 只有「接受邀请」和「通过申请」两条路径会走到这里，是入群的唯一收口。
     */
    private void addMemberIfAbsent(String groupId, String userId) {
        requireGroup(groupId);
        if (chatGroupMapper.findMember(groupId, userId) != null) {
            // 幂等：重复点击「同意」不应该报错，也不该插入第二条成员关系
            return;
        }
        if (chatGroupMapper.countMembers(groupId) >= MAX_GROUP_MEMBERS) {
            throw new BaseException("群成员已达上限 " + MAX_GROUP_MEMBERS + " 人，暂时无法加入");
        }
        LocalDateTime now = LocalDateTime.now();
        chatGroupMapper.insertMember(ChatGroupMember.builder()
                .id(UUID.randomUUID().toString())
                .groupId(groupId)
                .userId(userId)
                .role(ChatConstant.ROLE_MEMBER)
                // 已读位点设为入群时刻，入群前的历史消息不计入未读
                .lastReadAt(now)
                .joinTime(now)
                .build());
    }

    private void insertInvitation(String groupId, String inviterId, String inviteeId,
                                  String type, String message, LocalDateTime now) {
        chatGroupInvitationMapper.insert(ChatGroupInvitation.builder()
                .id(UUID.randomUUID().toString())
                .groupId(groupId)
                .inviterId(inviterId)
                .inviteeId(inviteeId)
                .type(type)
                .status(ChatConstant.INVITE_PENDING)
                .message(message)
                .createTime(now)
                .build());
    }

    private ChatGroupInvitation requireInvitation(String invitationId) {
        if (invitationId == null || invitationId.isEmpty()) {
            throw new BaseException("邀请不存在");
        }
        ChatGroupInvitation invitation = chatGroupInvitationMapper.findById(invitationId);
        if (invitation == null) {
            throw new BaseException("邀请不存在或已被撤回");
        }
        return invitation;
    }

    /**
     * 组装群 VO（成员数与未读数按需查询）
     * @param group 群实体
     * @param currentUserId 当前用户ID
     * @param withMembers 是否附带成员列表（详情/建群返回，列表接口不返回以省一次查询）
     */
    private ChatGroupVO buildGroupVO(ChatGroup group, String currentUserId, boolean withMembers) {
        User owner = group.getOwnerId() == null ? null : userMapper.getUserById(group.getOwnerId());
        List<GroupMemberVO> members = withMembers ? chatGroupMapper.listMembers(group.getId()) : null;
        boolean isOwner = group.getOwnerId() != null && group.getOwnerId().equals(currentUserId);
        return ChatGroupVO.builder()
                .id(group.getId())
                .name(group.getName())
                .avatar(group.getAvatar())
                .ownerId(group.getOwnerId())
                .ownerName(owner == null ? null : owner.getUsername())
                .notice(group.getNotice())
                .memberCount(chatGroupMapper.countMembers(group.getId()))
                .lastMessage(group.getLastMessage())
                .lastMsgAt(group.getLastMsgAt())
                .createTime(group.getCreateTime())
                .unreadCount(chatGroupMapper.countUnreadInGroup(group.getId(), currentUserId))
                .isOwner(isOwner)
                .members(members)
                // 仅群主需要知道有多少人申请加入，其他人恒为 0
                .pendingJoinCount(isOwner ? chatGroupInvitationMapper.countPendingJoins(group.getId()) : 0)
                .build();
    }

    private ChatGroup requireGroup(String groupId) {
        if (groupId == null || groupId.isEmpty()) {
            throw new BaseException("群聊不存在");
        }
        ChatGroup group = chatGroupMapper.findGroupById(groupId);
        if (group == null) {
            throw new BaseException("群聊不存在");
        }
        return group;
    }

    private ChatGroupMember requireMember(String groupId, String userId) {
        ChatGroupMember member = chatGroupMapper.findMember(groupId, userId);
        if (member == null) {
            throw new BaseException("你不在该群聊中");
        }
        return member;
    }

    /**
     * 清洗成员 ID：去空、去重、可选剔除某人（建群时把自己剔除掉）
     * 用 LinkedHashSet 是为了保持前端选择的顺序，同时天然去重
     */
    private List<String> normalizeMemberIds(List<String> raw, String excludeUserId) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String id : raw) {
            if (id == null || id.trim().isEmpty()) {
                continue;
            }
            String value = id.trim();
            if (excludeUserId != null && excludeUserId.equals(value)) {
                continue;
            }
            seen.add(value);
        }
        result.addAll(seen);
        return result;
    }

    private String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String value = message.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > MESSAGE_MAX_LENGTH ? value.substring(0, MESSAGE_MAX_LENGTH) : value;
    }
}
