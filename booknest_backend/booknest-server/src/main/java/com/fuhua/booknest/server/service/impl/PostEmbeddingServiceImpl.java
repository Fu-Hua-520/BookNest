package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.PostEmbeddingService;
import com.fuhua.booknest.server.utils.AliOssUtil;
import com.fuhua.booknest.server.utils.PostChunker;
import com.fuhua.booknest.server.vectorstore.ZVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子向量化入库服务实现。
 *
 * <p>仅在 {@code booknest.zvector.enabled=true} 时装配（强依赖 {@link ZVectorStore}）。
 * 流程：读帖子 → 下载 Markdown 正文 → 清洗 + 中文分块 → 每块构建 {@link Document}
 * → {@link ZVectorStore#add(List)} 向量化入库 → Redis Set 标记已入库。OSS 正文缺失时
 * 降级用 title + summary 单块入库，不抛异常。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "booknest.zvector", name = "enabled", havingValue = "true")
public class PostEmbeddingServiceImpl implements PostEmbeddingService {

    @Autowired
    private PostMapper postMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AliOssUtil aliOssUtil;
    @Autowired
    private ZVectorStore zVectorStore;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void embedPost(String postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getContentUrl() == null || post.getContentUrl().isEmpty()) {
            log.warn("帖子不存在或正文为空，跳过向量化入库，postId: {}", postId);
            return;
        }

        // 作者名（作者可能已注销）
        String authorName = null;
        if (post.getUserId() != null) {
            User user = userMapper.getUserById(post.getUserId());
            if (user != null) {
                authorName = user.getUsername();
            }
        }

        // 下载 Markdown 正文并清洗分块；OSS 下载失败降级用 title + summary
        List<String> chunks;
        try {
            String markdown = aliOssUtil.downloadAsString(post.getContentUrl());
            String cleaned = PostChunker.cleanMarkdown(markdown);
            chunks = PostChunker.chunkText(cleaned);
            if (chunks.isEmpty()) {
                chunks = fallbackChunks(post);
            }
        } catch (BaseException e) {
            log.warn("帖子正文下载失败，降级用 title+summary 入库，postId: {}, 原因: {}", postId, e.getMessage());
            chunks = fallbackChunks(post);
        }

        // 每块构建 Document 并批量入库
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("postId", post.getId());
            metadata.put("title", post.getTitle());
            metadata.put("userId", post.getUserId());
            metadata.put("categoryId", post.getCategoryId());
            metadata.put("authorName", authorName);
            metadata.put("chunkIndex", i);
            documents.add(new Document(post.getId() + "_" + i, chunks.get(i), metadata));
        }
        zVectorStore.add(documents);

        // 标记已入库
        stringRedisTemplate.opsForSet().add(RedisConstant.POST_EMBEDDING_SET, postId);
        log.info("帖子向量化入库成功，postId: {}, 分块数: {}", postId, documents.size());
    }

    @Override
    public void embedPostAsync(String postId) {
        // 新线程异步执行，避免阻塞调用方（如审核通过后的主流程）
        new Thread(() -> embedPost(postId), "post-embed-" + postId).start();
    }

    @Override
    public int batchEmbedAllPosts() {
        List<Post> posts = postMapper.selectAllApproved();
        if (posts == null || posts.isEmpty()) {
            return 0;
        }
        int successCount = 0;
        for (Post post : posts) {
            try {
                embedPost(post.getId());
                successCount++;
            } catch (Exception e) {
                log.error("帖子向量化入库失败，postId: {}", post.getId(), e);
            }
        }
        log.info("批量向量化入库完成，共 {} 篇，成功 {} 篇", posts.size(), successCount);
        return successCount;
    }

    @Override
    public boolean isEmbedded(String postId) {
        Boolean member = stringRedisTemplate.opsForSet().isMember(RedisConstant.POST_EMBEDDING_SET, postId);
        return Boolean.TRUE.equals(member);
    }

    /**
     * 降级分块：正文缺失时用标题 + 摘要拼成单块
     */
    private List<String> fallbackChunks(Post post) {
        String text = (post.getTitle() == null ? "" : post.getTitle())
                + "\n" + (post.getSummary() == null ? "" : post.getSummary());
        List<String> chunks = PostChunker.chunkText(text);
        if (chunks.isEmpty()) {
            // 极端情况：标题与摘要均空，仍保证入库一条占位
            chunks = new ArrayList<>();
            chunks.add(post.getTitle() == null ? "" : post.getTitle());
        }
        return chunks;
    }
}
