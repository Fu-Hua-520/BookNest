package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.AIMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AIMessageMapper {

    /**
     * 插入 AI 消息
     * @param message 消息实体
     */
    void insert(AIMessage message);

    /**
     * 查询会话的全部消息（按创建时间正序）
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<AIMessage> selectByConversationId(@Param("conversationId") String conversationId);

    /**
     * 查询会话最近 limit 条消息（按创建时间倒序，调用方自行反转为正序）
     * @param conversationId 会话ID
     * @param limit 条数
     * @return 消息列表（倒序）
     */
    List<AIMessage> getRecentMessages(@Param("conversationId") String conversationId, @Param("limit") int limit);
}
