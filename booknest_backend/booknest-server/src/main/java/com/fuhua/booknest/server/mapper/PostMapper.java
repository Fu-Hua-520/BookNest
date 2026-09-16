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
     * @param userId 作者用户ID（可空，用于「个人主页只看某人帖子」）
     * @param auditStatus 审核状态（可空）
     * @param status 帖子状态（可空）
     * @param sort 排序方式（可空，见 PostSortConstant；为空按发布时间倒序）
     * @param postType 帖子类型（可空，见 PostTypeConstant；为空不过滤）
     * @return 帖子列表
     */
    List<Post> list(@Param("categoryId") String categoryId,
                    @Param("tagId") String tagId,
                    @Param("userId") String userId,
                    @Param("auditStatus") Integer auditStatus,
                    @Param("status") Integer status,
                    @Param("sort") String sort,
                    @Param("postType") String postType);

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
     * 批量按 ID 查帖子（收藏列表按 ID 回捞、搜索结果补全等）。
     *
     * @param postIds 帖子 ID 集合
     * @return 帖子列表
     */
    List<Post> selectByIds(@Param("postIds") List<String> postIds);

    /**
     * 一次性累加四个计数（由计数落库任务调用，业务代码不直接用它）。
     *
     * <p><b>为什么是「一条 UPDATE 带四个增量」而不是四个 increment 方法：</b>
     * 计数不再由业务同步写入，而是先在 Redis 里按帖子累加、由
     * {@code CountFlushJob} 定时批量落库。落库时同一篇帖子的浏览/点赞/评论/收藏
     * 应当合成一条 UPDATE —— 一篇帖子被浏览 300 次再被点赞 20 次，是 1 条语句而不是 320 条。
     * 增量可能为负（取消点赞/取消收藏/删评论），也可能是 0（该维度本次没有变化）。</p>
     *
     * <p>用 {@code greatest(..., 0)} 兜底：正常数据流里计数不会为负，但历史脏数据
     * （例如 like_count 已是 0 却还有点赞行）叠加负增量就会把它压到负数，
     * 展示成「-1 赞」。夹住比让用户看到负数体面。</p>
     *
     * @param id           帖子ID
     * @param viewDelta    浏览量增量（可为 0 或负）
     * @param likeDelta    点赞数增量
     * @param commentDelta 评论数增量
     * @param collectDelta 收藏数增量
     */
    void applyCountDeltas(@Param("id") String id,
                          @Param("viewDelta") long viewDelta,
                          @Param("likeDelta") long likeDelta,
                          @Param("commentDelta") long commentDelta,
                          @Param("collectDelta") long collectDelta);

    /**
     * 关键词召回：按标题/摘要模糊匹配已过审帖子（供 RAG 混合检索）
     * @param keyword 关键词
     * @param limit 返回条数上限
     * @return 帖子列表
     */
    List<Post> searchByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

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
    /**
     * 统计使用指定分类的帖子数（删除分类前的引用检查）
     * @param categoryId 分类ID
     * @return 帖子数
     */
    int countByCategoryId(@Param("categoryId") String categoryId);
}
