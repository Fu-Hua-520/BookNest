package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.ChatGroupInvitation;
import com.fuhua.booknest.pojo.vo.GroupInvitationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatGroupInvitationMapper {

    /**
     * 新增一条邀请 / 加入申请
     * @param invitation 邀请实体
     */
    void insert(ChatGroupInvitation invitation);

    /**
     * 按 ID 查询
     * @param id 邀请 ID
     * @return 邀请实体（不存在返回 null）
     */
    ChatGroupInvitation findById(@Param("id") String id);

    /**
     * 查询是否已存在同方向的待处理记录（防重复邀请 / 重复申请）
     * @param groupId 群 ID
     * @param inviteeId 被邀请人 / 申请人
     * @param type INVITE / JOIN_REQUEST
     * @param status 状态（一般传 PENDING）
     * @return 已存在的记录（无则 null）
     */
    ChatGroupInvitation findOne(@Param("groupId") String groupId,
                               @Param("inviteeId") String inviteeId,
                               @Param("type") String type,
                               @Param("status") String status);

    /**
     * 我收到的待确认邀请（type=INVITE，inviteeId=我，PENDING）
     * @param inviteeId 当前用户 ID
     * @return 邀请列表（按创建时间倒序）
     */
    List<GroupInvitationVO> listMyInvitations(@Param("inviteeId") String inviteeId);

    /**
     * 某个群待审批的加入申请（type=JOIN_REQUEST，PENDING）
     * @param groupId 群 ID
     * @return 申请列表（按创建时间倒序）
     */
    List<GroupInvitationVO> listJoinRequests(@Param("groupId") String groupId);

    /**
     * 我发起的、仍待处理的加入申请对应的群 ID 集合
     * <p>前端用来把已申请的群标成「已申请」并禁用按钮。</p>
     * @param inviteeId 申请者用户 ID
     * @return 群 ID 列表
     */
    List<String> listPendingJoinGroupIds(@Param("inviteeId") String inviteeId);

    /**
     * 统计某个群待审批的加入申请条数（群主看板用）
     * @param groupId 群 ID
     * @return 条数
     */
    int countPendingJoins(@Param("groupId") String groupId);

    /**
     * 统计「我是群主的所有群」里待审批的加入申请总数。
     * 给导航红点用：有新申请时群主要能一眼看到，而不是挨个群点开翻。
     * @param userId 用户 ID（按群主身份统计）
     * @return 待审批申请总数
     */
    int countPendingJoinsForOwner(@Param("userId") String userId);

    /**
     * 更新处理状态
     * @param id 邀请 ID
     * @param status ACCEPTED / REJECTED
     * @param handleTime 处理时间
     */
    void updateStatus(@Param("id") String id,
                      @Param("status") String status,
                      @Param("handleTime") LocalDateTime handleTime);

    /**
     * 解散群时清理该群的所有邀请记录
     * @param groupId 群 ID
     */
    void deleteByGroupId(@Param("groupId") String groupId);
}
