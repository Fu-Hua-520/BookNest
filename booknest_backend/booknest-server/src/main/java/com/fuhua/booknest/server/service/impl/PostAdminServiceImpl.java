package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.PostSortConstant;
import com.fuhua.booknest.common.constant.PostStatusConstant;
import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.server.common.CountBuffer;
import com.fuhua.booknest.server.common.RedisCacheHelper;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.service.PostAdminService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PostAdminServiceImpl implements PostAdminService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private RedisCacheHelper cache;

    @Autowired
    private CountBuffer countBuffer;

    /**
     * 帖子列表缓存失效。
     *
     * <p>管理端这三个动作（审核 / 置顶 / 上下架）都会立刻改变用户端列表的可见性与顺序，
     * 是本项目里最常见「改了却看不到」的源头，必须显式清缓存。</p>
     */
    private void evictPostListCache() {
        cache.evictByPattern(RedisConstant.POST_LIST_PATTERN);
    }

    @Override
    public PageInfo<Post> listPosts(Integer auditStatus, Integer status, Integer page, Integer pageSize) {
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }

        PageHelper.startPage(page, pageSize);
        // 管理端按审核/上架状态筛选即可，不参与用户端的热度排序，sort 传 latest；
        // 末位 postType 传 null = 不按类型过滤（普通贴与求助贴都要能看到）
        List<Post> list = postMapper.list(null, null, null, auditStatus, status, PostSortConstant.LATEST, null);

        // 计数要补上「缓冲里还没落库的增量」：用户端展示的是 PostVO（组装时已合并增量），
        // 管理端这里直接返回 Post 实体，不补的话同一个帖子在两个页面上的阅/赞/评会对不上。
        applyPendingCounts(list);
        return new PageInfo<>(list);
    }

    /**
     * 把 Redis 计数缓冲里未落库的增量补到帖子实体的四个计数上。
     *
     * <p>一次 HMGET 取回整页增量（4 × N 个 field），不做逐条查询。
     * 计数列为 0 或 delta 为 0 时结果不变，所以无脑加是安全的。</p>
     *
     * @param posts 帖子实体列表（就地修改）
     */
    private void applyPendingCounts(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return;
        }
        List<String> postIds = new ArrayList<>(posts.size());
        for (Post post : posts) {
            if (post != null && post.getId() != null) {
                postIds.add(post.getId());
            }
        }
        if (postIds.isEmpty()) {
            return;
        }
        Map<String, CountBuffer.PostCountDelta> deltas = countBuffer.pendingPostCounts(postIds);
        for (Post post : posts) {
            if (post == null) {
                continue;
            }
            CountBuffer.PostCountDelta d =
                    deltas.getOrDefault(post.getId(), CountBuffer.PostCountDelta.ZERO);
            post.setViewCount(CountBuffer.merge(post.getViewCount(), d.view()));
            post.setLikeCount(CountBuffer.merge(post.getLikeCount(), d.like()));
            post.setCommentCount(CountBuffer.merge(post.getCommentCount(), d.comment()));
            post.setCollectCount(CountBuffer.merge(post.getCollectCount(), d.collect()));
        }
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
        evictPostListCache();
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
        evictPostListCache();
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
        evictPostListCache();
    }
}
