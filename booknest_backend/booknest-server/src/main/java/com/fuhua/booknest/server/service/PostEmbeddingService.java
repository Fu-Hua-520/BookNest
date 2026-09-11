package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.RagRebuildVO;

/**
 * 帖子向量化入库服务（向量库：Qdrant）。
 *
 * <h3>策略：定期全量重建，而不是逐条增量维护</h3>
 * <p>每隔一个可配置的周期（默认 30 天），把整个向量集合清空，
 * 再重新拉取「点赞量前 N 的热门帖子」分块写进去。这样做的好处：</p>
 * <ul>
 *   <li>不需要判断「这篇帖子入过库没有」，没有重复数据堆积的问题</li>
 *   <li>不需要在帖子审核 / 上下架时挂钩子去增删单条向量</li>
 *   <li>热度排行榜变动（谁进/掉出前 N）在下一次刷新时自动生效</li>
 * </ul>
 *
 * <p>代价是：在两次重建之间，新发布的帖子暂时检索不到，最长延迟一个刷新周期。</p>
 */
public interface PostEmbeddingService {

    /**
     * 清空向量集合，并把当前「点赞量前 N 的热门帖」重新 embedding 写入。
     * <p>未启用向量库时返回 {@code executed=false}，调用方无需再判断 Bean 是否存在。</p>
     *
     * @return 本次重建的统计信息
     */
    RagRebuildVO rebuildHotPosts();
}
