package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.entity.Tag;
import com.fuhua.booknest.server.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tag")
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签相关接口")
public class TagController {

    @Autowired
    private TagService tagService;

    /**
     * 查询全部标签
     * @return 标签列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询全部标签")
    public Result<List<Tag>> listTags() {
        return Result.success(tagService.listTags());
    }

    /**
     * 查询热门标签
     * @param limit 数量
     * @return 热门标签列表
     */
    @GetMapping("/hot")
    @Operation(summary = "查询热门标签")
    public Result<List<Tag>> getHotTags(@RequestParam(defaultValue = "20") Integer limit) {
        return Result.success(tagService.getHotTags(limit));
    }

    /**
     * 搜索标签
     * @param keyword 关键字
     * @return 标签列表
     */
    @GetMapping("/search")
    @Operation(summary = "搜索标签")
    public Result<List<Tag>> searchTags(@RequestParam String keyword) {
        return Result.success(tagService.searchTags(keyword));
    }
}
