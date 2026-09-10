package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RAG 混合检索命中结果 VO（单条命中）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagHit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 帖子 ID */
    private String postId;

    /** 帖子标题 */
    private String title;

    /** 命中的文本片段（向量路为分块内容，关键词路为摘要） */
    private String content;

    /** 作者名 */
    private String authorName;

    /** 融合得分（RRF） */
    private Double score;

    /** 召回来源：vector / keyword */
    private String source;
}
