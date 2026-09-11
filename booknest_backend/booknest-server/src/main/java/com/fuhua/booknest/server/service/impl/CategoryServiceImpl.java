package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.vo.AdminCategoryVO;
import com.fuhua.booknest.pojo.vo.CategoryVO;
import com.fuhua.booknest.server.mapper.CategoryMapper;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    /** 分类名称长度上限（与 category.name VARCHAR(50) 对齐） */
    private static final int NAME_MAX_LENGTH = 50;
    /** 分类描述长度上限（与 category.description VARCHAR(200) 对齐） */
    private static final int DESCRIPTION_MAX_LENGTH = 200;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private PostMapper postMapper;

    /**
     * 组装两级分类树：parentId 为空视为一级分类，其余挂到对应一级分类的 children
     * <p>前台视图：已禁用的分类不返回；父分类被禁用时，其子分类因找不到挂载点而被一并隐藏</p>
     */
    @Override
    public List<CategoryVO> getCategoryTree() {
        List<Category> categories = categoryMapper.listAll();

        List<CategoryVO> roots = new ArrayList<>();
        Map<String, CategoryVO> rootMap = new LinkedHashMap<>();

        // 组装一级分类
        for (Category category : categories) {
            if (isRoot(category.getParentId()) && !isDisabled(category)) {
                CategoryVO vo = toVO(category);
                roots.add(vo);
                rootMap.put(category.getId(), vo);
            }
        }

        // 组装二级分类，挂到对应一级分类下
        for (Category category : categories) {
            if (isRoot(category.getParentId()) || isDisabled(category)) {
                continue;
            }
            CategoryVO parent = rootMap.get(category.getParentId());
            if (parent != null) {
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(toVO(category));
            }
        }

        // 按 sortOrder 排序（null 排最后）
        Comparator<CategoryVO> orderComparator = Comparator.comparing(
                CategoryVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
        roots.sort(orderComparator);
        for (CategoryVO root : roots) {
            if (root.getChildren() != null) {
                root.getChildren().sort(orderComparator);
            }
        }

        return roots;
    }

    /**
     * 管理端分类树：不做状态过滤，额外携带 status / description / 时间戳
     */
    @Override
    public List<AdminCategoryVO> getCategoryTreeForAdmin() {
        List<Category> categories = categoryMapper.listAll();

        List<AdminCategoryVO> roots = new ArrayList<>();
        Map<String, AdminCategoryVO> rootMap = new LinkedHashMap<>();

        for (Category category : categories) {
            if (isRoot(category.getParentId())) {
                AdminCategoryVO vo = toAdminVO(category);
                roots.add(vo);
                rootMap.put(category.getId(), vo);
            }
        }

        for (Category category : categories) {
            if (isRoot(category.getParentId())) {
                continue;
            }
            AdminCategoryVO parent = rootMap.get(category.getParentId());
            if (parent == null) {
                // 删除分类时已拦截有子分类的情况，这里出现孤儿数据说明库被直接改动过
                log.warn("检测到孤儿分类：id={}, name={}, parentId={}",
                        category.getId(), category.getName(), category.getParentId());
                continue;
            }
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(toAdminVO(category));
        }

        Comparator<AdminCategoryVO> orderComparator = Comparator.comparing(
                AdminCategoryVO::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()));
        roots.sort(orderComparator);
        for (AdminCategoryVO root : roots) {
            if (root.getChildren() != null) {
                root.getChildren().sort(orderComparator);
            }
        }

        return roots;
    }

    @Override
    public String createCategory(Category category) {
        if (category == null) {
            throw new BaseException("分类信息不能为空");
        }
        String name = normalizeName(category.getName());
        String description = normalizeDescription(category.getDescription());

        List<Category> all = categoryMapper.listAll();
        String parentId = trimToNull(category.getParentId());
        validateParent(parentId, null, all);
        ensureNameAvailable(all, null, parentId, name);

        Category toInsert = Category.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .parentId(parentId)
                .sortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder())
                .description(description)
                .status(normalizeStatus(category.getStatus()))
                .createTime(LocalDateTime.now())
                .build();
        categoryMapper.insert(toInsert);
        log.info("新增分类成功：id={}, name={}, parentId={}", toInsert.getId(), name, parentId);
        return toInsert.getId();
    }

    @Override
    public void updateCategory(Category category) {
        if (category == null) {
            throw new BaseException("分类信息不能为空");
        }
        String id = trimToNull(category.getId());
        if (id == null) {
            throw new BaseException("分类ID不能为空");
        }
        if (categoryMapper.selectById(id) == null) {
            throw new BaseException("分类不存在");
        }

        String name = normalizeName(category.getName());
        String description = normalizeDescription(category.getDescription());

        List<Category> all = categoryMapper.listAll();
        String parentId = trimToNull(category.getParentId());
        validateParent(parentId, id, all);
        ensureNameAvailable(all, id, parentId, name);

        Category toUpdate = Category.builder()
                .id(id)
                .name(name)
                .parentId(parentId)
                .sortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder())
                .description(description)
                .status(normalizeStatus(category.getStatus()))
                .build();
        categoryMapper.update(toUpdate);
        log.info("更新分类成功：id={}, name={}, parentId={}", id, name, parentId);
    }

    @Override
    public void deleteCategory(String id) {
        String categoryId = trimToNull(id);
        if (categoryId == null) {
            throw new BaseException("分类ID不能为空");
        }
        if (categoryMapper.selectById(categoryId) == null) {
            throw new BaseException("分类不存在");
        }
        if (hasChildren(categoryId, categoryMapper.listAll())) {
            throw new BaseException("该分类下仍有子分类，请先删除或转移子分类");
        }
        int postCount = postMapper.countByCategoryId(categoryId);
        if (postCount > 0) {
            throw new BaseException("该分类下仍有 " + postCount + " 篇帖子，无法删除");
        }
        categoryMapper.deleteById(categoryId);
        log.info("删除分类成功：id={}", categoryId);
    }

    /* ------------------------------ 内部辅助方法 ------------------------------ */

    /**
     * 校验父分类：必须存在，且自身必须是一级分类（分类树仅两级）
     * @param parentId 待挂载的父分类ID，可为空（表示一级分类）
     * @param selfId 当前分类ID（更新时用于拦截「挂到自身之下」），新增时传 null
     * @param all 全部分类
     */
    private void validateParent(String parentId, String selfId, List<Category> all) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BaseException("不能将分类挂到自身之下");
        }
        Category parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BaseException("父分类不存在");
        }
        if (!isRoot(parent.getParentId())) {
            throw new BaseException("分类树最多两级，不能挂到子分类之下");
        }
        // 自身已有子分类时不允许降级为二级分类，否则其子分类会变成三级
        if (selfId != null && hasChildren(selfId, all)) {
            throw new BaseException("该分类下仍有子分类，不能改为子分类");
        }
    }

    /**
     * 同层级重名校验：同一 parentId 下分类名称不允许重复
     * @param excludeId 查重时排除的分类ID（更新场景传自身），新增时传 null
     */
    private void ensureNameAvailable(List<Category> all, String excludeId, String parentId, String name) {
        for (Category category : all) {
            if (excludeId != null && excludeId.equals(category.getId())) {
                continue;
            }
            boolean sameLevel = Objects.equals(parentId, trimToNull(category.getParentId()));
            if (sameLevel && name.equals(category.getName())) {
                throw new BaseException("同级下已存在名为「" + name + "」的分类");
            }
        }
    }

    private boolean hasChildren(String id, List<Category> all) {
        for (Category category : all) {
            if (id.equals(category.getParentId())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeName(String name) {
        String value = trimToNull(name);
        if (value == null) {
            throw new BaseException("分类名称不能为空");
        }
        if (value.length() > NAME_MAX_LENGTH) {
            throw new BaseException("分类名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    private String normalizeDescription(String description) {
        String value = trimToNull(description);
        if (value != null && value.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BaseException("分类描述不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /** 状态缺省为启用，只接受 0/1 */
    private int normalizeStatus(Integer status) {
        return status != null && status == STATUS_DISABLED ? STATUS_DISABLED : STATUS_ENABLED;
    }

    private boolean isDisabled(Category category) {
        return category.getStatus() != null && category.getStatus() == STATUS_DISABLED;
    }

    /**
     * 判断是否为一级分类（parentId 为 null 或空字符串）
     */
    private boolean isRoot(String parentId) {
        return parentId == null || parentId.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 分类实体转 VO（不含 children）
     */
    private CategoryVO toVO(Category category) {
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .sortOrder(category.getSortOrder())
                .build();
    }

    /**
     * 分类实体转管理端 VO（不含 children）
     */
    private AdminCategoryVO toAdminVO(Category category) {
        return AdminCategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .parentId(category.getParentId())
                .sortOrder(category.getSortOrder())
                .description(category.getDescription())
                .status(category.getStatus())
                .createTime(category.getCreateTime())
                .updateTime(category.getUpdateTime())
                .build();
    }
}
