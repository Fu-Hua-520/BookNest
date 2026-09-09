package com.fuhua.booknest.server.controller;

import com.fuhua.booknest.common.constant.MessageConstant;
import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.server.utils.AliOssUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

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
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
            "md", "txt", "pdf", "doc", "docx");

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 文件上传到 OSS
     * @param file 上传的文件
     * @return 文件访问 URL
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
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

            // objectName 形如 image/2026/09/10/{uuid}.jpg
            String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String objectName = type + "/" + datePath + "/" + UUID.randomUUID() + ext;

            String url = aliOssUtil.upload(file.getBytes(), objectName);
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
}
