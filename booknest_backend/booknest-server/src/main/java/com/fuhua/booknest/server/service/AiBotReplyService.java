package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.PostComment;

/**
 * AI 机器人评论区回复服务。
 *
 * <p>被 <b>评论落库并提交事务之后</b> 调用：解析评论里 @ 到的机器人，
 * 逐个丢进专用线程池去调用模型，再以「楼中楼回复」的形式把结果写回评论区。</p>
 *
 * <p>之所以要等事务提交：AI 回复要挂在触发它的那条评论下面（reply_id），
 * 若在事务提交前就把任务丢出去，异步线程可能先于提交执行，
 * 拿不到那条评论、也拿不到正确的帖子评论数。</p>
 */
public interface AiBotReplyService {

    /**
     * 评论发布后的触发入口。
     *
     * @param post          帖子（取标题 / 摘要 / 书吧名给模型当上下文）
     * @param comment       刚发布的评论（取正文解析 @，取 ID 作为回复的父级）
     * @param commenterName 评论者昵称
     */
    void onCommentPublished(Post post, PostComment comment, String commenterName);
}
