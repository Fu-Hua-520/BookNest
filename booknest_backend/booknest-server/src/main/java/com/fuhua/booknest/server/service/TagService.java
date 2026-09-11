package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.entity.Tag;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface TagService {

    /**
     * 查询全部标签
     * <p>前台视图：已禁用的标签（status=0）不会出现在结果中</p>
     * @return 标签列表
     */
    List<Tag> listTags();

    /**
     * 查询热门标签（按使用次数降序取前 limit 条）
     * <p>前台视图：已禁用的标签不会出现在结果中</p>
     * @param limit 数量
     * @return 热门标签列表
     */
    List<Tag> getHotTags(Integer limit);

    /**
     * 按名称模糊搜索标签
     * <p>前台视图：已禁用的标签不会出现在结果中</p>
     * @param keyword 关键字
     * @return 标签列表
     */
    List<Tag> searchTags(String keyword);

    /**
     * 管理端：分页查询标签（含禁用标签）
     * @param keyword 名称关键字（可空）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageInfo<Tag> listTagPage(String keyword, Integer page, Integer pageSize);

    /**
     * 管理端：新增标签
     * <p>标签名全局唯一；使用次数由发帖流程维护，此处固定为 0</p>
     * @param tag 标签信息（仅取 name / status）
     * @return 新标签ID
     */
    String createTag(Tag tag);

    /**
     * 管理端：更新标签（重命名 / 启用禁用）
     * @param tag 标签信息（必须含 id，可取 name / status）
     */
    void updateTag(Tag tag);

    /**
     * 管理端：删除标签
     * <p>已被帖子引用时拒绝删除</p>
     * @param id 标签ID
     */
    void deleteTag(String id);
}
