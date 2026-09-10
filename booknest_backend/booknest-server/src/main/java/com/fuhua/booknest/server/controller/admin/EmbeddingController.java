package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.server.service.PostEmbeddingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 向量库重建
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
     * 批量将全部已过审帖子向量化入库
     */
    @PostMapping("/batch")
    @Operation(summary = "批量重建向量库")
    public Result<Integer> batchEmbed() {
        if (postEmbeddingService == null) {
            return Result.error("向量库未启用（booknest.zvector.enabled=false）");
        }
        return Result.success(postEmbeddingService.batchEmbedAllPosts());
    }
}
