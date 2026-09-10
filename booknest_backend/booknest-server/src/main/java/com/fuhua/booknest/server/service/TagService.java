package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.entity.Tag;

import java.util.List;

public interface TagService {

    /**
     * 查询全部标签
     * @return 标签列表
     */
    List<Tag> listTags();

    /**
     * 查询热门标签（按使用次数降序取前 limit 条）
     * @param limit 数量
     * @return 热门标签列表
     */
    List<Tag> getHotTags(Integer limit);

    /**
     * 按名称模糊搜索标签
     * @param keyword 关键字
     * @return 标签列表
     */
    List<Tag> searchTags(String keyword);
}
