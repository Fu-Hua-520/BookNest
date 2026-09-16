package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.BarLevelTitleVO;
import com.fuhua.booknest.pojo.vo.BarManageVO;
import com.fuhua.booknest.pojo.vo.BarMemberVO;
import com.fuhua.booknest.pojo.vo.BarModeratorVO;

import java.util.List;

/**
 * 书吧社区化：关注 / 等级 / 称号 / 吧务
 *
 * <p>接口全部从 {@code BaseContext} 取当前用户，不接受前端传 operatorId ——
 * 权限判定必须走服务端，否则随便改个参数就能当吧主。</p>
 */
public interface BarService {

    /**
     * 我在这个吧里的身份与等级（未登录也返回，role = NONE）
     * @param barId 书吧ID
     * @return 成员视图
     */
    BarMemberVO myMembership(String barId);

    /**
     * 关注书吧（关注即入吧，从 Lv.1 / 0 经验开始）
     * @param barId 书吧ID
     */
    void follow(String barId);

    /**
     * 取消关注（退吧，等级与经验一并作废）
     * @param barId 书吧ID
     */
    void unfollow(String barId);

    /**
     * 某吧的管理员列表
     * @param barId 书吧ID
     * @return 管理员
     */
    List<BarModeratorVO> listModerators(String barId);

    /**
     * 任命管理员（仅吧主）
     * @param barId 书吧ID
     * @param userId 被任命的用户ID
     */
    void addModerator(String barId, String userId);

    /**
     * 撤销管理员（仅吧主）
     * @param barId 书吧ID
     * @param userId 用户ID
     */
    void removeModerator(String barId, String userId);

    /**
     * 某吧的等级称号列表（没设过称号的等级不会出现）
     * @param barId 书吧ID
     * @return 称号
     */
    List<BarLevelTitleVO> listTitles(String barId);

    /**
     * 整批重设等级称号（仅吧主）。传空列表等于清空全部称号。
     * @param barId 书吧ID
     * @param titles 称号（level 必须在 1~10，title 空白表示该等级不设称号）
     */
    void setTitles(String barId, List<BarLevelTitleVO> titles);

    /**
     * 吧主 / 管理员隐藏或恢复吧内帖子
     * @param barId 书吧ID
     * @param postId 帖子ID
     * @param visible true-恢复上架 false-隐藏
     */
    void setPostVisible(String barId, String postId, boolean visible);

    /**
     * 给某人在某个吧加经验（内部调用：发帖 / 评论 / 点赞）。
     *
     * <p>规则：只有本吧成员（已关注）才会累计经验；每日有上限；
     * 传负数用于「取消点赞扣回」，扣回不返还当日额度。</p>
     *
     * @param barId 书吧ID（为 null 时静默跳过）
     * @param userId 用户ID
     * @param amount 经验值，可为负
     */
    void addExp(String barId, String userId, int amount);

    /**
     * 某人是否是这个吧的吧主或管理员
     *
     * <p>帖子详情用它放宽访问控制：吧务隐藏掉一篇帖子后，自己也还得能打开它 ——
     * 否则点了「隐藏」就被自己关在外面，连恢复都点不到。</p>
     *
     * @param barId 书吧ID（为 null 直接返回 false）
     * @param userId 用户ID（为 null 直接返回 false）
     * @return 是吧主或管理员返回 true
     */
    boolean isBarManager(String barId, String userId);

    /* ---------------- 管理后台专用（跳过吧主校验） ----------------
     * 下面这组方法只在 /admin/** 下调用：调用方已经过了管理员令牌 + role=ADMIN 拦截，
     * 所以这里**不再校验吧主身份** —— 管理员本来就要能在吧主失联时接管吧务。
     * ⚠️ 千万不要为了省事把这些方法挂到用户端 /bar 前缀下，那样等于谁都能改吧主。 */

    /**
     * 书吧的吧务全貌（吧主 + 管理员 + 等级称号）
     * @param barId 书吧ID
     * @return 吧务视图
     */
    BarManageVO getManageInfo(String barId);

    /**
     * 任命 / 更换吧主。传 null 表示收回吧主（变成官方吧）。
     * @param barId 书吧ID
     * @param userId 新吧主用户ID（null = 清空）
     */
    void adminSetOwner(String barId, String userId);

    /**
     * 任命管理员
     * @param barId 书吧ID
     * @param userId 用户ID
     */
    void adminAddModerator(String barId, String userId);

    /**
     * 撤销管理员
     * @param barId 书吧ID
     * @param userId 用户ID
     */
    void adminRemoveModerator(String barId, String userId);

    /**
     * 整批重设等级称号
     * @param barId 书吧ID
     * @param titles 称号（level 必须在 1~10，title 空白表示该等级不设称号）
     */
    void adminSetTitles(String barId, List<BarLevelTitleVO> titles);
}
