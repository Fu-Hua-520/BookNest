package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.RagHit;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.RagRetrievalService;
import com.fuhua.booknest.server.vectorstore.ZVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 混合检索服务实现。
 *
 * <p>仅在 {@code booknest.zvector.enabled=true} 时装配（强依赖 {@link ZVectorStore}）。
 * 流程：HyDE 假设文档 → 向量召回 → 关键词召回 → RRF 融合去重 → TopK。</p>
 */
@Service
@Slf4j
@ConditionalOnProperty(prefix = "booknest.zvector", name = "enabled", havingValue = "true")
public class RagRetrievalServiceImpl implements RagRetrievalService {

    /** RRF 融合公式中的常数 k（rank 从 1 起，score = Σ 1/(rank + 60)） */
    private static final double RRF_K = 60.0;

    /** 向量召回条数 */
    private static final int VECTOR_TOP_K = 8;

    /** 关键词召回条数 */
    private static final int KEYWORD_TOP_K = 5;

    /** 向量召回相似度阈值 */
    private static final double SIMILARITY_THRESHOLD = 0.4;

    /** HyDE 假设文档生成提示词 */
    private static final String HYDE_PROMPT =
            "你是书虫社区 BookNest 的一名资深读者。请以论坛帖子作者的口吻，围绕下面问题写一段书籍推荐或读书观点分享片段，约150字，直接输出正文，不要标题与任何解释：\n问题：";

    /** 关键词召回去除的常见意图词（去掉后若为空则回退原查询） */
    private static final String[] INTENT_WORDS = {
            "推荐", "有哪些", "介绍一下", "帮我找", "什么书", "关于", "有没有", "请", "帮忙",
            "我想", "找一", "书籍", "书", "求"
    };

    @Autowired
    private ChatModel chatModel;
    @Autowired
    private ZVectorStore zVectorStore;
    @Autowired
    private PostMapper postMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public List<RagHit> retrieve(String query, int topK) {
        // 1. HyDE：生成假设文档，增强向量检索的语义表达能力
        String hydeText;
        try {
            hydeText = chatModel.call(HYDE_PROMPT + query);
        } catch (Exception e) {
            log.warn("HyDE 生成失败，回退使用原始查询，原因: {}", e.getMessage());
            hydeText = query;
        }

        // 2. 向量召回
        List<Document> vectorDocs;
        try {
            vectorDocs = zVectorStore.similaritySearch(SearchRequest.builder()
                    .query(hydeText)
                    .topK(VECTOR_TOP_K)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .build());
        } catch (Exception e) {
            log.warn("向量召回失败，跳过向量路，原因: {}", e.getMessage());
            vectorDocs = new ArrayList<>();
        }

        // 3. 关键词召回
        String keyword = extractKeyword(query);
        List<Post> keywordPosts;
        try {
            keywordPosts = postMapper.searchByKeyword(keyword, KEYWORD_TOP_K);
        } catch (Exception e) {
            log.warn("关键词召回失败，跳过关键词路，原因: {}", e.getMessage());
            keywordPosts = new ArrayList<>();
        }

        // 4. RRF 融合 + 按 postId 去重（向量路优先，保留向量路 content 片段）
        Map<String, RagHit> merged = new LinkedHashMap<>();

        // 向量路：rank 从 1 起
        for (int i = 0; i < vectorDocs.size(); i++) {
            RagHit hit = toVectorHit(vectorDocs.get(i));
            if (hit.getPostId() == null) {
                continue;
            }
            hit.setScore(rrfScore(i + 1));
            merged.put(hit.getPostId(), hit);
        }

        // 关键词路：已存在则累加得分（保留向量路 content）
        for (int i = 0; i < keywordPosts.size(); i++) {
            RagHit hit = toKeywordHit(keywordPosts.get(i));
            if (hit.getPostId() == null) {
                continue;
            }
            double score = rrfScore(i + 1);
            RagHit existing = merged.get(hit.getPostId());
            if (existing != null) {
                existing.setScore(existing.getScore() + score);
            } else {
                hit.setScore(score);
                merged.put(hit.getPostId(), hit);
            }
        }

        // 5. 按得分降序取前 topK
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(RagHit::getScore).reversed())
                .limit(topK)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 提取关键词：去除常见意图词；去除后为空则回退原始查询
     * @param query 原始查询
     * @return 关键词
     */
    private String extractKeyword(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }
        String keyword = query;
        for (String word : INTENT_WORDS) {
            keyword = keyword.replace(word, "");
        }
        keyword = keyword.replaceAll("[\\s，。！？、,.:;\"'《》()（）\\[\\]【】]+", " ").trim();
        return keyword.isEmpty() ? query.trim() : keyword;
    }

    /**
     * 将向量召回 Document 转为 RagHit（元数据中取 postId/title/authorName，content 取文本）
     * @param doc 向量召回文档
     * @return 命中 VO
     */
    private RagHit toVectorHit(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        return RagHit.builder()
                .postId(asString(metadata.get("postId")))
                .title(asString(metadata.get("title")))
                .authorName(asString(metadata.get("authorName")))
                .content(doc.getText())
                .source("vector")
                .build();
    }

    /**
     * 将关键词召回 Post 转为 RagHit（content 用摘要）
     * @param post 帖子实体
     * @return 命中 VO
     */
    private RagHit toKeywordHit(Post post) {
        String authorName = null;
        if (post.getUserId() != null) {
            User user = userMapper.getUserById(post.getUserId());
            if (user != null) {
                authorName = user.getUsername();
            }
        }
        return RagHit.builder()
                .postId(post.getId())
                .title(post.getTitle())
                .authorName(authorName)
                .content(post.getSummary())
                .source("keyword")
                .build();
    }

    /**
     * RRF 得分：1 / (rank + 60)
     * @param rank 排名（从 1 起）
     * @return 融合得分
     */
    private double rrfScore(int rank) {
        return 1.0 / (rank + RRF_K);
    }

    /**
     * 安全转换为字符串（null 转 null）
     * @param obj 元数据值
     * @return 字符串值
     */
    private String asString(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
