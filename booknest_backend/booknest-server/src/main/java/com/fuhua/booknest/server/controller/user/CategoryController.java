package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.BarVO;
import com.fuhua.booknest.pojo.vo.CategoryVO;
import com.fuhua.booknest.server.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/category")
@Slf4j
@Tag(name = "分类与书吧相关接口")
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

    /**
     * 书吧广场：全部已通过审核的书吧（按帖数降序）
     * <p>GET 落在公开只读前缀内，游客也能浏览广场。</p>
     * @return 书吧列表
     */
    @GetMapping("/bars")
    @Operation(summary = "查询书吧广场列表")
    public Result<List<BarVO>> listBars() {
        return Result.success(categoryService.listBars());
    }

    /**
     * 热门书吧：按成员数降序前 20（服务端缓存约一天，隔天自动重算）
     * <p>首页右侧「热门书吧」组件专用；GET 公开，游客也可见。</p>
     * @return 书吧列表（含 memberCount）
     */
    @GetMapping("/hot-bars")
    @Operation(summary = "查询热门书吧")
    public Result<List<BarVO>> listHotBars() {
        return Result.success(categoryService.listHotBars());
    }

    /**
     * 按吧名搜索可见书吧（顶部搜索栏「书吧」Tab）
     * @param keyword 关键词
     * @return 书吧列表
     */
    @GetMapping("/search")
    @Operation(summary = "搜索书吧")
    public Result<List<BarVO>> searchBars(@RequestParam(required = false) String keyword) {
        return Result.success(categoryService.searchBars(keyword));
    }

    /**
     * 我申请创建的书吧（含待审核 / 已驳回）
     * @return 书吧列表
     */
    @GetMapping("/my-applications")
    @Operation(summary = "查询我申请的书吧")
    public Result<List<BarVO>> listMyApplications() {
        return Result.success(categoryService.listMyApplications());
    }

    /**
     * 申请创建书吧（需登录，提交后进入管理员审核流程）
     * @param body 只读取 name / description / icon
     * @return 新书吧ID
     */
    @PostMapping("/apply")
    @Operation(summary = "申请创建书吧")
    public Result<String> applyBar(@RequestBody Map<String, String> body) {
        String name = body == null ? null : body.get("name");
        String description = body == null ? null : body.get("description");
        String icon = body == null ? null : body.get("icon");
        return Result.success(categoryService.applyBar(name, description, icon));
    }
}
