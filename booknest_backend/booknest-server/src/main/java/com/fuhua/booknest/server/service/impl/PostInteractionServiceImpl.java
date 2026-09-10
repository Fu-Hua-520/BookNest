package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.NotificationConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.dto.CommentPublishDTO;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.PostCollect;
import com.fuhua.booknest.pojo.entity.PostComment;
import com.fuhua.booknest.pojo.entity.PostCommentLike;
import com.fuhua.booknest.pojo.entity.PostLike;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.CommentVO;
import com.fuhua.booknest.server.mapper.PostCollectMapper;
import com.fuhua.booknest.server.mapper.PostCommentLikeMapper;
import com.fuhua.booknest.server.mapper.PostCommentMapper;
import com.fuhua.booknest.server.mapper.PostLikeMapper;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.mq.NotificationProducer;
import com.fuhua.booknest.server.service.PostInteractionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class PostInteractionServiceImpl implements PostInteractionService {

    @Autowired
    private PostMapper postMapper;
    @Autowired
    private PostCommentMapper postCommentMapper;
    @Autowired
    private PostCommentLikeMapper postCommentLikeMapper;
    @Autowired
    private PostLikeMapper postLikeMapper;
    @Autowired
    private PostCollectMapper postCollectMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NotificationProducer notificationProducer;

    @Override
    public CommentVO publishComment(String postId, CommentPublishDTO dto) {
        // 校验评论内容非空
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BaseException("评论内容不能为空");
        }
        // 校验帖子存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }

        // 楼中楼回复：规范化 replyId，并校验被回复评论存在
        String replyId = null;
        if (dto.getReplyId() != null && !dto.getReplyId().trim().isEmpty()) {
            replyId = dto.getReplyId().trim();
            if (postCommentMapper.selectById(replyId) == null) {
                throw new BaseException("回复的评论不存在");
            }
        }

        // 插入评论
        PostComment comment = PostComment.builder()
                .id(UUID.randomUUID().toString())
                .postId(postId)
                .userId(BaseContext.getCurrentId())
                .content(dto.getContent())
                .replyId(replyId)
                .likeCount(0)
                .createTime(LocalDateTime.now())
                .build();
        postCommentMapper.insert(comment);

        // 帖子评论数 +1
        postMapper.incrementCommentCount(postId);

        // 评论通知（自己评论自己的帖子不发通知）
        sendPostInteractionNotification(post, NotificationConstant.TYPE_COMMENT, "评论了你的帖子");

        return buildCommentVO(comment, null);
    }

    @Override
    public List<CommentVO> listComments(String postId) {
        // 校验帖子存在
        if (postMapper.selectById(postId) == null) {
            throw new BaseException("帖子不存在");
        }

        String currentId = BaseContext.getCurrentId();
        List<PostComment> comments = postCommentMapper.listByPostId(postId);
        List<CommentVO> result = new ArrayList<>();
        if (comments == null) {
            return result;
        }
        for (PostComment comment : comments) {
            result.add(buildCommentVO(comment, currentId));
        }
        return result;
    }

    @Override
    public void deleteComment(String commentId) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BaseException("评论不存在");
        }

        // 权限校验：评论作者本人或帖子作者本人可删除
        String currentId = BaseContext.getCurrentId();
        boolean isCommentAuthor = currentId != null && currentId.equals(comment.getUserId());
        boolean isPostAuthor = false;
        Post post = postMapper.selectById(comment.getPostId());
        if (post != null && currentId != null && currentId.equals(post.getUserId())) {
            isPostAuthor = true;
        }
        if (!isCommentAuthor && !isPostAuthor) {
            throw new BaseException("无权限操作");
        }

        postCommentMapper.deleteById(commentId);
        postMapper.decrementCommentCount(comment.getPostId());
    }

    @Override
    public Map<String, Object> toggleCommentLike(String commentId) {
        // 校验评论存在
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BaseException("评论不存在");
        }

        String userId = BaseContext.getCurrentId();
        PostCommentLike existing = postCommentLikeMapper.selectByCommentAndUser(commentId, userId);

        boolean liked;
        if (existing != null) {
            // 已点赞 → 取消点赞
            postCommentLikeMapper.deleteByCommentAndUser(commentId, userId);
            postCommentMapper.decrementLikeCount(commentId);
            liked = false;
        } else {
            // 未点赞 → 点赞
            postCommentLikeMapper.insert(PostCommentLike.builder()
                    .id(UUID.randomUUID().toString())
                    .commentId(commentId)
                    .userId(userId)
                    .build());
            postCommentMapper.incrementLikeCount(commentId);
            liked = true;
        }

        // 重新查询最新点赞数
        PostComment latest = postCommentMapper.selectById(commentId);
        Integer likeCount = latest == null || latest.getLikeCount() == null ? 0 : latest.getLikeCount();

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", likeCount);
        return result;
    }

    @Override
    public Map<String, Object> togglePostLike(String postId) {
        // 校验帖子存在
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }

        String userId = BaseContext.getCurrentId();
        PostLike existing = postLikeMapper.selectByPostAndUser(postId, userId);

        boolean liked;
        if (existing != null) {
            // 已点赞 → 取消点赞
            postLikeMapper.deleteByPostAndUser(postId, userId);
            postMapper.decrementLikeCount(postId);
            liked = false;
        } else {
            // 未点赞 → 点赞
            postLikeMapper.insert(PostLike.builder()
                    .id(UUID.randomUUID().toString())
                    .postId(postId)
                    .userId(userId)
                    .build());
            postMapper.incrementLikeCount(postId);
            liked = true;
        }

        // 本次为点赞（非取消）时发送点赞通知
        if (liked) {
            sendPostInteractionNotification(post, NotificationConstant.TYPE_LIKE, "赞了你的帖子");
        }

        // 重新查询帖子最新点赞数
        Post latest = postMapper.selectById(postId);
        Integer likeCount = latest == null || latest.getLikeCount() == null ? 0 : latest.getLikeCount();

        Map<String, Object> result = new HashMap<>();
        result.put("liked", liked);
        result.put("likeCount", likeCount);
        return result;
    }

    @Override
    public boolean isPostLiked(String postId) {
        return postLikeMapper.selectByPostAndUser(postId, BaseContext.getCurrentId()) != null;
    }

    @Override
    public Map<String, Object> togglePostCollect(String postId) {
        // 校验帖子存在
        if (postMapper.selectById(postId) == null) {
            throw new BaseException("帖子不存在");
        }

        String userId = BaseContext.getCurrentId();
        PostCollect existing = postCollectMapper.selectByPostAndUser(postId, userId);

        boolean collected;
        if (existing != null) {
            // 已收藏 → 取消收藏
            postCollectMapper.deleteByPostAndUser(postId, userId);
            postMapper.decrementCollectCount(postId);
            collected = false;
        } else {
            // 未收藏 → 收藏
            postCollectMapper.insert(PostCollect.builder()
                    .id(UUID.randomUUID().toString())
                    .postId(postId)
                    .userId(userId)
                    .build());
            postMapper.incrementCollectCount(postId);
            collected = true;
        }

        // 重新查询帖子最新收藏数
        Post latest = postMapper.selectById(postId);
        Integer collectCount = latest == null || latest.getCollectCount() == null ? 0 : latest.getCollectCount();

        Map<String, Object> result = new HashMap<>();
        result.put("collected", collected);
        result.put("collectCount", collectCount);
        return result;
    }

    @Override
    public boolean isPostCollected(String postId) {
        return postCollectMapper.selectByPostAndUser(postId, BaseContext.getCurrentId()) != null;
    }

    /**
     * 发送帖子互动通知（点赞/评论），自己对自己操作不发送
     * @param post 帖子实体
     * @param type 通知类型
     * @param verb 动作文案（如 "评论了你的帖子"）
     */
    private void sendPostInteractionNotification(Post post, String type, String verb) {
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || currentId.equals(post.getUserId())) {
            return;
        }
        User currentUser = userMapper.getUserById(currentId);
        String userName = (currentUser == null || currentUser.getUsername() == null) ? "用户" : currentUser.getUsername();
        String content = userName + " " + verb + "《" + post.getTitle() + "》";
        notificationProducer.sendNotification(post.getUserId(), type, content, post.getId());
    }

    /**
     * 组装评论 VO（填充评论者信息、被回复用户名与当前用户点赞状态）
     * @param comment 评论实体
     * @param currentId 当前用户ID（可空，未登录时点赞状态为 false）
     * @return 评论 VO
     */
    private CommentVO buildCommentVO(PostComment comment, String currentId) {
        // 评论者信息
        String userName = null;
        String userAvatar = null;
        if (comment.getUserId() != null) {
            User user = userMapper.getUserById(comment.getUserId());
            if (user != null) {
                userName = user.getUsername();
                userAvatar = user.getAvatar();
            }
        }

        // 被回复用户名（顶级评论为 null）
        String replyToUserName = null;
        if (comment.getReplyId() != null) {
            PostComment replyComment = postCommentMapper.selectById(comment.getReplyId());
            if (replyComment != null) {
                User replyUser = userMapper.getUserById(replyComment.getUserId());
                if (replyUser != null) {
                    replyToUserName = replyUser.getUsername();
                }
            }
        }

        // 当前用户是否已点赞该评论（未登录则 false）
        boolean liked = currentId != null
                && postCommentLikeMapper.selectByCommentAndUser(comment.getId(), currentId) != null;

        return CommentVO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .userName(userName)
                .userAvatar(userAvatar)
                .content(comment.getContent())
                .replyId(comment.getReplyId())
                .replyToUserName(replyToUserName)
                .likeCount(comment.getLikeCount())
                .createTime(comment.getCreateTime())
                .liked(liked)
                .build();
    }
}
