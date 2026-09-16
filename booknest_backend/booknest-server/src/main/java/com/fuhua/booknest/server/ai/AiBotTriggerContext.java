package com.fuhua.booknest.server.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 机器人回复任务的上下文。
 *
 * <p>评论区发评论的线程持有 {@code BaseContext}（ThreadLocal），而回复任务跑在
 * 线程池里读不到它，所以在投递任务之前必须把需要的信息都「摘」到这个对象里，
 * 异步线程只认它、不再碰任何 ThreadLocal。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBotTriggerContext implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 帖子ID */
    private String postId;
    /** 帖子标题 */
    private String postTitle;
    /** 帖子摘要（可为空） */
    private String postSummary;
    /**
     * 帖子正文在 OSS 上的对象名（即 {@code post.content_url}）。
     *
     * <p>只带对象名、不带正文本身：正文是 OSS 上的 Markdown 文件，
     * 下载有一定耗时，绝不能发生在发评论的请求线程上（afterCommit 也在请求线程里）。
     * 真正下载交给线程池里的 {@code loadPostContent}。</p>
     */
    private String postContentUrl;
    /** 帖子所在书吧名（可为空） */
    private String barName;
    /** 触发评论的ID（AI 回复会挂在它下面，做成楼中楼） */
    private String triggerCommentId;
    /** 触发评论的正文 */
    private String commentContent;
    /** 评论者用户ID（通知接收人） */
    private String commenterId;
    /** 评论者昵称 */
    private String commenterName;
}
