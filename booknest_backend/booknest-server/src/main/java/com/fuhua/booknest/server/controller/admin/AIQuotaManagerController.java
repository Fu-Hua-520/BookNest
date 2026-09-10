package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 - AI 提问额度管理
 */
@RestController
@RequestMapping("/admin/ai-quota")
@Slf4j
@Tag(name = "管理后台-AI额度")
public class AIQuotaManagerController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询用户 AI 额度（无记录时返回默认免费额度）
     */
    @GetMapping("/{userId}")
    @Operation(summary = "查询AI额度")
    public Result<Integer> getQuota(@PathVariable String userId) {
        String value = stringRedisTemplate.opsForValue().get(RedisConstant.AI_QUOTA + userId);
        if (value == null) {
            return Result.success(RedisConstant.AI_QUOTA_FREE);
        }
        try {
            return Result.success(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            log.warn("AI额度值非法，userId: {}, value: {}", userId, value);
            return Result.success(RedisConstant.AI_QUOTA_FREE);
        }
    }

    /**
     * 设置用户 AI 额度
     */
    @PostMapping("/{userId}")
    @Operation(summary = "设置AI额度")
    public Result<String> setQuota(@PathVariable String userId, @RequestParam Integer quota) {
        stringRedisTemplate.opsForValue().set(RedisConstant.AI_QUOTA + userId, String.valueOf(quota));
        return Result.success("设置成功");
    }

    /**
     * 重置用户 AI 额度（删除记录，恢复为默认值）
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "重置AI额度")
    public Result<String> deleteQuota(@PathVariable String userId) {
        stringRedisTemplate.delete(RedisConstant.AI_QUOTA + userId);
        return Result.success("重置成功");
    }
}
