package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper {

    /**
     * 插入新标签
     * @param tag 标签信息
     */
    void insert(Tag tag);

    /**
     * 根据标签ID查询标签
     * @param id 标签ID
     * @return 标签信息
     */
    Tag selectById(@Param("id") String id);

    /**
     * 根据标签名称查询标签
     * @param name 标签名称
     * @return 标签信息
     */
    Tag selectByName(@Param("name") String name);

    /**
     * 查询全部标签
     * @return 标签列表
     */
    List<Tag> listAll();

    /**
     * 查询热门标签（按使用次数降序取前 limit 条）
     * @param limit 数量
     * @return 标签列表
     */
    List<Tag> listHotTags(@Param("limit") Integer limit);

    /**
     * 按名称模糊搜索标签
     * @param keyword 关键字
     * @return 标签列表
     */
    List<Tag> searchByKeyword(@Param("keyword") String keyword);

    /**
     * 标签使用次数 +1
     * @param id 标签ID
     */
    void incrementUseCount(@Param("id") String id);

    /**
     * 更新标签信息
     * @param tag 标签信息
     */
    void update(Tag tag);

    /**
     * 根据标签ID删除标签
     * @param id 标签ID
     */
    void deleteById(@Param("id") String id);
}
