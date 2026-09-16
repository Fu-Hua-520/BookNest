package com.fuhua.booknest.common.constant;

/**
 * 帖子类型常量。
 *
 * 目前只有两种：
 *   NORMAL —— 普通书评
 *   HELP   —— 求助贴（求推荐、求资源、求解答），首页有独立小版块
 *
 * 与 PostSortConstant 同理，类型值会直接参与 SQL 条件与前端展示判断，
 * 收敛成白名单常量，避免客户端传任意串导致数据不一致。
 */
public class PostTypeConstant {

    /** 普通帖子（默认） */
    public static final String NORMAL = "NORMAL";

    /** 求助贴 */
    public static final String HELP = "HELP";

    /**
     * 校验并归一化帖子类型
     * @param postType 前端传入的类型标识（可空）
     * @return 合法类型，非法值一律回落为 NORMAL
     */
    public static String normalize(String postType) {
        if (postType == null) {
            return NORMAL;
        }
        String value = postType.trim().toUpperCase();
        if (HELP.equals(value)) {
            return HELP;
        }
        return NORMAL;
    }

    /** 是否为求助贴（前端/服务端展示判断复用同一处逻辑） */
    public static boolean isHelp(String postType) {
        return HELP.equalsIgnoreCase(postType == null ? null : postType.trim());
    }
}
