package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 群聊会话 VO（群列表 / 群详情共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String avatar;
    private String ownerId;
    private String ownerName;
    private String notice;
    private Integer memberCount;
    private String lastMessage;
    private LocalDateTime lastMsgAt;
    private LocalDateTime createTime;
    /** 当前用户在该群的未读数 */
    private Integer unreadCount;
    /** 当前用户是否群主（前端据此决定是否展示「解散/管理」入口） */
    private Boolean isOwner;
    /**
     * 待审批的加入申请条数。
     * 仅群主视角有值（其他人恒为 0），前端用来在「群信息」上打红点。
     */
    private Integer pendingJoinCount;
    /** 创建者视角返回的成员列表，仅建群/详情接口填充 */
    private List<GroupMemberVO> members;
}
