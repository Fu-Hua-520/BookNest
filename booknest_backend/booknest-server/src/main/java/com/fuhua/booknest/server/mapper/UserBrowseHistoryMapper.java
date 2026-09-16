package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.UserBrowseHistory;
import com.fuhua.booknest.pojo.vo.BrowseHistoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserBrowseHistoryMapper {

    /**
     * 写入浏览记录：已存在则只更新时间与次数
     * @param history 浏览记录（userId + postId 唯一）
     */
    void upsert(UserBrowseHistory history);

    /**
     * 分页查询某用户的浏览历史（帖子信息在 SQL 里 join 出来）
     * @param userId 用户 ID
     * @return 浏览历史列表
     */
    List<BrowseHistoryVO> listByUser(@Param("userId") String userId);

    /**
     * 统计某个帖子的浏览历史条数（删除帖子时用于提示）
     * @param postId 帖子 ID
     * @return 条数
     */
    int countByPostId(@Param("postId") String postId);

    /**
     * 删除单条浏览记录
     * @param userId 用户 ID
     * @param postId 帖子 ID
     */
    void deleteOne(@Param("userId") String userId, @Param("postId") String postId);

    /**
     * 清空某用户的浏览历史
     * @param userId 用户 ID
     */
    void deleteAll(@Param("userId") String userId);

    /**
     * 删除某个帖子的全部浏览记录（帖子被删除时级联清理）
     * @param postId 帖子 ID
     */
    void deleteByPostId(@Param("postId") String postId);
}
