package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.BrowseHistoryVO;

import java.util.List;

/**
 * 浏览历史服务
 */
public interface BrowseHistoryService {

    /**
     * 分页查询我的浏览历史（按最后浏览时间倒序）
     * @param userId 当前用户ID
     * @param page 页码
     * @param pageSize 每页条数
     * @return 浏览历史列表
     */
    List<BrowseHistoryVO> listMyHistory(String userId, int page, int pageSize);

    /**
     * 删除单条浏览记录
     * @param userId 当前用户ID
     * @param postId 帖子 ID
     */
    void removeOne(String userId, String postId);

    /**
     * 清空我的浏览历史
     * @param userId 当前用户ID
     */
    void clear(String userId);
}
