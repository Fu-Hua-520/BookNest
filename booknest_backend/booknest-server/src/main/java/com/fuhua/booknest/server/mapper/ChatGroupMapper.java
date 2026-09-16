package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.ChatGroup;
import com.fuhua.booknest.pojo.entity.ChatGroupMember;
import com.fuhua.booknest.pojo.vo.GroupMemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatGroupMapper {

    /**
     * 新建群
     * @param group 群实体
     */
    void insertGroup(ChatGroup group);

    /**
     * 新增群成员
     * @param member 成员实体
     */
    void insertMember(ChatGroupMember member);

    /**
     * 批量新增群成员
     * @param members 成员列表
     */
    void batchInsertMembers(@Param("members") List<ChatGroupMember> members);

    /**
     * 按群 ID 查询群
     * @param id 群 ID
     * @return 群实体（不存在返回 null）
     */
    ChatGroup findGroupById(@Param("id") String id);

    /**
     * 查询成员关系
     * @param groupId 群 ID
     * @param userId 用户 ID
     * @return 成员实体（不在群里返回 null）
     */
    ChatGroupMember findMember(@Param("groupId") String groupId, @Param("userId") String userId);

    /**
     * 查询用户加入的所有群（按最后消息时间倒序）
     * @param userId 用户 ID
     * @return 群列表
     */
    List<ChatGroup> listMyGroups(@Param("userId") String userId);

    /**
     * 查询群成员（带用户名/头像）
     * @param groupId 群 ID
     * @return 成员列表
     */
    List<GroupMemberVO> listMembers(@Param("groupId") String groupId);

    /**
     * 查询群成员用户 ID 列表（用于消息广播，避免广播时再去 join 用户表）
     * @param groupId 群 ID
     * @return 用户 ID 列表
     */
    List<String> listMemberIds(@Param("groupId") String groupId);

    /**
     * 统计群成员数
     * @param groupId 群 ID
     * @return 成员数
     */
    int countMembers(@Param("groupId") String groupId);

    /**
     * 更新群最后一条消息预览
     * @param id 群 ID
     * @param lastMessage 预览文本
     * @param lastMsgAt 消息时间
     */
    void updateLastMessage(@Param("id") String id,
                           @Param("lastMessage") String lastMessage,
                           @Param("lastMsgAt") LocalDateTime lastMsgAt);

    /**
     * 更新群名称与公告（选择性更新）
     * @param group 群实体（id 必填）
     */
    void updateGroup(ChatGroup group);

    /**
     * 移除群成员
     * @param groupId 群 ID
     * @param userId 用户 ID
     */
    void deleteMember(@Param("groupId") String groupId, @Param("userId") String userId);

    /**
     * 解散群：删除全部成员关系
     * @param groupId 群 ID
     */
    void deleteMembersByGroupId(@Param("groupId") String groupId);

    /**
     * 解散群：删除群本身
     * @param groupId 群 ID
     */
    void deleteGroup(@Param("groupId") String groupId);

    /**
     * 推进成员的已读位点
     * @param groupId 群 ID
     * @param userId 用户 ID
     * @param lastReadAt 已读时间点
     */
    void updateLastReadAt(@Param("groupId") String groupId,
                          @Param("userId") String userId,
                          @Param("lastReadAt") LocalDateTime lastReadAt);

    /**
     * 统计某群内当前用户的未读数：
     * last_read_at 之后、且不是自己发的消息条数（last_read_at 为空视为从未读过）
     * @param groupId 群 ID
     * @param userId 用户 ID
     * @return 未读数
     */
    int countUnreadInGroup(@Param("groupId") String groupId, @Param("userId") String userId);

    /**
     * 统计用户所有群的未读总数（导航角标用）
     * @param userId 用户 ID
     * @return 未读总数
     */
    int countUnreadGroups(@Param("userId") String userId);

    /**
     * 按群名模糊搜索「我还没加入」的群（「发现群聊 / 加入别人的群」入口）
     * @param keyword 群名关键字
     * @param userId 当前用户 ID（用于排除已加入的群）
     * @return 群列表（最多 30 条，按最后消息时间倒序）
     */
    List<ChatGroup> searchByName(@Param("keyword") String keyword, @Param("userId") String userId);
}
