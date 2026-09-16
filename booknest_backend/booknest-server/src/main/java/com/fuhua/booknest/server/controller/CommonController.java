package com.fuhua.booknest.server.controller;

import com.fuhua.booknest.common.constant.MessageConstant;
import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.server.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Set;

/**
 * 通用接口（文件上传等），顶层 controller 包，不走用户/管理端鉴权
 */
@RestController
@RequestMapping("/common")
@Slf4j
@Tag(name = "通用接口")
public class CommonController {

    // 允许上传的扩展名白名单
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "md", "txt", "pdf", "doc", "docx");

    @Autowired
    private AliOssUtil aliOssUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 单用户每分钟上传次数上限，对应 booknest.upload.rate-limit-per-minute */
    @Value("${booknest.upload.rate-limit-per-minute:20}")
    private int uploadRateLimitPerMinute;

    /**
     * 文件上传到 OSS
     * @param file 上传的文件
     * @return 文件访问 URL
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        // 上传必须登录：/common/** 已纳入用户端 JWT 拦截器，这里再兜一次底，
        // 防止日后有人把 /common/** 从拦截器里摘掉，使上传接口退化成匿名可用
        String userId = BaseContext.getCurrentId();
        if (userId == null || userId.isEmpty()) {
            return Result.error(MessageConstant.UPLOAD_UNAUTHORIZED);
        }

        // 频率限制：不限的话，任意一个登录账号都能把 OSS 流量和存储刷爆
        if (!checkUploadRate(userId)) {
            log.warn("用户 {} 上传过于频繁，已拒绝", userId);
            return Result.error(MessageConstant.UPLOAD_TOO_FREQUENT);
        }

        if (file == null || file.isEmpty()) {
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }

        // 提取扩展名（转小写），无扩展名时安全处理为空串，不越界
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        // 扩展名白名单校验
        if (!isAllowedExtension(ext)) {
            return Result.error("不支持的文件类型");
        }

        try {
            // 根据 content-type 判断文件类型：image/* 归入 image 目录，其余归入 file 目录
            String contentType = file.getContentType();
            String type = (contentType != null && contentType.startsWith("image/")) ? "image" : "file";

            // 只读取一次字节：既要用来算内容摘要，也要用来上传
            byte[] bytes = file.getBytes();

            // objectName 用「文件内容 MD5」而不是随机 UUID —— 内容寻址存储。
            // 这样同一份文件永远对应同一个对象名：同一张图传多次、或不同用户传同一张图，
            // OSS 里都只存一份，重复上传时 AliOssUtil 直接复用，不会再堆出孤儿副本。
            // 刻意不再按日期分目录：否则同一张图跨天上传仍会存成两份，去重就白做了。
            // MD5 在此仅用于内容去重，不涉及安全场景，碰撞风险可忽略。
            String contentHash = DigestUtils.md5DigestAsHex(bytes);
            String objectName = type + "/" + contentHash + ext;

            String url = aliOssUtil.upload(bytes, objectName);
            return Result.success(url);
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }

    /**
     * 判断扩展名是否在白名单内
     * @param ext 扩展名（含前导点，已转小写）
     * @return 是否允许上传
     */
    private boolean isAllowedExtension(String ext) {
        if (ext == null || ext.isEmpty()) {
            return false;
        }
        String lower = ext.startsWith(".") ? ext.substring(1) : ext;
        return ALLOWED_EXTENSIONS.contains(lower);
    }

    /**
     * 校验上传频率：单用户每个窗口（1 分钟）内不超过 uploadRateLimitPerMinute 次。
     * 窗口从本轮第一次上传开始计时，而非自然分钟对齐，避免跨分钟边界时突发量翻倍。
     *
     * @param userId 当前登录用户 ID
     * @return true 表示允许本次上传
     */
    private boolean checkUploadRate(String userId) {
        String key = RedisConstant.RATE_LIMIT + userId + ":upload";
        Long count;
        try {
            count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // 本窗口第一次上传，起一个 1 分钟的有效期
                stringRedisTemplate.expire(key, Duration.ofSeconds(RedisConstant.EXPIRE_1_MINUTE));
            }
        } catch (Exception e) {
            // 限流属于保护性功能，Redis 抖动时选择放行，不让它把上传主功能拖挂
            log.warn("上传限流计数失败（Redis 异常），本次放行: {}", e.getMessage());
            return true;
        }
        // count 为 null 是 Redis 返回值异常，同样放行
        return count == null || count <= uploadRateLimitPerMinute;
    }
}
