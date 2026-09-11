package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.vo.AdminCategoryVO;
import com.fuhua.booknest.pojo.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    /**
     * 查询分类树（两级：一级分类 + 其下子分类）
     * <p>前台视图：已禁用的分类（status=0）不会出现在结果中</p>
     * @return 分类树
     */
    List<CategoryVO> getCategoryTree();

    /**
     * 管理端：查询完整分类树（含禁用分类，携带状态/描述/时间戳）
     * @return 分类树
     */
    List<AdminCategoryVO> getCategoryTreeForAdmin();

    /**
     * 管理端：新增分类
     * <p>同层级下分类名称不允许重复；子分类只能挂在已存在的一级分类下</p>
     * @param category 分类信息（name / parentId / sortOrder / description / status）
     * @return 新分类ID
     */
    String createCategory(Category category);

    /**
     * 管理端：更新分类
     * <p>已有子分类的分类不允许被挂到其他分类下（避免出现三级结构）</p>
     * @param category 分类信息（必须含 id）
     */
    void updateCategory(Category category);

    /**
     * 管理端：删除分类
     * <p>存在子分类或已被帖子引用时拒绝删除</p>
     * @param id 分类ID
     */
    void deleteCategory(String id);
}
