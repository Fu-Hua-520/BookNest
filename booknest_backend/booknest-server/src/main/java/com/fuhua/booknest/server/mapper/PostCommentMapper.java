package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostCommentMapper {

    /**
     * 插入评论
     * @param postComment 评论信息
     */
    void insert(PostComment postComment);

    /**
     * 根据评论ID查询评论
     * @param id 评论ID
     * @return 评论信息
     */
    PostComment selectById(@Param("id") String id);

    /**
     * 按ID批量查询评论（评论列表组装「回复 @某某」的父评论时用）。
     *
     * <p>一次 {@code in} 查询替代「循环里逐条 selectById」：一页 50 条评论里若有一半是回复，
     * 原本要发 25 次单条查询。调用方需保证 {@code ids} 非空 ——
     * {@code where id in ()} 是语法错误。</p>
     *
     * @param ids 评论ID集合（调用方保证非空）
     * @return 评论列表
     */
    List<PostComment> selectByIds(@Param("ids") List<String> ids);

    /**
     * 根据评论ID删除评论
     * @param id 评论ID
     */
    void deleteById(@Param("id") String id);

    /**
     * 根据帖子ID删除该帖下所有评论（级联删除帖子时调用）
     * @param postId 帖子ID
     */
    void deleteByPostId(@Param("postId") String postId);

    /**
     * 根据被回复评论ID查询所有回复该评论的评论列表（楼中楼后代收集）
     * @param replyId 被回复评论ID
     * @return 回复评论列表
     */
    List<PostComment> listByReplyId(@Param("replyId") String replyId);

    /**
     * 根据帖子ID查询评论列表（顶级评论与回复统一按时间正序，前端组装楼层）
     * @param postId 帖子ID
     * @return 评论列表
     */
    List<PostComment> listByPostId(@Param("postId") String postId);

    /**
     * 评论点赞数批量累加（只由计数落库任务调用，业务代码不直接用它）。
     *
     * <p>与 {@code PostMapper.applyCountDeltas} 同源：评论点赞数同样先在 Redis 里累加，
     * 再由 {@code CountFlushJob} 定时批量落库。增量可为负（取消点赞）。</p>
     *
     * <p>评论若已被删除，这条 UPDATE 影响 0 行 —— 静默空操作，符合预期。</p>
     *
     * @param id    评论ID
     * @param delta 点赞数增量
     */
    void applyLikeCountDelta(@Param("id") String id, @Param("delta") long delta);

    /**
     * 判断某个机器人是否已经回复过某条评论（幂等：同一条评论被重复触发时不重复回复）
     * @param botId 机器人ID
     * @param replyId 被回复的评论ID
     * @return 已存在的回复条数
     */
    int countByBotAndReply(@Param("botId") String botId, @Param("replyId") String replyId);

    /**
     * 统计某个机器人一共在评论区留了多少条回复（删除机器人前提示用）
     * @param botId 机器人ID
     * @return 回复条数
     */
    int countByBotId(@Param("botId") String botId);

    /**
     * 删除某个机器人的全部回复（删除机器人时连带清理）
     * @param botId 机器人ID
     */
    void deleteByBotId(@Param("botId") String botId);
}
