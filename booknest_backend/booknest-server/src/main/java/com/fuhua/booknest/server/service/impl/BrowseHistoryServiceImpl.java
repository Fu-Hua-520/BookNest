package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.pojo.vo.BrowseHistoryVO;
import com.fuhua.booknest.server.common.CountBuffer;
import com.fuhua.booknest.server.mapper.UserBrowseHistoryMapper;
import com.fuhua.booknest.server.service.BrowseHistoryService;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    /** 单页上限，防止一次把几千条历史全捞出来 */
    private static final int MAX_PAGE_SIZE = 50;

    @Autowired
    private UserBrowseHistoryMapper userBrowseHistoryMapper;

    @Autowired
    private CountBuffer countBuffer;

    @Override
    public List<BrowseHistoryVO> listMyHistory(String userId, int page, int pageSize) {
        if (userId == null || userId.isEmpty()) {
            return new ArrayList<>();
        }
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1) {
            pageSize = 20;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }

        PageHelper.startPage(page, pageSize);
        List<BrowseHistoryVO> list = userBrowseHistoryMapper.listByUser(userId);
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        applyPendingCounts(list);
        return list;
    }

    /**
     * 补上 Redis 计数缓冲里还没落库的增量。
     *
     * <p>这里的计数是 SQL join 出来的（为了免掉逐条回查造成 N+1），拿到的是
     * <b>数据库里的滞后值</b>。不补的话，同一篇帖子在首页和浏览历史里会显示两个数字。</p>
     *
     * @param list 浏览历史 VO 列表（就地修改计数）
     */
    private void applyPendingCounts(List<BrowseHistoryVO> list) {
        List<String> postIds = new ArrayList<>(list.size());
        for (BrowseHistoryVO vo : list) {
            if (vo != null && vo.getPostId() != null) {
                postIds.add(vo.getPostId());
            }
        }
        if (postIds.isEmpty()) {
            return;
        }
        Map<String, CountBuffer.PostCountDelta> deltas = countBuffer.pendingPostCounts(postIds);
        for (BrowseHistoryVO vo : list) {
            if (vo == null) {
                continue;
            }
            CountBuffer.PostCountDelta d =
                    deltas.getOrDefault(vo.getPostId(), CountBuffer.PostCountDelta.ZERO);
            // 这个 VO 里没有收藏数，所以只补三类
            vo.setViewCount(CountBuffer.merge(vo.getViewCount(), d.view()));
            vo.setLikeCount(CountBuffer.merge(vo.getLikeCount(), d.like()));
            vo.setCommentCount(CountBuffer.merge(vo.getCommentCount(), d.comment()));
        }
    }

    @Override
    public void removeOne(String userId, String postId) {
        if (userId == null || userId.isEmpty() || postId == null || postId.isEmpty()) {
            return;
        }
        userBrowseHistoryMapper.deleteOne(userId, postId);
    }

    @Override
    public void clear(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        userBrowseHistoryMapper.deleteAll(userId);
        log.info("用户 {} 清空了浏览历史", userId);
    }
}
