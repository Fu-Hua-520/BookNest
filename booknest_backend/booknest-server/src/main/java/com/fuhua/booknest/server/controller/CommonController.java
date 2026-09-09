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
import java.util.UUID;

/**
 * 通用接口（文件上传等），顶层 controller 包，不走用户/管理端鉴权
 */
@RestController
@RequestMapping("/common")
@Slf4j
@Tag(name = "通用接口")
public class CommonController {

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
        try {
            String originalFilename = file.getOriginalFilename();
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

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
}
