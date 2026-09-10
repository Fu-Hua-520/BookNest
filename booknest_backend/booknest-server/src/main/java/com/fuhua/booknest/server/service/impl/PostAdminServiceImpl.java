package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.PostStatusConstant;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.service.PostAdminService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class PostAdminServiceImpl implements PostAdminService {

    @Autowired
    private PostMapper postMapper;

    @Override
    public PageInfo<Post> listPosts(Integer auditStatus, Integer status, Integer page, Integer pageSize) {
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }

        PageHelper.startPage(page, pageSize);
        List<Post> list = postMapper.list(null, null, auditStatus, status);
        return new PageInfo<>(list);
    }

    @Override
    public void auditPost(String postId, Integer auditStatus, String auditReason) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        if (!PostStatusConstant.AUDIT_APPROVED.equals(auditStatus)
                && !PostStatusConstant.AUDIT_REJECTED.equals(auditStatus)) {
            throw new BaseException("审核状态非法");
        }
        postMapper.audit(postId, auditStatus, auditReason, LocalDateTime.now());
    }

    @Override
    public void setPostTop(String postId, Integer isTop) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        if (isTop == null || (isTop != 0 && isTop != 1)) {
            throw new BaseException("置顶参数非法");
        }
        postMapper.updateIsTop(postId, isTop);
    }

    @Override
    public void setPostStatus(String postId, Integer status) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        if (!PostStatusConstant.STATUS_PUBLISHED.equals(status)
                && !PostStatusConstant.STATUS_OFFLINE.equals(status)
                && !PostStatusConstant.STATUS_DRAFT.equals(status)) {
            throw new BaseException("帖子状态非法");
        }
        postMapper.updateStatus(postId, status);
    }
}
