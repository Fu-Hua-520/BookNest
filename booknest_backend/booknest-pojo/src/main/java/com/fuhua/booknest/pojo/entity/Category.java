package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    /**
     * 书吧图标 URL（OSS 地址）。为空时前端用吧名首字生成单字方块兜底，
     * 所以这里允许为 null，不要填成空串以外的占位。
     */
    private String icon;
    /**
     * 【遗留字段】书吧已取消两级结构，所有吧平级，本字段恒为 null。
     * 保留是为了兼容 category 表的历史列与既有 SQL，新代码不要再用它做判层级。
     */
    private String parentId;
    private Integer sortOrder;
    private String description;
    private Integer status;
    /**
     * 吧主用户ID（「书吧」概念下 = 申请创建该吧的人）。
     * 管理端后台直接建的吧可以为空，表示官方吧。
     */
    private String ownerId;
    /**
     * 创建审核状态：0-待审核 1-已通过 2-已驳回。
     * 既有的分类数据在升级脚本里统一回填为 1，保证老数据不受影响。
     */
    private Integer auditStatus;
    /** 驳回原因（仅 auditStatus=2 时有值） */
    private String rejectReason;
    /**
     * 吧主昵称 / 头像。⚠️ <b>不是 category 表的列</b>，而是列表查询 left join user 带出的展示字段
     * （见 {@code CategoryMapper.listAll / listByOwnerId}、{@code BarMemberMapper.listFollowedBars}）。
     *
     * <p>之所以放在实体上：书吧列表每一行都要显示吧主，如果不在 SQL 里带出，
     * Service 就只能「循环里逐条 getUserById」补，广场 / 热门榜 / 搜索 / 申请列表全都会变成 N+1。
     * 放在这里等于让「查询顺路带人」成为这一层的约定。</p>
     *
     * <p>因此用 <b>不带 join 的查询</b>（如 {@code selectById}）取到的 Category，这两个字段恒为 null。
     * 组装 BarVO 的地方依赖「调用方一定来自上面那三条带 join 的查询」这条不变量 ——
     * 新增列表查询时要一并补 join，否则吧主会显示成空白。</p>
     */
    private String ownerName;
    private String ownerAvatar;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
