package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.AiBotVO;
import com.fuhua.booknest.server.service.AiBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 评论区 AI 机器人审核。
 *
 * <p>机器人要调用用户自己填的第三方 API Key，一旦被用来发违规内容，
 * 平台是要担责的，所以创建与每次配置改动都必须过审。</p>
 */
@RestController
@RequestMapping("/admin/bot")
@Slf4j
@Tag(name = "管理后台-AI机器人审核")
public class AdminBotController {

    @Autowired
    private AiBotService aiBotService;

    /**
     * 机器人列表（默认查待审核的；auditStatus 传空则不筛状态）
     */
    @GetMapping("/list")
    @Operation(summary = "查询 AI 机器人")
    public Result<List<AiBotVO>> list(
            @RequestParam(required = false, defaultValue = "0") Integer auditStatus) {
        return Result.success(aiBotService.listForAudit(auditStatus));
    }

    /**
     * 审批机器人（通过 / 驳回）
     * <p>驳回必须带 rejectReason。审核通过后该机器人立即可被全站 @ 触发。</p>
     */
    @PutMapping("/{id}/audit")
    @Operation(summary = "审批 AI 机器人")
    public Result<String> audit(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // 与书吧审核保持同一口径：approve 缺失或非布尔一律按「驳回」，
        // 避免 JSON 写错时把申请默默通过
        Object approveFlag = body == null ? null : body.get("approve");
        boolean approve = Boolean.TRUE.equals(approveFlag)
                || "true".equalsIgnoreCase(String.valueOf(approveFlag));
        Object reason = body == null ? null : body.get("rejectReason");
        aiBotService.audit(id, approve, reason == null ? null : String.valueOf(reason));
        return Result.success(approve ? "已通过" : "已驳回");
    }

    /**
     * 删除机器人（连带删除它在评论区留下的回复）
     * <p>不校验归属：机器人可能因 Key 失效、提示词违规、创建者跑路而必须下架。</p>
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除 AI 机器人")
    public Result<String> delete(@PathVariable String id) {
        aiBotService.deleteByAdmin(id);
        return Result.success("已删除");
    }
}
