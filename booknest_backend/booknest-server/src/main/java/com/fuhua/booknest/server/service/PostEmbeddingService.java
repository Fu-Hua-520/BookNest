package com.fuhua.booknest.server.service;

/**
 * 帖子向量化入库服务：将帖子正文清洗分块后 embedding 写入 zvector 向量库。
 */
public interface PostEmbeddingService {

    /**
     * 将单篇帖子向量化入库
     * @param postId 帖子ID
     */
    void embedPost(String postId);

    /**
     * 异步将单篇帖子向量化入库（新线程执行，不阻塞主流程）
     * @param postId 帖子ID
     */
    void embedPostAsync(String postId);

    /**
     * 批量将全部已过审帖子向量化入库
     * @return 成功入库的帖子数量
     */
    int batchEmbedAllPosts();

    /**
     * 判断帖子是否已向量化入库
     * @param postId 帖子ID
     * @return 是否已入库
     */
    boolean isEmbedded(String postId);
}
