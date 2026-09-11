package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.RagRebuildVO;
import com.fuhua.booknest.server.service.PostEmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 向量库索引重建
 *
 * <p>除了每月一次的自动刷新，这里提供手动触发入口：适合改了分块策略、
 * 换 embedding 模型，或者想立刻让新热门帖进索引时使用。</p>
 */
@RestController
@RequestMapping("/admin/embedding")
@Slf4j
@Tag(name = "管理后台-向量库")
public class EmbeddingController {

    /**
     * 向量库未启用时该 Bean 不存在，注入为 null
     */
    @Autowired(required = false)
    private PostEmbeddingService postEmbeddingService;

    /**
     * 手动重建：清空集合后重新写入点赞量前 N 的热门帖
     */
    @PostMapping("/rebuild")
    @Operation(summary = "手动重建 RAG 索引")
    public Result<RagRebuildVO> rebuild() {
        if (postEmbeddingService == null) {
            return Result.error("向量库未启用（spring.ai.vectorstore.type 未设为 qdrant）");
        }
        RagRebuildVO vo = postEmbeddingService.rebuildHotPosts();
        if (!vo.isExecuted()) {
            return Result.error(vo.getMessage());
        }
        return Result.success(vo);
    }
}
