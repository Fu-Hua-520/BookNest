package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 插入新分类
     * @param category 分类信息
     */
    void insert(Category category);

    /**
     * 根据分类ID查询分类
     * @param id 分类ID
     * @return 分类信息
     */
    Category selectById(@Param("id") String id);

    /**
     * 查询全部分类
     * @return 分类列表
     */
    List<Category> listAll();

    /**
     * 按ID批量查询分类（帖子列表组装时一次性取回用到的分类，避免逐条 selectById）
     * @param ids 分类ID集合
     * @return 分类列表
     */
    List<Category> selectByIds(@Param("ids") List<String> ids);

    /**
     * 更新分类信息
     * @param category 分类信息
     */
    void update(Category category);

    /**
     * 根据分类ID删除分类
     * @param id 分类ID
     */
    void deleteById(@Param("id") String id);

    /**
     * 按创建者查询「我申请过的书吧」
     * @param ownerId 吧主用户ID
     * @return 分类列表（按申请时间倒序）
     */
    List<Category> listByOwnerId(@Param("ownerId") String ownerId);

    /**
     * 审核书吧创建申请（只动 audit_status / reject_reason / status）
     * @param id 分类ID
     * @param auditStatus 审核状态 1-通过 2-驳回
     * @param rejectReason 驳回原因（通过时传 null）
     */
    void auditBar(@Param("id") String id,
                  @Param("auditStatus") Integer auditStatus,
                  @Param("rejectReason") String rejectReason);

    /**
     * 只更新书吧的吧主（管理员审批时指定 / 变更吧主）
     * @param id 书吧ID
     * @param ownerId 新吧主用户ID
     */
    void updateOwner(@Param("id") String id, @Param("ownerId") String ownerId);

    /**
     * 统计每个分类下已发布且已过审的帖子数，用于书吧广场展示「帖数」。
     * 一次 group by 拿全量，避免按吧逐个 count 造成 N+1。
     * @return 每行包含 category_id 与 post_count 两个键
     */
    List<java.util.Map<String, Object>> countPostsGroupByCategory();
}
