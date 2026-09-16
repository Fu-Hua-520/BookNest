package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.vo.AdminCategoryVO;
import com.fuhua.booknest.pojo.vo.BarVO;
import com.fuhua.booknest.pojo.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    /**
     * 查询全部可见书吧（扁平列表，无层级）。
     * <p>历史包袱：方法名与接口路径仍叫 tree，但书吧已取消两级结构，
     * 返回的每个元素 children 恒为空，前端按平铺列表渲染即可。</p>
     * <p>可见性：已禁用（status=0）或未过审（auditStatus≠1）的书吧不会返回。</p>
     * @return 书吧列表
     */
    List<CategoryVO> getCategoryTree();

    /**
     * 管理端：查询完整书吧列表（含禁用书吧，携带状态/描述/时间戳）
     * @return 书吧列表
     */
    List<AdminCategoryVO> getCategoryTreeForAdmin();

    /**
     * 管理端：新增书吧
     * <p>吧名不允许重复；书吧已无层级，parentId 一律忽略。</p>
     * @param category 书吧信息（name / icon / sortOrder / description / status）
     * @return 新书吧ID
     */
    String createCategory(Category category);

    /**
     * 管理端：更新书吧
     * @param category 书吧信息（必须含 id）
     */
    void updateCategory(Category category);

    /**
     * 管理端：删除书吧
     * <p>已被帖子引用时拒绝删除</p>
     * @param id 书吧ID
     */
    void deleteCategory(String id);

    /* ==================== 书吧（贴吧式「吧」）==================== */

    /**
     * 书吧广场：全部已通过审核的书吧，按帖数降序。
     * <p>只返回 auditStatus=1 且 status=1 的记录，待审/被驳回的申请不会出现在广场里。</p>
     * @return 书吧列表（含图标、吧主与帖数）
     */
    List<BarVO> listBars();

    /**
     * 我关注的书吧（侧边栏「我关注的吧」用），按关注时间倒序。
     * <p>只返回当前可见（已过审 + 未禁用）的吧 —— 被禁用的吧即使还在关注列表里也不展示。</p>
     * @return 书吧列表
     */
    List<BarVO> listMyBars();

    /**
     * 热门书吧：按吧内成员（关注）数降序前 20。
     * <p>结果内存缓存约一天（隔天自动重算），避免每次进首页都全表聚合。</p>
     * @return 书吧列表（最多 20 个）
     */
    List<BarVO> listHotBars();

    /**
     * 按吧名模糊搜索可见书吧（顶部搜索栏「书吧」Tab 用）
     * @param keyword 关键词
     * @return 书吧列表
     */
    List<BarVO> searchBars(String keyword);

    /**
     * 用户申请创建书吧
     * <p>落库时 auditStatus=0（待审核）、ownerId=当前登录用户，需要管理员审批后才出现在广场。
     * 名称全局唯一，重名直接拒绝，避免广场出现两个同名吧。</p>
     * @param name 吧名
     * @param description 吧简介（可空）
     * @param icon 书吧图标 URL（可空，为空则前端用吧名首字兜底）
     * @return 新书吧ID
     */
    String applyBar(String name, String description, String icon);

    /**
     * 我申请过的书吧（只含待审 / 已驳回 —— 已通过的申请对应的吧已经上了广场，
     * 继续展示会造成「我的申请里还有一个吧」的错觉）
     * @return 书吧列表
     */
    List<BarVO> listMyApplications();

    /**
     * 管理端：书吧创建申请列表
     * @param auditStatus 审核状态过滤（可空表示全部）
     * @return 书吧列表
     */
    List<BarVO> listBarApplications(Integer auditStatus);

    /**
     * 管理端：审批书吧创建申请
     * <p>通过时可由管理员指定吧主（不传则维持申请人）；通过后吧主会自动关注本吧，
     * 否则会出现「吧主自己不在吧里」的怪状态 —— 等级经验也无处累计。</p>
     * @param id 书吧ID
     * @param approve true-通过 false-驳回
     * @param rejectReason 驳回原因（驳回时必填）
     * @param ownerId 吧主用户ID（可空；通过时生效）
     */
    void auditBar(String id, boolean approve, String rejectReason, String ownerId);
}
