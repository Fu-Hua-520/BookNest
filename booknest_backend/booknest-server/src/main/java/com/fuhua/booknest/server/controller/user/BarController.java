package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.BarLevelTitleVO;
import com.fuhua.booknest.pojo.vo.BarMemberVO;
import com.fuhua.booknest.pojo.vo.BarModeratorVO;
import com.fuhua.booknest.pojo.vo.BarVO;
import com.fuhua.booknest.server.service.BarService;
import com.fuhua.booknest.server.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 书吧社区化接口（关注 / 等级 / 称号 / 吧务）
 *
 * <p>为什么挂在 <b>/bar</b> 而不是 /category：/category 前缀在拦截器里属于
 * 「公开只读放行」，游客也能命中；这些接口全都必须登录（或至少要知道当前用户是谁），
 * 放在 /category 下会出现「没带令牌也被放行，进去才发现 BaseContext 是空」的尴尬。</p>
 */
@RestController
@RequestMapping("/bar")
@Slf4j
@Tag(name = "书吧社区相关接口")
public class BarController {

    @Autowired
    private BarService barService;

    @Autowired
    private CategoryService categoryService;

    /**
     * 我关注的书吧（侧边栏「我关注的吧」）
     * <p>挂在 /bar 而不是 /category/my-bars：/category 前缀的 GET 是公开只读放行的，
     * 游客请求会带着空身份进来；这里必须登录，语义也对得上。</p>
     * @return 书吧列表（按关注时间倒序）
     */
    @GetMapping("/my-bars")
    @Operation(summary = "查询我关注的书吧")
    public Result<List<BarVO>> myBars() {
        return Result.success(categoryService.listMyBars());
    }

    /**
     * 我在这个吧里的身份与等级
     * @param barId 书吧ID
     * @return 成员视图（未登录时 role = NONE）
     */
    @GetMapping("/{barId}/membership")
    @Operation(summary = "查询我在某吧的身份与等级")
    public Result<BarMemberVO> myMembership(@PathVariable String barId) {
        return Result.success(barService.myMembership(barId));
    }

    /**
     * 关注书吧（关注即入吧）
     * @param barId 书吧ID
     * @return 关注后的成员视图
     */
    @PostMapping("/{barId}/follow")
    @Operation(summary = "关注书吧")
    public Result<BarMemberVO> follow(@PathVariable String barId) {
        barService.follow(barId);
        return Result.success(barService.myMembership(barId));
    }

    /**
     * 取消关注（退吧）
     * @param barId 书吧ID
     * @return 操作后的成员视图
     */
    @DeleteMapping("/{barId}/follow")
    @Operation(summary = "取消关注书吧")
    public Result<BarMemberVO> unfollow(@PathVariable String barId) {
        barService.unfollow(barId);
        return Result.success(barService.myMembership(barId));
    }

    /**
     * 管理员列表
     * @param barId 书吧ID
     * @return 管理员
     */
    @GetMapping("/{barId}/moderators")
    @Operation(summary = "查询书吧管理员")
    public Result<List<BarModeratorVO>> listModerators(@PathVariable String barId) {
        return Result.success(barService.listModerators(barId));
    }

    /**
     * 任命管理员（仅吧主）
     * @param barId 书吧ID
     * @param body 只读取 userId
     * @return 最新管理员列表
     */
    @PostMapping("/{barId}/moderators")
    @Operation(summary = "任命书吧管理员")
    public Result<List<BarModeratorVO>> addModerator(@PathVariable String barId,
                                                     @RequestBody(required = false) Map<String, String> body) {
        String userId = body == null ? null : body.get("userId");
        barService.addModerator(barId, userId);
        return Result.success(barService.listModerators(barId));
    }

    /**
     * 撤销管理员（仅吧主）
     * @param barId 书吧ID
     * @param userId 用户ID
     * @return 最新管理员列表
     */
    @DeleteMapping("/{barId}/moderators/{userId}")
    @Operation(summary = "撤销书吧管理员")
    public Result<List<BarModeratorVO>> removeModerator(@PathVariable String barId,
                                                        @PathVariable String userId) {
        barService.removeModerator(barId, userId);
        return Result.success(barService.listModerators(barId));
    }

    /**
     * 等级称号列表（没设过的等级不会出现）
     * @param barId 书吧ID
     * @return 称号
     */
    @GetMapping("/{barId}/titles")
    @Operation(summary = "查询书吧等级称号")
    public Result<List<BarLevelTitleVO>> listTitles(@PathVariable String barId) {
        return Result.success(barService.listTitles(barId));
    }

    /**
     * 整批重设等级称号（仅吧主）
     * @param barId 书吧ID
     * @param titles 称号列表，传空数组表示清空
     * @return 最新称号列表
     */
    @PutMapping("/{barId}/titles")
    @Operation(summary = "设置书吧等级称号")
    public Result<List<BarLevelTitleVO>> setTitles(@PathVariable String barId,
                                                   @RequestBody(required = false) List<BarLevelTitleVO> titles) {
        barService.setTitles(barId, titles);
        return Result.success(barService.listTitles(barId));
    }

    /**
     * 吧主 / 管理员隐藏或恢复吧内帖子
     * @param barId 书吧ID
     * @param postId 帖子ID
     * @param body 只读取 visible（true 恢复 / false 隐藏）
     * @return null
     */
    @PutMapping("/{barId}/posts/{postId}/visible")
    @Operation(summary = "隐藏或恢复吧内帖子")
    public Result<String> setPostVisible(@PathVariable String barId,
                                         @PathVariable String postId,
                                         @RequestBody(required = false) Map<String, Object> body) {
        Object raw = body == null ? null : body.get("visible");
        // 缺省按隐藏处理：这个接口最常见的调用就是「隐藏」，少传一个参数不该变成恢复
        boolean visible = Boolean.TRUE.equals(raw);
        barService.setPostVisible(barId, postId, visible);
        return Result.success();
    }
}
