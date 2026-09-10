package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.AIConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AIConversationMapper {

    /**
     * 插入 AI 会话
     * @param conversation 会话实体
     */
    void insert(AIConversation conversation);

    /**
     * 根据会话ID查询会话
     * @param id 会话ID
     * @return 会话实体（不存在返回 null）
     */
    AIConversation selectById(@Param("id") String id);

    /**
     * 查询用户的会话列表（正常状态，按更新时间倒序）
     * @param userId 用户ID
     * @return 会话列表
     */
    List<AIConversation> selectByUserId(@Param("userId") String userId);

    /**
     * 选择性更新会话信息
     * @param conversation 会话实体
     */
    void update(AIConversation conversation);

    /**
     * 软删除会话（status=0）
     * @param id 会话ID
     */
    void softDelete(@Param("id") String id);
}
