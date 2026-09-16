package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.AiBot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiBotMapper {

    /**
     * 新增机器人
     * @param bot 机器人配置
     */
    void insert(AiBot bot);

    /**
     * 按 ID 查询机器人
     * @param id 机器人ID
     * @return 机器人配置（不存在返回 null）
     */
    AiBot selectById(@Param("id") String id);

    /**
     * 按名称精确查询（名称大小写敏感由数据库排序规则决定，这里统一用 lower 比较）
     * @param name 机器人名称
     * @return 机器人配置（不存在返回 null）
     */
    AiBot selectByName(@Param("name") String name);

    /**
     * 我的机器人列表（含待审/驳回，按创建时间倒序）
     * @param ownerId 创建者用户ID
     * @return 机器人列表
     */
    List<AiBot> listByOwner(@Param("ownerId") String ownerId);

    /**
     * 全站可用机器人：已过审 + 创建者未停用（供 @ 选择器与触发索引）
     * @return 机器人列表
     */
    List<AiBot> listEnabledApproved();

    /**
     * 管理端：按审核状态查询机器人（auditStatus 为 null 时查全部，按创建时间倒序）
     * @param auditStatus 审核状态
     * @return 机器人列表
     */
    List<AiBot> listForAudit(@Param("auditStatus") Integer auditStatus);

    /**
     * 批量按 ID 查询机器人（触发后用，避免逐个 selectById）
     * @param ids ID 集合
     * @return 机器人列表
     */
    List<AiBot> selectByIds(@Param("ids") List<String> ids);

    /**
     * 统计创建者当前有多少个机器人（限制单用户上限）
     * @param ownerId 创建者用户ID
     * @return 数量
     */
    int countByOwner(@Param("ownerId") String ownerId);

    /**
     * 更新机器人配置（按 dto 传什么更新什么，null 字段不覆盖）
     * @param bot 机器人配置（必须带 id）
     */
    void update(AiBot bot);

    /**
     * 删除机器人
     * @param id 机器人ID
     */
    void deleteById(@Param("id") String id);

    /**
     * 审核：设置审核状态与驳回原因
     * @param id 机器人ID
     * @param auditStatus 审核状态
     * @param rejectReason 驳回原因（通过时为 null）
     */
    void audit(@Param("id") String id,
               @Param("auditStatus") Integer auditStatus,
               @Param("rejectReason") String rejectReason);

    /**
     * 创建者启用/停用
     * @param id 机器人ID
     * @param enabled 1启用 0停用
     */
    void updateEnabled(@Param("id") String id, @Param("enabled") Integer enabled);

    /**
     * 回复数 +1（触发成功落库后调用）
     * @param id 机器人ID
     */
    void incrementReplyCount(@Param("id") String id);
}
