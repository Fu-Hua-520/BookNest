package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群邀请 / 加入申请 VO
 *
 * 把「群」与「人」的信息都拍平进来，前端一处即可渲染出
 * 「某某邀请你加入「群名」」或「某某申请加入「群名」」这类完整条目。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupInvitationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String groupId;
    private String groupName;
    private String groupAvatar;
    /** 群当前成员数，方便用户判断要不要进 */
    private Integer memberCount;

    private String inviterId;
    private String inviterName;
    private String inviterAvatar;

    private String inviteeId;
    private String inviteeName;
    private String inviteeAvatar;

    /** INVITE / JOIN_REQUEST */
    private String type;
    /** PENDING / ACCEPTED / REJECTED */
    private String status;
    private String message;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
