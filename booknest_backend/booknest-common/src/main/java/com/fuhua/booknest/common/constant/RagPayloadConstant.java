package com.fuhua.booknest.common.constant;

/**
 * BookNest RAG（检索增强生成）模块的 payload 字段名常量。
 * <p>统一存放 Qdrant Document 的 metadata（payload）字段名，避免业务代码散落魔法字符串
 * 以及日后字段重命名时的回归错误。</p>
 *
 * <p>这些 key 必须与 {@code PostEmbeddingServiceImpl#buildDocument} 中写入的 metadata 一致，
 * 与 {@code RagRetrievalServiceImpl} 中读取的 metadata 一致，详见 RAG 模块。</p>
 *
 * <p><b>注意：</b>改任何 key 都意味着要把现有 Qdrant 集合全量重建（payload 字段命名后不能识别）。</p>
 */
public final class RagPayloadConstant {

    /** 帖子 ID（字符串，与 Post.id 一致，用于按帖子过滤/分组） */
    public static final String POST_ID = "postId";

    /** 分块序号（整数，从 0 开始） */
    public static final String CHUNK_IDX = "chunkIdx";

    /** 帖子标题（冗余存 payload，便于展示） */
    public static final String TITLE = "title";

    /** 点赞量（long，用于业务层 top-N 排序，可选） */
    public static final String LIKE_COUNT = "likeCount";

    /** 帖子发布状态（{@link PostStatusConstant#STATUS_DRAFT/STATUS_PUBLISHED/STATUS_OFFLINE}） */
    public static final String STATUS = "status";

    /** 帖子审核状态（{@link PostStatusConstant#AUDIT_PENDING/AUDIT_APPROVED/AUDIT_REJECTED}） */
    public static final String AUDIT_STATUS = "auditStatus";

    /** 一级分类 ID（string） */
    public static final String CATEGORY_ID = "categoryId";

    /** 发布时间戳（long，毫秒） */
    public static final String PUBLISH_TIME = "publishTime";

    /** 文本内容（写入 payload，与 Document text 字段冗余保存，方便导出/调试） */
    public static final String CONTENT = "content";

    private RagPayloadConstant() {
        // 常量类禁止实例化
    }
}
