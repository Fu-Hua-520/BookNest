package com.fuhua.booknest.common.constant;

/**
 * 帖子列表排序方式常量。
 *
 * 之所以收敛成白名单常量而不是让前端直接传 SQL 片段：
 * 排序字段会直接拼进 order by（无法用 #{} 参数化），必须由服务端枚举控制，
 * 否则等于把 order by 注入的口子开给客户端。
 */
public class PostSortConstant {

    /** 最新：按发布时间倒序（默认） */
    public static final String LATEST = "latest";

    /** 推荐：综合热度 = 阅读 + 点赞 x 5 + 评论 x 3 */
    public static final String HOT = "hot";

    /** 热门：按阅读量倒序 */
    public static final String VIEWS = "views";

    /** 精华：按点赞量倒序（点赞为 0 的帖子自然沉底） */
    public static final String ESSENCE = "essence";

    /** 热议：按评论数倒序 */
    public static final String COMMENTS = "comments";

    /**
     * 校验并归一化排序参数
     * @param sort 前端传入的排序标识（可空）
     * @return 合法的排序标识，非法值一律回落到默认的「最新」
     */
    public static String normalize(String sort) {
        if (sort == null) {
            return LATEST;
        }
        String value = sort.trim().toLowerCase();
        switch (value) {
            case HOT:
            case VIEWS:
            case ESSENCE:
            case COMMENTS:
            case LATEST:
                return value;
            default:
                return LATEST;
        }
    }
}
