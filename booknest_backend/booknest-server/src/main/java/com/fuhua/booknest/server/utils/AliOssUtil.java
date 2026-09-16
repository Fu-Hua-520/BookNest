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
     * 上传文件到 OSS。
     *
     * objectName 由「文件内容 MD5」构成（见 CommonController），即内容寻址：
     * 同一份内容永远映射到同一个对象名。因此这里先判断是否已存在，已存在就直接复用，
     * 不再重复写入——既避免同一张图在 OSS 里堆出多份副本，也省掉重复上传的流量与耗时。
     *
     * @param bytes 文件字节内容
     * @param objectName 对象名（含目录路径）
     * @return 文件可访问的 URL
     */
    public String upload(byte[] bytes, String objectName) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            if (ossClient.doesObjectExist(bucketName, objectName)) {
                log.info("OSS 对象已存在，跳过上传直接复用: {}", objectName);
            } else {
                ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
            }
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

        return buildUrl(objectName);
    }

    /**
     * 拼接对象公网访问 URL。
     *
     * endpoint 配置项通常自带协议前缀（如 https://oss-cn-qingdao.aliyuncs.com），
     * 若直接拼 "https://" + bucket + "." + endpoint，会得到
     * "https://bucket.https://oss-cn-xxx.aliyuncs.com/..." 这种畸形地址，
     * 入库后图片根本加载不出来。所以先把 endpoint 的协议前缀剥掉再拼。
     *
     * @param objectName 对象名（含目录路径）
     * @return 公网可访问 URL
     */
    private String buildUrl(String objectName) {
        String host = endpoint.replaceFirst("^https?://", "");
        return "https://" + bucketName + "." + host + "/" + objectName;
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
