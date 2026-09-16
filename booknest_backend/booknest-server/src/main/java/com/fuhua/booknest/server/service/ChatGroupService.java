package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.ChatGroupCreateDTO;
import com.fuhua.booknest.pojo.dto.ChatGroupUpdateDTO;
import com.fuhua.booknest.pojo.vo.ChatGroupVO;
import com.fuhua.booknest.pojo.vo.GroupInvitationVO;
import com.fuhua.booknest.pojo.vo.GroupMemberVO;

import java.util.List;

/**
 * 群聊服务
 */
public interface ChatGroupService {

    /**
     * 创建群聊（创建者自动成为群主并加入）
     * @param ownerId 创建者用户ID（由登录态推导，不接受前端传入）
     * @param dto 建群信息
     * @return 群 VO
     */
    ChatGroupVO createGroup(String ownerId, ChatGroupCreateDTO dto);

    /**
     * 查询我加入的群列表
     * @param userId 当前用户ID
     * @return 群列表
     */
    List<ChatGroupVO> listMyGroups(String userId);

    /**
     * 查询群详情（含成员列表，需为群成员）
     * @param groupId 群 ID
     * @param userId 当前用户ID
     * @return 群 VO
     */
    ChatGroupVO getGroupDetail(String groupId, String userId);

    /**
     * 查询群成员
     * @param groupId 群 ID
     * @param operatorId 操作者用户ID（需为群成员）
     * @return 成员列表
     */
    List<GroupMemberVO> listMembers(String groupId, String operatorId);

    /**
     * 邀请成员入群（群成员均可操作）。
     * <p>注意语义：这里**不直接把人加进群**，而是写入一条待确认的 INVITE 记录，
     * 由被邀请人同意后才真正成为群成员。返回值仍是当前群 VO（成员数不变）。</p>
     * @param groupId 群 ID
     * @param operatorId 操作者用户ID
     * @param userIds 待邀请用户ID
     * @return 更新后的群 VO
     */
    ChatGroupVO inviteMembers(String groupId, String operatorId, List<String> userIds);

    /**
     * 退群 / 移除成员：
     * 传入自己即「退群」（群主不允许直接退，需要先解散或自行解散）；
     * 传入他人时仅群主有权限。
     * @param groupId 群 ID
     * @param operatorId 操作者用户ID
     * @param targetUserId 目标用户ID
     */
    void removeMember(String groupId, String operatorId, String targetUserId);

    /**
     * 修改群资料（仅群主）
     * @param groupId 群 ID
     * @param operatorId 操作者用户ID
     * @param dto 修改内容
     * @return 更新后的群 VO
     */
    ChatGroupVO updateGroup(String groupId, String operatorId, ChatGroupUpdateDTO dto);

    /**
     * 解散群（仅群主），同时清理成员关系与群消息
     * @param groupId 群 ID
     * @param operatorId 操作者用户ID
     */
    void dissolveGroup(String groupId, String operatorId);

    /**
     * 推进当前用户在群内的已读位点
     * @param groupId 群 ID
     * @param userId 当前用户ID
     */
    void markGroupRead(String groupId, String userId);

    /**
     * 查询群成员用户ID列表（WebSocket 广播用）
     * @param groupId 群 ID
     * @return 用户ID列表
     */
    List<String> listMemberIds(String groupId);

    /* ==================== 邀请 / 加入申请 ==================== */

    /**
     * 我收到的待确认入群邀请（对方邀请我，等我点同意）
     * @param userId 当前用户ID
     * @return 邀请列表
     */
    List<GroupInvitationVO> listMyInvitations(String userId);

    /**
     * 同意 / 拒绝收到的入群邀请
     * @param invitationId 邀请 ID
     * @param userId 当前用户ID（必须是被邀请人本人）
     * @param accept true-同意并入群 false-拒绝
     * @return 同意时返回入群后的群 ID，拒绝时返回 null
     */
    String handleInvitation(String invitationId, String userId, boolean accept);

    /**
     * 主动申请加入某个群（写入 JOIN_REQUEST，等群主审批）
     * @param groupId 群 ID
     * @param userId 申请者用户ID
     * @param message 申请理由（可空）
     */
    void applyJoin(String groupId, String userId, String message);

    /**
     * 查询我发起的、仍待处理的加入申请所对应的群 ID 集合
     * <p>前端用来把「已申请」的按钮置灰，避免重复点。</p>
     * @param userId 申请者用户ID
     * @return 群 ID 列表
     */
    List<String> listMyPendingJoinGroupIds(String userId);

    /**
     * 我（作为群主）名下所有群待审批的加入申请总数（导航红点用）
     * @param userId 用户ID
     * @return 待审批申请数
     */
    int countPendingJoinRequests(String userId);

    /**
     * 某个群待审批的加入申请（仅群主可见）
     * @param groupId 群 ID
     * @param operatorId 操作者用户ID
     * @return 申请列表
     */
    List<GroupInvitationVO> listJoinRequests(String groupId, String operatorId);

    /**
     * 群主审批加入申请
     * @param invitationId 申请 ID
     * @param operatorId 操作者用户ID（必须是该群群主）
     * @param approve true-通过 false-拒绝
     * @return 通过时返回群 ID，拒绝时返回 null
     */
    String approveJoin(String invitationId, String operatorId, boolean approve);

    /**
     * 发现群聊：按群名搜索我还没加入的群
     * @param keyword 群名关键字（为空返回空列表）
     * @param userId 当前用户ID
     * @return 群列表
     */
    List<ChatGroupVO> searchGroups(String keyword, String userId);
}
