package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.PostStatusConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.dto.PostPublishDTO;
import com.fuhua.booknest.pojo.dto.PostUpdateDTO;
import com.fuhua.booknest.pojo.entity.Book;
import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.PostTag;
import com.fuhua.booknest.pojo.entity.Tag;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.PostDetailVO;
import com.fuhua.booknest.pojo.vo.PostVO;
import com.fuhua.booknest.server.mapper.BookMapper;
import com.fuhua.booknest.server.mapper.CategoryMapper;
import com.fuhua.booknest.server.mapper.PostCollectMapper;
import com.fuhua.booknest.server.mapper.PostCommentLikeMapper;
import com.fuhua.booknest.server.mapper.PostCommentMapper;
import com.fuhua.booknest.server.mapper.PostLikeMapper;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.mapper.PostTagMapper;
import com.fuhua.booknest.server.mapper.TagMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.PostService;
import com.fuhua.booknest.server.utils.AliOssUtil;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class PostServiceImpl implements PostService {

    // OSS 对象名中的日期格式（yyyy/MM/dd）
    private static final DateTimeFormatter OSS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

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
    private PostTagMapper postTagMapper;
    @Autowired
    private TagMapper tagMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AliOssUtil aliOssUtil;

    @Override
    @Transactional
    public PostVO publishPost(PostPublishDTO dto) {
        // 校验必填字段（@Valid 之外再兜底）
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BaseException("标题不能为空");
        }
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BaseException("正文不能为空");
        }
        if (dto.getCategoryId() == null || dto.getCategoryId().trim().isEmpty()) {
            throw new BaseException("分类不能为空");
        }

        // 正文 Markdown 上传 OSS，objectName 存入 contentUrl
        String objectName = uploadContent(dto.getContent());

        LocalDateTime now = LocalDateTime.now();
        Post post = Post.builder()
                .id(UUID.randomUUID().toString())
                .userId(BaseContext.getCurrentId())
                .bookId(dto.getBookId())
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .contentUrl(objectName)
                .coverImage(dto.getCoverImage())
                .categoryId(dto.getCategoryId())
                .viewCount(0L)
                .likeCount(0)
                .commentCount(0)
                .collectCount(0)
                .status(PostStatusConstant.STATUS_PUBLISHED)
                .auditStatus(PostStatusConstant.AUDIT_PENDING)
                .isTop(0)
                .createTime(now)
                .updateTime(now)
                .publishTime(now)
                .build();
        postMapper.insert(post);

        // 保存标签关联
        savePostTags(post.getId(), dto.getTagIds());

        return toPostVO(post);
    }

    @Override
    public PostDetailVO getPostDetail(String postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }

        // 访问控制：非作者本人需同时满足已过审且已发布，否则不可见
        String currentId = BaseContext.getCurrentId();
        boolean isAuthor = currentId != null && currentId.equals(post.getUserId());
        if (!isAuthor) {
            if (!PostStatusConstant.AUDIT_APPROVED.equals(post.getAuditStatus())
                    || !PostStatusConstant.STATUS_PUBLISHED.equals(post.getStatus())) {
                throw new BaseException("帖子不存在或审核未通过");
            }
        }

        // 浏览量 +1，并同步内存值使返回结果准确
        postMapper.incrementViewCount(postId);
        post.setViewCount((post.getViewCount() == null ? 0L : post.getViewCount()) + 1);

        // 读取正文
        if (post.getContentUrl() == null || post.getContentUrl().isEmpty()) {
            throw new BaseException("正文加载失败");
        }
        String content;
        try {
            content = aliOssUtil.downloadAsString(post.getContentUrl());
        } catch (BaseException e) {
            log.warn("帖子正文加载失败，postId: {}", postId);
            throw new BaseException("正文加载失败");
        }

        PostVO base = toPostVO(post);
        return PostDetailVO.builder()
                .id(base.getId())
                .title(base.getTitle())
                .summary(base.getSummary())
                .coverImage(base.getCoverImage())
                .bookId(base.getBookId())
                .bookTitle(base.getBookTitle())
                .categoryId(base.getCategoryId())
                .categoryName(base.getCategoryName())
                .authorId(base.getAuthorId())
                .authorName(base.getAuthorName())
                .authorAvatar(base.getAuthorAvatar())
                .tags(base.getTags())
                .viewCount(base.getViewCount())
                .likeCount(base.getLikeCount())
                .commentCount(base.getCommentCount())
                .collectCount(base.getCollectCount())
                .isTop(base.getIsTop())
                .publishTime(base.getPublishTime())
                .content(content)
                .auditStatus(post.getAuditStatus())
                .auditReason(post.getAuditReason())
                .build();
    }

    @Override
    public List<PostVO> listPosts(String categoryId, String tagId, Integer auditStatus, Integer page, Integer pageSize) {
        // 用户端强制只返回已过审帖子，防止客户端传入 0/2 枚举未过审/被拒帖子；
        // 管理端审核走 admin 的 PostAdminService 直调 mapper，不受此影响
        auditStatus = PostStatusConstant.AUDIT_APPROVED;
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }

        PageHelper.startPage(page, pageSize);
        // status 传已发布，只展示已发布帖子
        List<Post> posts = postMapper.list(categoryId, tagId, auditStatus, PostStatusConstant.STATUS_PUBLISHED);

        List<PostVO> result = new ArrayList<>();
        for (Post post : posts) {
            result.add(toPostVO(post));
        }
        return result;
    }

    @Override
    @Transactional
    public void updatePost(String postId, PostUpdateDTO dto) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || !currentId.equals(post.getUserId())) {
            throw new BaseException("无权限操作");
        }

        // 正文重新上传 OSS，得到新 objectName
        String oldObjectName = post.getContentUrl();
        String newObjectName = uploadContent(dto.getContent());

        // 更新字段：编辑后回退审核状态为待审核，清空审核原因与审核时间，重新进入审核流程
        post.setTitle(dto.getTitle());
        post.setSummary(dto.getSummary());
        post.setContentUrl(newObjectName);
        post.setBookId(dto.getBookId());
        post.setCategoryId(dto.getCategoryId());
        post.setCoverImage(dto.getCoverImage());
        post.setAuditStatus(PostStatusConstant.AUDIT_PENDING);
        post.setAuditReason("");
        post.setAuditTime(null);
        post.setUpdateTime(LocalDateTime.now());
        // 先写库，成功后再清理旧正文，避免 DB 写失败导致正文丢失
        postMapper.update(post);

        // 清理旧正文（失败仅告警，不阻断编辑）
        if (oldObjectName != null && !oldObjectName.isEmpty() && !oldObjectName.equals(newObjectName)) {
            try {
                aliOssUtil.delete(oldObjectName);
            } catch (BaseException e) {
                log.warn("清理旧正文失败，objectName: {}", oldObjectName);
            }
        }

        // 重建标签关联（简化处理，不做使用次数递减）
        postTagMapper.deleteByPostId(postId);
        rebuildPostTags(postId, dto.getTagIds());
    }

    @Override
    @Transactional
    public void deletePost(String postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || !currentId.equals(post.getUserId())) {
            throw new BaseException("无权限操作");
        }

        // 清理 OSS 正文（失败不阻断删除）
        if (post.getContentUrl() != null && !post.getContentUrl().isEmpty()) {
            try {
                aliOssUtil.delete(post.getContentUrl());
            } catch (BaseException e) {
                log.warn("清理帖子正文失败，objectName: {}", post.getContentUrl());
            }
        }

        // 级联删除互动数据：评论点赞 → 评论 → 帖子点赞 → 收藏 → 标签关联 → 帖子
        postCommentLikeMapper.deleteByPostId(postId);
        postCommentMapper.deleteByPostId(postId);
        postLikeMapper.deleteByPostId(postId);
        postCollectMapper.deleteByPostId(postId);
        postTagMapper.deleteByPostId(postId);
        postMapper.deleteById(postId);
    }

    /**
     * 组装帖子卡片 VO（填充分类名、书名、作者信息、标签）
     */
    private PostVO toPostVO(Post post) {
        // 分类名
        String categoryName = null;
        if (post.getCategoryId() != null) {
            Category category = categoryMapper.selectById(post.getCategoryId());
            if (category != null) {
                categoryName = category.getName();
            }
        }

        // 书名
        String bookTitle = null;
        if (post.getBookId() != null && !post.getBookId().isEmpty()) {
            Book book = bookMapper.selectById(post.getBookId());
            if (book != null) {
                bookTitle = book.getTitle();
            }
        }

        // 作者信息
        String authorName = null;
        String authorAvatar = null;
        if (post.getUserId() != null) {
            User user = userMapper.getUserById(post.getUserId());
            if (user != null) {
                authorName = user.getUsername();
                authorAvatar = user.getAvatar();
            }
        }

        // 标签
        List<Tag> tags = new ArrayList<>();
        List<String> tagIds = postTagMapper.listTagIdsByPostId(post.getId());
        if (tagIds != null) {
            for (String tagId : tagIds) {
                Tag tag = tagMapper.selectById(tagId);
                if (tag != null) {
                    tags.add(tag);
                }
            }
        }

        return PostVO.builder()
                .id(post.getId())
                .title(post.getTitle())
                .summary(post.getSummary())
                .coverImage(post.getCoverImage())
                .bookId(post.getBookId())
                .bookTitle(bookTitle)
                .categoryId(post.getCategoryId())
                .categoryName(categoryName)
                .authorId(post.getUserId())
                .authorName(authorName)
                .authorAvatar(authorAvatar)
                .tags(tags)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .collectCount(post.getCollectCount())
                .isTop(post.getIsTop())
                .publishTime(post.getPublishTime())
                .build();
    }

    /**
     * 上传正文 Markdown 到 OSS，返回 objectName
     */
    private String uploadContent(String content) {
        String objectName = "post/" + LocalDateTime.now().format(OSS_DATE_FORMAT) + "/" + UUID.randomUUID() + ".md";
        aliOssUtil.upload(content.getBytes(StandardCharsets.UTF_8), objectName);
        return objectName;
    }

    /**
     * 保存帖子标签关联（并累加标签使用次数）
     */
    private void savePostTags(String postId, List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<PostTag> postTags = new ArrayList<>();
        for (String tagId : tagIds) {
            if (tagId == null || tagId.trim().isEmpty()) {
                continue;
            }
            postTags.add(PostTag.builder()
                    .id(UUID.randomUUID().toString())
                    .postId(postId)
                    .tagId(tagId)
                    .build());
            // 标签使用次数 +1
            tagMapper.incrementUseCount(tagId);
        }
        if (!postTags.isEmpty()) {
            postTagMapper.batchInsert(postTags);
        }
    }

    /**
     * 重建帖子标签关联（不做标签使用次数累加）
     */
    private void rebuildPostTags(String postId, List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        List<PostTag> postTags = new ArrayList<>();
        for (String tagId : tagIds) {
            if (tagId == null || tagId.trim().isEmpty()) {
                continue;
            }
            postTags.add(PostTag.builder()
                    .id(UUID.randomUUID().toString())
                    .postId(postId)
                    .tagId(tagId)
                    .build());
        }
        if (!postTags.isEmpty()) {
            postTagMapper.batchInsert(postTags);
        }
    }
}
