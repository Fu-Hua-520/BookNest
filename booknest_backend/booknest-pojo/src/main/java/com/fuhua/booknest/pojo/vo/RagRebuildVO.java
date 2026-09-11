package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RAG 索引全量重建结果 VO（返回给管理端展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRebuildVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 是否真正执行了重建（false 表示未启用向量库 / 已有任务在执行） */
    private boolean executed;

    /** 未执行或执行结果的说明 */
    private String message;

    /** MySQL 里查到的热门候选帖子数 */
    private int candidateCount;

    /** 实际成功写入向量的帖子数 */
    private int postCount;

    /** 实际写入的分块（向量）数 */
    private int chunkCount;

    /** 本次重建耗时（毫秒） */
    private long costMs;
}
