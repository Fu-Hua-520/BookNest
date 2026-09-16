package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.AiBotSaveDTO;
import com.fuhua.booknest.pojo.entity.AiBot;
import com.fuhua.booknest.pojo.vo.AiBotBriefVO;
import com.fuhua.booknest.pojo.vo.AiBotVO;

import java.util.List;

/**
 * 评论区 AI 机器人服务。
 *
 * <p>职责分三块：</p>
 * <ul>
 *   <li><b>用户端</b>：创建 / 编辑 / 删除 / 启停自己的机器人，查看审核状态；</li>
 *   <li><b>管理端</b>：列出待审机器人并审批；</li>
 *   <li><b>运行期</b>：把评论正文里 @ 到的机器人解析出来（{@link #matchTriggered}），
 *       供异步回复使用。</li>
 * </ul>
 */
public interface AiBotService {

    /* ------------------------- 用户端 ------------------------- */

    /**
     * 提交创建申请。新建的机器人一律是「待审核」，通过前不参与 @ 触发。
     *
     * @param dto 机器人配置
     * @return 新建的机器人
     */
    AiBotVO apply(AiBotSaveDTO dto);

    /**
     * 我创建的机器人（含待审 / 已驳回 / 已通过）
     *
     * @return 机器人列表
     */
    List<AiBotVO> listMine();

    /**
     * 查看我创建的某个机器人详情
     *
     * @param id 机器人ID
     * @return 机器人详情
     */
    AiBotVO getMine(String id);

    /**
     * 编辑机器人。任何配置改动都会把机器人退回「待审核」状态并清空驳回原因 ——
     * 审核针对的是「这套 Key + 这个提示词」，改完必须重审。
     *
     * @param id  机器人ID
     * @param dto 新配置（apiKey 留空表示不修改）
     * @return 更新后的机器人
     */
    AiBotVO update(String id, AiBotSaveDTO dto);

    /**
     * 删除机器人（连带删除它在评论区留下的回复）
     *
     * @param id 机器人ID
     */
    void delete(String id);

    /**
     * 创建者启用 / 停用机器人
     *
     * @param id      机器人ID
     * @param enabled true 启用
     * @return 更新后的机器人
     */
    AiBotVO setEnabled(String id, boolean enabled);

    /**
     * 全站可用机器人（已过审 + 未停用），供评论区 @ 选择器与机器人广场展示。
     *
     * @return 机器人简要信息列表
     */
    List<AiBotBriefVO> listAvailable();

    /* ------------------------- 管理端 ------------------------- */

    /**
     * 管理端审核列表
     *
     * @param auditStatus 审核状态（null 表示全部）
     * @return 机器人列表
     */
    List<AiBotVO> listForAudit(Integer auditStatus);

    /**
     * 审批机器人
     *
     * @param id           机器人ID
     * @param approve      true 通过，false 驳回
     * @param rejectReason 驳回原因（驳回时必填）
     * @return 审批后的机器人
     */
    AiBotVO audit(String id, boolean approve, String rejectReason);

    /**
     * 管理端删除机器人（连带删除它在评论区留下的回复）。
     *
     * <p>与用户端的 {@link #delete} 的区别只有一个：<b>不校验归属</b>。
     * 机器人可能因为 Key 失效、提示词违规、创建者跑路而必须下架，
     * 这些场景下管理员不该被「只能操作自己的机器人」挡住。</p>
     *
     * @param id 机器人ID
     */
    void deleteByAdmin(String id);

    /* ------------------------- 运行期 ------------------------- */

    /**
     * 解析一段评论正文里 @ 到的机器人。
     *
     * <p>只返回「已过审 + 启用中」的机器人，最多 {@code MAX_TRIGGERS_PER_COMMENT} 个，
     * 同一个机器人重复 @ 只算一次。匹配采用「前缀匹配」而不是严格按分隔符切词：
     * 中文里 @机器人名 后面常常直接跟正文（如「@小书虫推荐几本」），
     * 按空格切词会漏触发。</p>
     *
     * @param content 评论正文
     * @return 命中的机器人（可能为空列表）
     */
    List<AiBot> matchTriggered(String content);

    /**
     * 按 ID 取机器人配置（含 API Key，仅供服务端内部调用）
     *
     * @param id 机器人ID
     * @return 机器人配置（不存在返回 null）
     */
    AiBot getById(String id);
}
