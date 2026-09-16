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
     * 按ID批量查询标签（帖子列表组装时一次性取回用到的全部标签，避免逐条 selectById）
     * @param ids 标签ID集合
     * @return 标签列表
     */
    List<Tag> listByIds(@Param("ids") List<String> ids);

    /**
     * 查询热门标签（按实时引用数降序取前 limit 条）
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
