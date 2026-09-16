package com.fuhua.booknest.common.constant;

/**
 * 书吧社区化常量：角色、经验规则、等级阈值
 *
 * <p>集中放这里的理由：角色判断在「隐藏帖子 / 任命管理员 / 设称号」三处都要用，
 * 经验与等级规则在「加经验」和「算等级」两处都要用，散在各处必然对不上。</p>
 */
public class BarConstant {

    /** 吧主（category.owner_id 命中） */
    public static final String ROLE_OWNER = "OWNER";
    /** 管理员（bar_moderator 命中） */
    public static final String ROLE_MODERATOR = "MODERATOR";
    /** 普通吧内成员（关注了但不是吧务） */
    public static final String ROLE_MEMBER = "MEMBER";
    /** 与这个吧没有关系（未关注、也不是吧务） */
    public static final String ROLE_NONE = "NONE";

    /** 发一帖获得的吧内经验 */
    public static final int EXP_PUBLISH_POST = 10;
    /** 发一条评论获得的吧内经验 */
    public static final int EXP_COMMENT = 5;
    /** 点赞获得的吧内经验（取消点赞时扣回同额，防止反复点赞刷经验） */
    public static final int EXP_LIKE = 2;
    /** 每个用户每天在一个吧里最多能拿到的经验 */
    public static final int DAILY_EXP_LIMIT = 100;

    /** 最高等级 */
    public static final int MAX_LEVEL = 10;

    /**
     * 各等级所需的累计经验（下标 0 对应 Lv.1）。
     * 达到或超过阈值即进入该等级，超过 Lv.10 阈值后仍是 Lv.10。
     */
    private static final int[] LEVEL_THRESHOLDS = {0, 50, 150, 300, 600, 1000, 1500, 2500, 4000, 6000};

    /**
     * 累计经验换算等级（1 ~ {@link #MAX_LEVEL}）
     * @param exp 累计经验（null 视为 0）
     * @return 等级
     */
    public static int levelOf(Integer exp) {
        int value = exp == null || exp < 0 ? 0 : exp;
        int level = 1;
        for (int i = 0; i < LEVEL_THRESHOLDS.length; i++) {
            if (value >= LEVEL_THRESHOLDS[i]) {
                level = i + 1;
            }
        }
        return Math.min(level, MAX_LEVEL);
    }

    /**
     * 升到下一级还需要的经验
     * @param exp 当前累计经验
     * @return 还差多少；已满级返回 null
     */
    public static Integer nextLevelExp(Integer exp) {
        int level = levelOf(exp);
        if (level >= MAX_LEVEL) {
            return null;
        }
        int value = exp == null || exp < 0 ? 0 : exp;
        return Math.max(0, LEVEL_THRESHOLDS[level] - value);
    }

    /**
     * 当前等级区间的起始经验（进度条用）
     * @param level 等级
     * @return 该等级的经验下限
     */
    public static int levelFloor(int level) {
        int idx = Math.max(0, Math.min(level - 1, LEVEL_THRESHOLDS.length - 1));
        return LEVEL_THRESHOLDS[idx];
    }
}
