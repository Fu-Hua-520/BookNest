package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.RagHit;

import java.util.List;

/**
 * RAG 混合检索服务：HyDE 假设文档 + 向量召回 + 关键词召回 + RRF 融合。
 */
public interface RagRetrievalService {

    /**
     * 混合检索：对查询做 HyDE 增强后进行向量召回与关键词召回，RRF 融合去重后返回 TopK
     * @param query 用户查询
     * @param topK 返回条数上限
     * @return 融合后的命中列表（按得分降序）
     */
    List<RagHit> retrieve(String query, int topK);
}
