package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 发布帖子请求 DTO
 */
@Data
public class PostPublishDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 标题
    @NotBlank(message = "标题不能为空")
    private String title;

    // 摘要
    private String summary;

    // 正文（Markdown 全文）
    @NotBlank(message = "正文不能为空")
    private String content;

    // 关联书籍ID（可空）
    private String bookId;

    // 分类ID
    @NotBlank(message = "分类不能为空")
    private String categoryId;

    // 封面图URL（可空）
    private String coverImage;

    // 标签ID列表（可空）
    private List<String> tagIds;

    // 帖子类型：NORMAL-普通（默认） HELP-求助贴（可空，非法值回落 NORMAL）
    private String postType;
}
