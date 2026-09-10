package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.PostPublishDTO;
import com.fuhua.booknest.pojo.dto.PostUpdateDTO;
import com.fuhua.booknest.pojo.vo.PostDetailVO;
import com.fuhua.booknest.pojo.vo.PostVO;
import com.fuhua.booknest.server.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/post")
@Slf4j
@Tag(name = "帖子相关接口")
public class PostController {

    @Autowired
    private PostService postService;

    /**
     * 发布帖子
     * @param dto 发布信息
     * @return 帖子卡片 VO
     */
    @PostMapping
    @Operation(summary = "发布帖子")
    public Result<PostVO> publishPost(@RequestBody @Valid PostPublishDTO dto) {
        return Result.success(postService.publishPost(dto));
    }

    /**
     * 分页查询帖子列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询帖子列表")
    public Result<List<PostVO>> listPosts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String tagId,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(postService.listPosts(categoryId, tagId, auditStatus, page, pageSize));
    }

    /**
     * 查询帖子详情
     * @param id 帖子ID
     * @return 帖子详情 VO
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询帖子详情")
    public Result<PostDetailVO> getPostDetail(@PathVariable String id) {
        return Result.success(postService.getPostDetail(id));
    }

    /**
     * 更新帖子
     * @param id 帖子ID
     * @param dto 更新信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新帖子")
    public Result<String> updatePost(@PathVariable String id, @RequestBody @Valid PostUpdateDTO dto) {
        postService.updatePost(id, dto);
        return Result.success("更新成功");
    }

    /**
     * 删除帖子
     * @param id 帖子ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除帖子")
    public Result<String> deletePost(@PathVariable String id) {
        postService.deletePost(id);
        return Result.success("删除成功");
    }
}
