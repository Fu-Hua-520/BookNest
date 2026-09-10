package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostMapper {

    /**
     * 插入新帖子
     * @param post 帖子信息
     */
    void insert(Post post);

    /**
     * 根据帖子ID查询帖子
     * @param id 帖子ID
     * @return 帖子信息
     */
    Post selectById(@Param("id") String id);

    /**
     * 根据作者用户ID查询帖子列表
     * @param userId 用户ID
     * @return 帖子列表
     */
    List<Post> selectByUserId(@Param("userId") String userId);

    /**
     * 条件查询帖子列表（供 PageHelper 分页）
     * @param categoryId 分类ID（可空）
     * @param tagId 标签ID（可空）
     * @param auditStatus 审核状态（可空）
     * @param status 帖子状态（可空）
     * @return 帖子列表
     */
    List<Post> list(@Param("categoryId") String categoryId,
                    @Param("tagId") String tagId,
                    @Param("auditStatus") Integer auditStatus,
                    @Param("status") Integer status);

    /**
     * 更新帖子信息
     * @param post 帖子信息
     */
    void update(Post post);

    /**
     * 根据帖子ID删除帖子
     * @param id 帖子ID
     */
    void deleteById(@Param("id") String id);

    /**
     * 浏览量 +1
     * @param id 帖子ID
     */
    void incrementViewCount(@Param("id") String id);

    /**
     * 点赞数 +1
     * @param id 帖子ID
     */
    void incrementLikeCount(@Param("id") String id);

    /**
     * 评论数 +1
     * @param id 帖子ID
     */
    void incrementCommentCount(@Param("id") String id);

    /**
     * 收藏数 +1
     * @param id 帖子ID
     */
    void incrementCollectCount(@Param("id") String id);
}
