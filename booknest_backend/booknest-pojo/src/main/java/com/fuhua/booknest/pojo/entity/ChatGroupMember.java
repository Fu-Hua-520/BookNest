package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群成员实体
 *
 * last_read_at 承担群聊的「已读位点」：群消息不写 is_read（一条消息对应多人，
 * 单列存不下多人的已读态），改成每个成员各记一个时间点，
 * 未读数 = 群里 last_read_at 之后、且不是自己发的消息条数。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroupMember implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String groupId;
    private String userId;
    /** 角色：OWNER 群主 / MEMBER 普通成员 */
    private String role;
    /** 已读位点，为空视为从未读过 */
    private LocalDateTime lastReadAt;
    private LocalDateTime joinTime;
}
