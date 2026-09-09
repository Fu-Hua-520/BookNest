package com.fuhua.booknest.server.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.OSSObject;
import com.fuhua.booknest.common.exception.BaseException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 阿里云 OSS 工具类，封装文件上传、删除、下载为字符串三个操作。
 * 每个方法内部自行创建 OSSClient，并在 finally 中关闭，避免连接泄漏。
 * 仅暴露 getter，避免 toString/equals/hashCode 泄露 accessKeySecret。
 */
@Getter
@AllArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    /**
     * 上传文件到 OSS
     * @param bytes 文件字节内容
     * @param objectName 对象名（含目录路径）
     * @return 文件可访问的 URL
     */
    public String upload(byte[] bytes, String objectName) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
        } catch (OSSException oe) {
            // 服务端异常：必须记录日志并抛出，不能静默吞掉后仍返回 URL
            log.error("OSS 上传失败，ErrorCode: {}, ErrorMessage: {}", oe.getErrorCode(), oe.getErrorMessage());
            throw new BaseException("文件上传失败: " + oe.getErrorMessage());
        } catch (ClientException ce) {
            log.error("OSS 上传客户端异常，ErrorCode: {}, ErrorMessage: {}", ce.getErrorCode(), ce.getErrorMessage());
            throw new BaseException("文件上传失败: " + ce.getErrorMessage());
        } finally {
            ossClient.shutdown();
        }

        return "https://" + bucketName + "." + endpoint + "/" + objectName;
    }

    /**
     * 删除 OSS 中的对象
     * @param objectName 对象名（含目录路径）
     */
    public void delete(String objectName) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucketName, objectName);
        } catch (OSSException oe) {
            log.error("OSS 删除失败，ErrorCode: {}, ErrorMessage: {}", oe.getErrorCode(), oe.getErrorMessage());
            throw new BaseException("文件删除失败: " + oe.getErrorMessage());
        } catch (ClientException ce) {
            log.error("OSS 删除客户端异常，ErrorCode: {}, ErrorMessage: {}", ce.getErrorCode(), ce.getErrorMessage());
            throw new BaseException("文件删除失败: " + ce.getErrorMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 下载 OSS 对象并读取为 UTF-8 字符串
     * @param objectName 对象名（含目录路径）
     * @return 对象内容字符串
     */
    public String downloadAsString(String objectName) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            // OSSObject 与内容流一并放入 try-with-resources，确保连接正确释放
            try (OSSObject ossObject = ossClient.getObject(bucketName, objectName);
                 InputStream in = ossObject.getObjectContent()) {
                return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            }
        } catch (OSSException oe) {
            log.error("OSS 下载失败，ErrorCode: {}, ErrorMessage: {}", oe.getErrorCode(), oe.getErrorMessage());
            throw new BaseException("文件下载失败: " + oe.getErrorMessage());
        } catch (ClientException ce) {
            log.error("OSS 下载客户端异常，ErrorCode: {}, ErrorMessage: {}", ce.getErrorCode(), ce.getErrorMessage());
            throw new BaseException("文件下载失败: " + ce.getErrorMessage());
        } catch (Exception e) {
            log.error("OSS 下载异常", e);
            throw new BaseException("文件下载失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }
}
