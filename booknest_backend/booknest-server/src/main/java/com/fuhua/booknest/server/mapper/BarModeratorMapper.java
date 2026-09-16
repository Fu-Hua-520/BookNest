package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.BarModerator;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BarModeratorMapper {

    /**
     * 任命管理员
     * @param moderator 管理员记录
     */
    void insert(BarModerator moderator);

    /**
     * 撤销管理员
     * @param barId 书吧ID
     * @param userId 用户ID
     */
    void deleteByBarAndUser(@Param("barId") String barId, @Param("userId") String userId);

    /**
     * 判断某人是否是该吧的管理员
     * @param barId 书吧ID
     * @param userId 用户ID
     * @return 命中返回 1，否则 0
     */
    int countByBarAndUser(@Param("barId") String barId, @Param("userId") String userId);

    /**
     * 查询某吧的全部管理员用户ID（按任命时间正序）
     * @param barId 书吧ID
     * @return 用户ID列表
     */
    List<String> listUserIdsByBar(@Param("barId") String barId);

    /**
     * 删掉某吧的全部管理员（书吧被删除时清理，避免留下孤儿行）
     * @param barId 书吧ID
     */
    void deleteByBarId(@Param("barId") String barId);
}
