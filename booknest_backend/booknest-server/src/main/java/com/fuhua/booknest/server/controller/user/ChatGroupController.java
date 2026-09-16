package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.ChatGroupCreateDTO;
import com.fuhua.booknest.pojo.dto.ChatGroupInviteDTO;
import com.fuhua.booknest.pojo.dto.ChatGroupUpdateDTO;
import com.fuhua.booknest.pojo.dto.GroupJoinApplyDTO;
import com.fuhua.booknest.pojo.vo.ChatGroupVO;
import com.fuhua.booknest.pojo.vo.GroupInvitationVO;
import com.fuhua.booknest.pojo.vo.GroupMemberVO;
import com.fuhua.booknest.server.service.ChatGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 群聊接口
 *
 * 所有接口都从登录态（BaseContext）推导当前用户，不接受前端传入 operatorId，
 * 否则任何人都能伪造成群主去解散别人的群。
 */
@RestController
@RequestMapping("/chat/groups")
@Slf4j
@Tag(name = "群聊相关接口")
public class ChatGroupController {

    @Autowired
    private ChatGroupService chatGroupService;

    /**
     * 创建群聊
     * @param dto 建群信息（群名 + 初始成员）
     * @return 群 VO
     */
    @PostMapping
    @Operation(summary = "创建群聊")
    public Result<ChatGroupVO> createGroup(@RequestBody @Valid ChatGroupCreateDTO dto) {
        return Result.success(chatGroupService.createGroup(BaseContext.getCurrentId(), dto));
    }

    /**
     * 查询我加入的群
     * @return 群列表
     */
    @GetMapping
    @Operation(summary = "查询我加入的群聊")
    public Result<List<ChatGroupVO>> listMyGroups() {
        return Result.success(chatGroupService.listMyGroups(BaseContext.getCurrentId()));
    }

    /* ------------------------------------------------------------------
       下面这几个「字面量路径」接口必须写在 /{groupId} 之前。
       虽然 Spring 的路径匹配里字面量优先级高于变量（不会被吃掉），
       但显式前置能让后来者一眼看出这里的顺序是刻意的，而不是碰巧能跑。
       ------------------------------------------------------------------ */

    /**
     * 发现群聊：按群名搜索我还没加入的群
     * @param keyword 群名关键字
     * @return 群列表
     */
    @GetMapping("/search")
    @Operation(summary = "搜索可加入的群聊")
    public Result<List<ChatGroupVO>> searchGroups(@RequestParam String keyword) {
        return Result.success(chatGroupService.searchGroups(keyword, BaseContext.getCurrentId()));
    }

    /**
     * 我收到的待确认入群邀请（别人邀请我，等我同意）
     * @return 邀请列表
     */
    @GetMapping("/invitations")
    @Operation(summary = "查询我收到的入群邀请")
    public Result<List<GroupInvitationVO>> listMyInvitations() {
        return Result.success(chatGroupService.listMyInvitations(BaseContext.getCurrentId()));
    }

    /**
     * 同意 / 拒绝收到的入群邀请
     * @param invitationId 邀请 ID
     * @param body 只读取 accept 字段
     * @return 同意时返回入群后的群 ID
     */
    @PostMapping("/invitations/{invitationId}/handle")
    @Operation(summary = "处理入群邀请")
    public Result<String> handleInvitation(@PathVariable String invitationId,
                                           @RequestBody(required = false) Map<String, Object> body) {
        boolean accept = body != null && Boolean.TRUE.equals(body.get("accept"));
        return Result.success(chatGroupService.handleInvitation(
                invitationId, BaseContext.getCurrentId(), accept));
    }

    /**
     * 我发起且仍待审批的加入申请（前端据此把按钮置为「已申请」）
     * @return 群 ID 列表
     */
    @GetMapping("/my-pending-joins")
    @Operation(summary = "查询我待审批的加入申请")
    public Result<List<String>> listMyPendingJoins() {
        return Result.success(chatGroupService.listMyPendingJoinGroupIds(BaseContext.getCurrentId()));
    }

    /**
     * 我（作为群主）名下所有群待审批的申请总数，导航红点轮询用。
     * <p>路径是精确匹配，Spring 会优先于 GET /{groupId} 命中，
     * 不会被当成「群 ID 为 pending-request-count」处理。</p>
     * @return 待审批申请数
     */
    @GetMapping("/pending-request-count")
    @Operation(summary = "查询我待审批的入群申请总数")
    public Result<Integer> countPendingRequests() {
        return Result.success(chatGroupService.countPendingJoinRequests(BaseContext.getCurrentId()));
    }

