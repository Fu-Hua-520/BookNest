package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.PostPublishDTO;
import com.fuhua.booknest.pojo.dto.PostUpdateDTO;
import com.fuhua.booknest.pojo.vo.PostDetailVO;
import com.fuhua.booknest.pojo.vo.PostVO;

import java.util.List;

public interface PostService {

    /**
     * 发布帖子
     * @param dto 发布信息
     * @return 帖子卡片 VO
     */
    PostVO publishPost(PostPublishDTO dto);

    /**
     * 查询帖子详情（浏览量 +1）
     * @param postId 帖子ID
     * @return 帖子详情 VO
     */
    PostDetailVO getPostDetail(String postId);

    /**
     * 分页查询帖子列表
     * @param categoryId 分类ID（可空）
     * @param tagId 标签ID（可空）
     * @param userId 作者用户ID（可空，用于个人主页按作者过滤）
     * @param auditStatus 审核状态（可空，默认只返回已过审）
     * @param sort 排序方式（可空，latest/hot/views/essence/comments，非法值回落 latest）
     * @param postType 帖子类型（可空；传 HELP 只看求助贴，传 NORMAL 只看普通贴，空为全部）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 帖子卡片列表
     */
    List<PostVO> listPosts(String categoryId, String tagId, String userId, Integer auditStatus,
                           String sort, String postType, Integer page, Integer pageSize);

    /**
     * 分页查询当前用户收藏的帖子（个人中心「我的收藏」）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 帖子卡片列表（按收藏时间倒序）
     */
    List<PostVO> listMyCollectedPosts(Integer page, Integer pageSize);

    /**
     * 按关键词搜索已过审帖子（供 AI 工具调用）
     * @param keyword 搜索关键词（可空，为空返回空列表）
     * @param limit 返回条数上限
     * @return 帖子卡片列表
     */
    List<PostVO> searchPosts(String keyword, int limit);

    /**
     * 更新帖子
     * @param postId 帖子ID
     * @param dto 更新信息
     */
    void updatePost(String postId, PostUpdateDTO dto);

    /**
     * 删除帖子
     * @param postId 帖子ID
     */
    void deletePost(String postId);
}
