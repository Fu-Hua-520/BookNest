package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群邀请 / 加入申请
 *
 * 一张表承载两个方向的「进群请求」，靠 type 区分：
 *   INVITE       —— 群内成员邀请站内书友，等**被邀请人**确认
 *   JOIN_REQUEST —— 用户主动申请加入，等**群主**审批
 *
 * 为什么不做成「邀请即入群」：
 * 直接把人塞进群会让用户莫名其妙出现在陌生群里，也无法拒绝。
 * 拆成一条待处理记录后，双方都有明确的接受/拒绝动作，且历史可追溯。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupInvitation implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    /** 目标群 ID */
    private String groupId;
    /** 发起人：INVITE 时是邀请人；JOIN_REQUEST 时为空（由申请者自己发起） */
    private String inviterId;
    /** 被邀请人 / 申请人 */
    private String inviteeId;
    /** INVITE / JOIN_REQUEST（见 ChatConstant） */
    private String type;
    /** PENDING / ACCEPTED / REJECTED */
    private String status;
    /** 附言（邀请说明或申请理由） */
    private String message;
    private LocalDateTime createTime;
    /** 处理时间，未处理时为空 */
    private LocalDateTime handleTime;
}
