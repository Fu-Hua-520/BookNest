package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.AiBotSaveDTO;
import com.fuhua.booknest.pojo.vo.AiBotBriefVO;
import com.fuhua.booknest.pojo.vo.AiBotVO;
import com.fuhua.booknest.server.service.AiBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评论区 AI 机器人（用户端）。
 *
 * <p>挂在独立的 {@code /bot} 前缀下，不走 {@code /category} 那套「GET 免令牌」的
 * 公开只读放行 —— 这里的每个接口都要知道「当前是谁」，游客进来必然 401。</p>
 */
@RestController
@RequestMapping("/bot")
@Slf4j
@Tag(name = "AI机器人")
public class AiBotController {

    @Autowired
    private AiBotService aiBotService;

    /**
     * 创建机器人（提交审核）
     */
    @PostMapping
    @Operation(summary = "创建 AI 机器人")
    public Result<AiBotVO> create(@Valid @RequestBody AiBotSaveDTO dto) {
        return Result.success(aiBotService.apply(dto));
    }

    /**
     * 我创建的机器人列表（含待审 / 已驳回 / 已通过）
     */
    @GetMapping("/mine")
    @Operation(summary = "我的 AI 机器人")
    public Result<List<AiBotVO>> mine() {
        return Result.success(aiBotService.listMine());
    }

    /**
     * 我创建的机器人详情
     */
    @GetMapping("/mine/{id}")
    @Operation(summary = "AI 机器人详情")
    public Result<AiBotVO> detail(@PathVariable String id) {
        return Result.success(aiBotService.getMine(id));
    }

    /**
     * 编辑机器人（配置改动会退回待审核）
     */
    @PutMapping("/{id}")
    @Operation(summary = "编辑 AI 机器人")
    public Result<AiBotVO> update(@PathVariable String id, @Valid @RequestBody AiBotSaveDTO dto) {
        return Result.success(aiBotService.update(id, dto));
    }

    /**
     * 删除机器人（连带删除它的历史回复）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除 AI 机器人")
    public Result<String> delete(@PathVariable String id) {
        aiBotService.delete(id);
        return Result.success("已删除");
    }

    /**
     * 启用 / 停用机器人
     */
    @PostMapping("/{id}/enabled")
    @Operation(summary = "启用或停用 AI 机器人")
    public Result<AiBotVO> setEnabled(@PathVariable String id, @RequestParam boolean enabled) {
        return Result.success(aiBotService.setEnabled(id, enabled));
    }

    /**
     * 全站可用机器人（已过审 + 未停用），供评论区 @ 选择器与机器人广场展示
     */
    @GetMapping("/available")
    @Operation(summary = "可用 AI 机器人列表")
    public Result<List<AiBotBriefVO>> available() {
        return Result.success(aiBotService.listAvailable());
    }
}
