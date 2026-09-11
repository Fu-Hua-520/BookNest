package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.RagPayloadConstant;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.vo.RagHit;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.service.RagRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索：在一批指定帖子的内容里，找出与问题最相关的几段。
 *
 * <h3>检索范围：点赞量前 N 的热门帖</h3>
 * <p>不是全站检索。先从 MySQL 查出「已发布且已审核、按点赞量倒序」的前 {@code N} 篇帖子 ID，
 * 只在这些帖子的向量里找答案。</p>
 *
 * <h3>为什么是 SearchRequest 而不是直接调 方法</h3>
 * <p>{@link SearchRequest} 就是 Spring AI 封装的「一次检索的全部条件」：
 * 问句原文、要几条、相似度下限、过滤条件。框架内部会先用 {@code EmbeddingModel}
 * 把问句转成向量，再把整个请求交给 Qdrant。</p>
 *
 * <h3>为什么要把热门 ID 列表放进 filter</h3>
 * <p>如果不传过滤条件，Qdrant 会在全部帖子里找最相似的。传了
 * {@code postId in [热门100个ID]} 之后，Qdrant 在遍历索引时就顺带检查条件，
 * 不符合的候选直接跳过，不会返回给我们再筛。</p>
 *
 * <h3>为什么要去重</h3>
 * <p>一篇帖子被切成多个块，可能好几块都和问题相关。这里按帖子只保留
 * 最靠前的那一块，让最终结果是「N 篇相关帖子」而不是「N 段同一篇的内容」。</p>
 */
@Slf4j
@Service
public class RagRetrievalServiceImpl implements RagRetrievalService {

    /** 相似度下限（0~1，越大越严格）。低于此值的结果不采纳 */
    private static final double SIMILARITY_THRESHOLD = 0.4;

    /**
     * 一篇帖子切成多个块，多召回一些才能在「按帖子去重」之后还凑够目标条数。
     * 例如要 5 条结果，实际向 Qdrant 要 5×8=40 条候选。
     */
    private static final int OVER_FETCH_FACTOR = 8;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired
    private PostMapper postMapper;

    /** 候选范围大小：取点赞量最高的前多少篇帖子 */
    @Value("${booknest.rag.hot-top-n:100}")
    private int hotTopN;

    @Override
    public List<RagHit> retrieve(String query, int topK) {
        if (vectorStore == null || query == null || query.isEmpty() || topK <= 0) {
            return Collections.emptyList();
        }

        // 1. 确定检索范围：点赞量最高的前 N 篇已发布帖子
        List<String> hotPostIds = postMapper.listHotApprovedPostIds(hotTopN);
        if (hotPostIds == null || hotPostIds.isEmpty()) {
            log.info("[RAG] 热门帖子为空，跳过检索");
            return Collections.emptyList();
        }

        // 2. 拼检索条件：在这些帖子里找，多召回一些用于去重
        Expression onlyHotPosts = new FilterExpressionBuilder()
                .in(RagPayloadConstant.POST_ID, hotPostIds)
                .build();
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK * OVER_FETCH_FACTOR)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .filterExpression(onlyHotPosts)
                .build();

        // 3. 交给 Spring AI 执行（问句转向量、查 Qdrant、转回 Document 都在这里完成）
        List<Document> docs;
        try {
            docs = vectorStore.similaritySearch(request);
        } catch (Exception ex) {
            log.warn("[RAG] 检索失败，降级为不使用外挂知识: {}", ex.getMessage());
            return Collections.emptyList();
        }
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 按帖子去重，每篇只留相似度最高的一块（Spring AI 已按相似度倒序返回）
        Map<String, Document> bestChunkPerPost = new LinkedHashMap<>();
        for (Document doc : docs) {
            Object postId = doc.getMetadata() == null
                    ? null : doc.getMetadata().get(RagPayloadConstant.POST_ID);
            if (postId != null) {
                bestChunkPerPost.putIfAbsent(postId.toString(), doc);
            }
        }

        List<Document> picked = new ArrayList<>(bestChunkPerPost.values());
        if (picked.size() > topK) {
            picked = picked.subList(0, topK);
        }

        // 5. 回查标题等展示信息（不在向量里存这些冗余字段，取现阶段一次性补全）
        List<String> pickedIds = new ArrayList<>(bestChunkPerPost.keySet());
        List<String> topIds = pickedIds.size() > topK ? pickedIds.subList(0, topK) : pickedIds;
        Map<String, Post> postMap = new HashMap<>();
        for (Post p : postMapper.selectByIds(new ArrayList<>(topIds))) {
            postMap.put(p.getId(), p);
        }

        List<RagHit> hits = new ArrayList<>(picked.size());
        for (Document doc : picked) {
            hits.add(toRagHit(doc, postMap));
        }
        log.info("[RAG] 检索完成 候选={}篇 召回块={} 去重后={}", hotPostIds.size(), docs.size(), hits.size());
        return hits;
    }

    private RagHit toRagHit(Document doc, Map<String, Post> postMap) {
        String postId = String.valueOf(doc.getMetadata().get(RagPayloadConstant.POST_ID));
        Post post = postMap.get(postId);
        return RagHit.builder()
                .postId(postId)
                .title(post != null ? post.getTitle()
                        : String.valueOf(doc.getMetadata().get(RagPayloadConstant.TITLE)))
                .authorName(null)
                .content(doc.getText())
                .source("qdrant")
                .score(extractScore(doc))
                .build();
    }

    /**
     * 取出 Qdrant 返回的相似度。
     * <p>Spring AI 各 VectorStore 放置分数的位置不统一：Qdrant 实现放在
     * metadata 的 {@code distance}（余弦距离）里，相似度 = 1 - 距离。</p>
     */
    private double extractScore(Document doc) {
        Object distance = doc.getMetadata() == null ? null : doc.getMetadata().get("distance");
        if (distance instanceof Number n) {
            return 1.0 - n.doubleValue();
        }
        // 拿不到就退化为阈值中值，排序会受影响但结果集不受影响
        return SIMILARITY_THRESHOLD;
    }
}
