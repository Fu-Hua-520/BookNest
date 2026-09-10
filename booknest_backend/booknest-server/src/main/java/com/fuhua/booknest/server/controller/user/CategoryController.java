package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.CategoryVO;
import com.fuhua.booknest.server.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/category")
@Slf4j
@Tag(name = "分类相关接口")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 查询分类树
     * @return 分类树
     */
    @GetMapping("/tree")
    @Operation(summary = "查询分类树")
    public Result<List<CategoryVO>> getCategoryTree() {
        return Result.success(categoryService.getCategoryTree());
    }
}
