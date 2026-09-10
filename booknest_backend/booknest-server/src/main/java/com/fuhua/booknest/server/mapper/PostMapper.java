package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    /**
     * 点赞数 -1
     * @param id 帖子ID
     */
    void decrementLikeCount(@Param("id") String id);

    /**
     * 评论数 -1
     * @param id 帖子ID
     */
    void decrementCommentCount(@Param("id") String id);

    /**
     * 评论数 -count（级联删除评论时按实际删除条数扣减）
     * @param id 帖子ID
     * @param count 扣减数量
     */
    void decrementCommentCountBy(@Param("id") String id, @Param("count") int count);

    /**
     * 收藏数 -1
     * @param id 帖子ID
     */
    void decrementCollectCount(@Param("id") String id);

    /**
     * 关键词召回：按标题/摘要模糊匹配已过审帖子（供 RAG 混合检索）
     * @param keyword 关键词
     * @param limit 返回条数上限
     * @return 帖子列表
     */
    List<Post> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * 查询全部已过审帖子（供批量向量化入库）
     * @return 帖子列表
     */
    List<Post> selectAllApproved();

    /**
     * 帖子审核（设置审核状态、审核原因、审核时间）
     * @param id 帖子ID
     * @param auditStatus 审核状态
     * @param auditReason 审核原因
     * @param auditTime 审核时间
     */
    void audit(@Param("id") String id,
               @Param("auditStatus") Integer auditStatus,
               @Param("auditReason") String auditReason,
               @Param("auditTime") LocalDateTime auditTime);

    /**
     * 更新帖子状态
     * @param id 帖子ID
     * @param status 帖子状态
     */
    void updateStatus(@Param("id") String id, @Param("status") Integer status);

    /**
     * 更新帖子置顶状态
     * @param id 帖子ID
     * @param isTop 是否置顶（0/1）
     */
    void updateIsTop(@Param("id") String id, @Param("isTop") Integer isTop);
}
