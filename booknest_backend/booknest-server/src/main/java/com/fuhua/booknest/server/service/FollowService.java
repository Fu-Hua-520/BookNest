package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.FollowVO;

import java.util.List;

public interface FollowService {

    /**
     * 关注用户
     * @param followeeId 被关注者用户ID
     */
    void follow(String followeeId);

    /**
     * 取消关注
     * @param followeeId 被关注者用户ID
     */
    void unfollow(String followeeId);

    /**
     * 查询当前用户是否已关注目标用户
     * @param followeeId 被关注者用户ID
     * @return 是否已关注
     */
    boolean isFollowing(String followeeId);

    /**
     * 查询用户的关注列表
     * @param userId 用户ID
     * @return 关注列表
     */
    List<FollowVO> listFollowing(String userId);

    /**
     * 查询用户的粉丝列表
     * @param userId 用户ID
     * @return 粉丝列表
     */
    List<FollowVO> listFollowers(String userId);
}
