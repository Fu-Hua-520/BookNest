package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.entity.Post;
import com.github.pagehelper.PageInfo;

/**
 * 管理后台帖子服务
 */
public interface PostAdminService {

    /**
     * 分页查询帖子列表
     * @param auditStatus 审核状态（可空）
     * @param status 帖子状态（可空）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageInfo<Post> listPosts(Integer auditStatus, Integer status, Integer page, Integer pageSize);

    /**
     * 审核帖子
     * @param postId 帖子ID
     * @param auditStatus 审核状态（通过/拒绝）
     * @param auditReason 审核原因（可空）
     */
    void auditPost(String postId, Integer auditStatus, String auditReason);

    /**
     * 设置帖子置顶
     * @param postId 帖子ID
     * @param isTop 是否置顶（0/1）
     */
    void setPostTop(String postId, Integer isTop);

    /**
     * 设置帖子状态
     * @param postId 帖子ID
     * @param status 帖子状态
     */
    void setPostStatus(String postId, Integer status);
}