    /**
     * 群主审批加入申请：通过
     * @param invitationId 申请 ID
     * @return 群 ID
     */
    @PostMapping("/requests/{invitationId}/approve")
    @Operation(summary = "通过入群申请")
    public Result<String> approveJoin(@PathVariable String invitationId) {
        return Result.success(chatGroupService.approveJoin(
                invitationId, BaseContext.getCurrentId(), true));
    }

    /**
     * 群主审批加入申请：拒绝
     * @param invitationId 申请 ID
     * @return 空
     */
    @PostMapping("/requests/{invitationId}/reject")
    @Operation(summary = "拒绝入群申请")
    public Result<String> rejectJoin(@PathVariable String invitationId) {
        chatGroupService.approveJoin(invitationId, BaseContext.getCurrentId(), false);
        return Result.success("已拒绝");
    }

    /**
     * 查询群详情（含成员）
     * @param groupId 群 ID
     * @return 群 VO
     */
    @GetMapping("/{groupId}")
    @Operation(summary = "查询群详情")
    public Result<ChatGroupVO> getGroupDetail(@PathVariable String groupId) {
        return Result.success(chatGroupService.getGroupDetail(groupId, BaseContext.getCurrentId()));
    }

    /**
     * 群主查看某个群待审批的加入申请
     * @param groupId 群 ID
     * @return 申请列表
     */
    @GetMapping("/{groupId}/requests")
    @Operation(summary = "查询群待审批的加入申请")
    public Result<List<GroupInvitationVO>> listJoinRequests(@PathVariable String groupId) {
        return Result.success(chatGroupService.listJoinRequests(groupId, BaseContext.getCurrentId()));
    }

    /**
     * 申请加入某个群（写待审批记录，等群主通过）
     * @param groupId 群 ID
     * @param dto 申请理由（可空）
     * @return 操作结果
     */
    @PostMapping("/{groupId}/join")
    @Operation(summary = "申请加入群聊")
    public Result<String> applyJoin(@PathVariable String groupId,
                                    @RequestBody(required = false) GroupJoinApplyDTO dto) {
        chatGroupService.applyJoin(groupId, BaseContext.getCurrentId(), dto == null ? null : dto.getMessage());
        return Result.success("申请已提交，等待群主通过");
    }

    /**
     * 查询群成员
     * @param groupId 群 ID
     * @return 成员列表
     */
    @GetMapping("/{groupId}/members")
    @Operation(summary = "查询群成员")
    public Result<List<GroupMemberVO>> listMembers(@PathVariable String groupId) {
        return Result.success(chatGroupService.listMembers(groupId, BaseContext.getCurrentId()));
    }

    /**
     * 邀请成员入群
     * @param groupId 群 ID
     * @param dto 待邀请用户
     * @return 更新后的群 VO
     */
    @PostMapping("/{groupId}/members")
    @Operation(summary = "邀请成员入群")
    public Result<ChatGroupVO> inviteMembers(@PathVariable String groupId,
                                             @RequestBody @Valid ChatGroupInviteDTO dto) {
        return Result.success(chatGroupService.inviteMembers(groupId, BaseContext.getCurrentId(), dto.getUserIds()));
    }

    /**
     * 退群（targetUserId 传自己）或移除成员（群主）
     * @param groupId 群 ID
     * @param userId 目标用户 ID
     * @return 操作结果
     */
    @DeleteMapping("/{groupId}/members/{userId}")
    @Operation(summary = "退群或移除群成员")
    public Result<String> removeMember(@PathVariable String groupId, @PathVariable String userId) {
        chatGroupService.removeMember(groupId, BaseContext.getCurrentId(), userId);
        return Result.success("操作成功");
    }

    /**
     * 修改群资料（群主）
     * @param groupId 群 ID
     * @param dto 修改内容
     * @return 更新后的群 VO
     */
    @PutMapping("/{groupId}")
    @Operation(summary = "修改群资料")
    public Result<ChatGroupVO> updateGroup(@PathVariable String groupId,
                                           @RequestBody @Valid ChatGroupUpdateDTO dto) {
        return Result.success(chatGroupService.updateGroup(groupId, BaseContext.getCurrentId(), dto));
    }

    /**
     * 解散群（群主）
     * @param groupId 群 ID
     * @return 操作结果
     */
    @DeleteMapping("/{groupId}")
    @Operation(summary = "解散群聊")
    public Result<String> dissolveGroup(@PathVariable String groupId) {
        chatGroupService.dissolveGroup(groupId, BaseContext.getCurrentId());
        return Result.success("群聊已解散");
    }

    /**
     * 标记群已读
     * @param groupId 群 ID
     * @return 操作结果
     */
    @PostMapping("/{groupId}/read")
    @Operation(summary = "标记群聊已读")
    public Result<String> markGroupRead(@PathVariable String groupId) {
        chatGroupService.markGroupRead(groupId, BaseContext.getCurrentId());
        return Result.success("已标记为已读");
    }
}
