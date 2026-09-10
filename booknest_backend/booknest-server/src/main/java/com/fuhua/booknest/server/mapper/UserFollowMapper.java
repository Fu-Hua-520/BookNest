package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserFollowMapper {

    /**
     * 插入关注记录
     * @param userFollow 关注记录
     */
    void insert(UserFollow userFollow);

    /**
     * 取消关注（根据关注者与被关注者删除记录）
     * @param followerId 关注者用户ID
     * @param followeeId 被关注者用户ID
     */
    void deleteByFollowerAndFollowee(@Param("followerId") String followerId, @Param("followeeId") String followeeId);

    /**
     * 查询是否已关注
     * @param followerId 关注者用户ID
     * @param followeeId 被关注者用户ID
     * @return 关注记录（不存在返回 null）
     */
    UserFollow selectByFollowerAndFollowee(@Param("followerId") String followerId, @Param("followeeId") String followeeId);

    /**
     * 查询关注列表（被关注用户ID，按关注时间倒序）
     * @param followerId 关注者用户ID
     * @return 被关注用户ID列表
     */
    List<String> listFolloweeIds(@Param("followerId") String followerId);

    /**
     * 查询粉丝列表（关注者用户ID，按关注时间倒序）
     * @param followeeId 被关注者用户ID
     * @return 关注者用户ID列表
     */
    List<String> listFollowerIds(@Param("followeeId") String followeeId);
}
