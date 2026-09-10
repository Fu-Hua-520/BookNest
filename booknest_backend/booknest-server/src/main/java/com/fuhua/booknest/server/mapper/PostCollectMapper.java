package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.PostCollect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostCollectMapper {

    /**
     * 插入帖子收藏记录
     * @param postCollect 收藏记录信息
     */
    void insert(PostCollect postCollect);

    /**
     * 根据帖子ID与用户ID删除收藏记录
     * @param postId 帖子ID
     * @param userId 用户ID
     */
    void deleteByPostAndUser(@Param("postId") String postId, @Param("userId") String userId);

    /**
     * 根据帖子ID删除该帖下所有收藏记录（级联删除帖子时调用）
     * @param postId 帖子ID
     */
    void deleteByPostId(@Param("postId") String postId);

    /**
     * 根据帖子ID与用户ID查询收藏记录
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return 收藏记录（不存在返回 null）
     */
    PostCollect selectByPostAndUser(@Param("postId") String postId, @Param("userId") String userId);

    /**
     * 统计帖子收藏数
     * @param postId 帖子ID
     * @return 收藏数
     */
    int countByPostId(@Param("postId") String postId);
}
