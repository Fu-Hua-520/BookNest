package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.PostStatusConstant;
import com.fuhua.booknest.common.constant.RagPayloadConstant;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.vo.RagRebuildVO;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.service.PostEmbeddingService;
import com.fuhua.booknest.server.utils.AliOssUtil;
import com.fuhua.booknest.server.utils.PostChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 帖子向量化入库服务实现。
 *
 * <h3>一句话说明</h3>
 * <p>把「点赞量前 N 的热门帖」的正文取回来 → 切块 → 交给 {@link VectorStore#add(List)}。
 * 文本转向量、写 Qdrant 都是 Spring AI 内部完成的，本类没有一行 embedding 代码。</p>
 *
 * <h3>重建流程（{@link #rebuildHotPosts()}）</h3>
 * <ol>
 *   <li>清空集合里的全部向量</li>
 *   <li>从 MySQL 取点赞量前 N 的已发布且过审的帖子</li>
 *   <li>逐篇下载正文、清洗分块、拼成 {@link Document} 后批量写入</li>
 * </ol>
 * <p>全过程是「先删光再重写」，所以既不用判重，也不用记录哪些帖子入过库。</p>
 */
@Slf4j
@Service
public class PostEmbeddingServiceImpl implements PostEmbeddingService {

    /** 单篇帖子最大分块数（防止异常长文档拖垮一次入库） */
    private static final int MAX_CHUNKS_PER_POST = 256;

    /** 每次调用 vectorStore.add 的文档上限，避免单次请求过大 */
    private static final int ADD_BATCH_SIZE = 64;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired
    private PostMapper postMapper;

    @Autowired(required = false)
    private AliOssUtil aliOssUtil;

    /** 候选范围：只索引点赞量最高的前多少篇帖子 */
    @Value("${booknest.rag.hot-top-n:100}")
    private int hotTopN;

    /** 批量入库时每处理 N 篇打印一次进度，避免刷屏 */
    @Value("${booknest.rag.batch-log-interval:50}")
    private int batchLogInterval;

    /** 幂等锁：定时任务与后台手动触发不会同时跑（并发重建会互相删数据） */
    private final AtomicBoolean rebuilding = new AtomicBoolean(false);

    // ============================================================
    //   定时全量重建
    // ============================================================

    /**
     * 周期性刷新索引：默认 30 天一次，周期与首次延迟均可配置。
     *
     * <p>{@code fixedDelayString} 表示「上一次结束到下一次开始」之间隔多久，
     * 写 ISO-8601 时长（{@code P30D}、{@code PT12H}、{@code PT30M}）或直接写毫秒数都可以。</p>
     */
    @Scheduled(
            initialDelayString = "${booknest.rag.refresh-initial-delay:PT2M}",
            fixedDelayString = "${booknest.rag.refresh-interval:P30D}"
    )
    public void refreshTask() {
        try {
            rebuildHotPosts();
        } catch (Exception ex) {
            // 定时任务里抛异常只会打日志然后中断后续调度，这里吞掉等下一个周期重试
            log.error("[RAG] 定时重建失败，等待下一个周期重试", ex);
        }
    }

    // ============================================================
    //   公开方法
    // ============================================================

    @Override
    public RagRebuildVO rebuildHotPosts() {
        if (vectorStore == null) {
            log.warn("[RAG] VectorStore 未装配（spring.ai.vectorstore.type 未设为 qdrant），跳过重建");
            return RagRebuildVO.builder().executed(false).message("向量库未启用").build();
        }
        if (!rebuilding.compareAndSet(false, true)) {
            log.info("[RAG] 上一次重建尚未结束，跳过本次触发");
            return RagRebuildVO.builder().executed(false).message("已有重建任务正在执行").build();
        }

        long start = System.currentTimeMillis();
        try {
            clearAll();

            List<String> hotPostIds = postMapper.listHotApprovedPostIds(hotTopN);
            if (hotPostIds == null || hotPostIds.isEmpty()) {
                log.info("[RAG] 没有符合条件的热门帖子（hotTopN={}），集合已清空", hotTopN);
                return RagRebuildVO.builder()
                        .executed(true)
                        .message("无热门帖子，集合已清空")
                        .candidateCount(0)
                        .costMs(System.currentTimeMillis() - start)
                        .build();
            }

            List<Post> posts = postMapper.selectByIds(hotPostIds);
            int[] stat = embedPosts(posts);

            long cost = System.currentTimeMillis() - start;
            log.info("[RAG] 全量重建完成 候选={} 入库={} 分块={} 耗时={}ms",
                    hotPostIds.size(), stat[0], stat[1], cost);
            return RagRebuildVO.builder()
                    .executed(true)
                    .message("重建完成")
                    .candidateCount(hotPostIds.size())
                    .postCount(stat[0])
                    .chunkCount(stat[1])
                    .costMs(cost)
                    .build();
        } finally {
            rebuilding.set(false);
        }
    }

    // ============================================================
    //   内部实现
    // ============================================================

    /**
     * 清空向量集合。
     * <p>用「带过滤条件的整批删除」而不是删掉整个集合：集合是 Spring AI 启动时
     * 按 embedding 维度创建好的，删掉之后还得按正确维度重建，容易出错。
     * 本项目写入的每一个 Document 都带 {@code chunkIdx >= 0}，用它能覆盖全部数据。</p>
     */
    private void clearAll() {
        vectorStore.delete(new FilterExpressionBuilder().gte(RagPayloadConstant.CHUNK_IDX, 0).build());
        log.info("[RAG] 已清空向量集合");
    }

    /**
     * 批量把帖子写进向量库。
     *
     * @return {@code [成功帖子数, 写入分块数]}
     */
    private int[] embedPosts(List<Post> posts) {
        int okPosts = 0;
        int totalChunks = 0;
        boolean logProgress = batchLogInterval > 0;
        List<Document> buffer = new ArrayList<>();

        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            try {
                List<Document> docs = buildDocuments(post);
                if (!docs.isEmpty()) {
                    buffer.addAll(docs);
                    okPosts++;
                    totalChunks += docs.size();
                }
            } catch (Exception ex) {
                // 单篇失败不影响整批
                log.warn("[RAG] 单篇入库失败 postId={}", post.getId(), ex);
            }

            if (buffer.size() >= ADD_BATCH_SIZE) {
                vectorStore.add(buffer);
                buffer = new ArrayList<>();
            }
            if (logProgress && ((i + 1) % batchLogInterval == 0 || i == posts.size() - 1)) {
                log.info("[RAG] 入库进度 {}/{} 成功={}", i + 1, posts.size(), okPosts);
            }
        }
        if (!buffer.isEmpty()) {
            vectorStore.add(buffer);
        }
        return new int[]{okPosts, totalChunks};
    }

    /** 把一篇帖子拆成若干带元数据的 Document；正文缺失或分块为空时返回空列表。 */
    private List<Document> buildDocuments(Post post) {
        String markdown = downloadContent(post);
        if (markdown == null || markdown.isEmpty()) {
            log.warn("[RAG] 正文为空，跳过 postId={}", post.getId());
            return Collections.emptyList();
        }

        List<String> chunks = PostChunker.chunkText(PostChunker.cleanMarkdown(markdown));
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }
        if (chunks.size() > MAX_CHUNKS_PER_POST) {
            log.warn("[RAG] 分块数过多 {}，截断到 {} postId={}",
                    chunks.size(), MAX_CHUNKS_PER_POST, post.getId());
            chunks = chunks.subList(0, MAX_CHUNKS_PER_POST);
        }

        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            docs.add(buildDocument(formatChunkId(post.getId(), i), chunks.get(i), post, i));
        }
        return docs;
    }

    /** 下载 OSS 上的正文；失败返回 null，由调用方按"跳过"处理。 */
    private String downloadContent(Post post) {
        if (aliOssUtil == null || post.getContentUrl() == null || post.getContentUrl().isEmpty()) {
            return null;
        }
        try {
            return aliOssUtil.downloadAsString(post.getContentUrl());
        } catch (Exception ex) {
            log.warn("[RAG] 下载正文失败 postId={}", post.getId(), ex);
            return null;
        }
    }

    private Document buildDocument(String id, String content, Post post, int chunkIdx) {
        Map<String, Object> md = new HashMap<>();
        md.put(RagPayloadConstant.POST_ID, post.getId());
        md.put(RagPayloadConstant.CHUNK_IDX, chunkIdx);
        md.put(RagPayloadConstant.TITLE, post.getTitle());
        md.put(RagPayloadConstant.LIKE_COUNT, post.getLikeCount() == null ? 0L : post.getLikeCount());
        md.put(RagPayloadConstant.STATUS, post.getStatus() == null
                ? PostStatusConstant.STATUS_PUBLISHED : post.getStatus());
        md.put(RagPayloadConstant.AUDIT_STATUS, post.getAuditStatus());
        md.put(RagPayloadConstant.CATEGORY_ID, post.getCategoryId());
        md.put(RagPayloadConstant.PUBLISH_TIME, post.getPublishTime() == null
                ? 0L : post.getPublishTime().toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli());
        // content 本身已在 getText()，这里冗余存一份便于在 Qdrant 后台直接看
        md.put(RagPayloadConstant.CONTENT, content);
        return new Document(id, content, md);
    }

    private static String formatChunkId(String postId, int chunkIdx) {
        return postId + "::" + chunkIdx;
    }
}
