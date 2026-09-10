package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.vo.CategoryVO;
import com.fuhua.booknest.server.mapper.CategoryMapper;
import com.fuhua.booknest.server.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 组装两级分类树：parentId 为空视为一级分类，其余挂到对应一级分类的 children
     */
    @Override
    public List<CategoryVO> getCategoryTree() {
        List<Category> categories = categoryMapper.listAll();

        List<CategoryVO> roots = new ArrayList<>();
        Map<String, CategoryVO> rootMap = new LinkedHashMap<>();

        // 组装一级分类
        for (Category category : categories) {
            if (isRoot(category.getParentId())) {
                CategoryVO vo = toVO(category);
                roots.add(vo);
                rootMap.put(category.getId(), vo);
            }
        }

        // 组装二级分类，挂到对应一级分类下
        for (Category category : categories) {
            if (!isRoot(category.getParentId())) {
                CategoryVO parent = rootMap.get(category.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(toVO(category));
                }
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
     * 判断是否为一级分类（parentId 为 null 或空字符串）
     */
    private boolean isRoot(String parentId) {
        return parentId == null || parentId.trim().isEmpty();
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
}
