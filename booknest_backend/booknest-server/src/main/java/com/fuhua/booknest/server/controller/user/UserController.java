package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.UserLoginDTO;
import com.fuhua.booknest.pojo.dto.UserRegisterDTO;
import com.fuhua.booknest.pojo.vo.BrowseHistoryVO;
import com.fuhua.booknest.pojo.vo.UserBriefVO;
import com.fuhua.booknest.pojo.vo.UserLoginVO;
import com.fuhua.booknest.pojo.vo.UserProfileVO;
import com.fuhua.booknest.server.service.BrowseHistoryService;
import com.fuhua.booknest.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "用户相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private BrowseHistoryService browseHistoryService;

    /**
     * 用户注册
     * @param userRegisterDTO 注册信息
     * @return 注册结果
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<String> userRegister(@RequestBody @Valid UserRegisterDTO userRegisterDTO) {
        log.info("用户注册：phone={}, email={}", userRegisterDTO.getPhone(), userRegisterDTO.getEmail());

        userService.userRegister(userRegisterDTO);

        log.info("用户注册成功");
        return Result.success("注册成功");
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<UserLoginVO> userLogin(@RequestBody @Valid UserLoginDTO userLoginDTO){
        // 仅打印登录标识（邮箱），不打印密码，避免日志泄露明文密码
        log.info("用户登录：email={}", userLoginDTO.getEmail());
        UserLoginVO userLoginVO=userService.userLogin(userLoginDTO);
        log.info("用户登录成功");
        return Result.success(userLoginVO);
    }

    /**
     * 更新我的资料（昵称 / 头像）
     *
     * ⚠️ 目标用户一律取 BaseContext 里的登录态，**不接受前端传 userId** ——
     * 否则任何人都能改别人的昵称和头像。字段以「传 null 即不改」为约定。
     *
     * @param body 可含 username / avatar
     * @return 更新后的资料
     */
    @PutMapping("/profile")
    @Operation(summary = "更新我的资料")
    public Result<UserProfileVO> updateProfile(@RequestBody(required = false) Map<String, String> body) {
        String username = body == null ? null : body.get("username");
        String avatar = body == null ? null : body.get("avatar");
        UserProfileVO profile = userService.updateProfile(BaseContext.getCurrentId(), username, avatar);
        log.info("用户资料更新完成：userId={}", profile == null ? null : profile.getId());
        return Result.success(profile);
    }

    /**
     * 搜索用户（吧主任命管理员时挑人用）
     *
     * <p>只返回 id / 用户名 / 头像，不含手机号与邮箱。需要登录才能调 ——
     * /user 前缀整体在拦截器里，游客直接被挡掉。</p>
     *
     * @param keyword 昵称或账号关键字，空白返回空列表
     * @return 用户简要信息
     */
    @GetMapping("/search")
    @Operation(summary = "搜索用户")
    public Result<List<UserBriefVO>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(userService.searchUsers(keyword, limit));
    }

    /**
     * 我的浏览历史（按最后浏览时间倒序，同一帖子只占一行）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 浏览历史列表
     */
    @GetMapping("/history")
    @Operation(summary = "分页查询我的浏览历史")
    public Result<List<BrowseHistoryVO>> listMyHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(browseHistoryService.listMyHistory(BaseContext.getCurrentId(), page, pageSize));
    }

    /**
     * 删除单条浏览记录
     * @param postId 帖子 ID
     * @return 操作结果
     */
    @DeleteMapping("/history/{postId}")
    @Operation(summary = "删除单条浏览记录")
    public Result<String> removeHistory(@PathVariable String postId) {
        browseHistoryService.removeOne(BaseContext.getCurrentId(), postId);
        return Result.success("已删除");
    }

    /**
     * 清空浏览历史
     * @return 操作结果
     */
    @DeleteMapping("/history")
    @Operation(summary = "清空浏览历史")
    public Result<String> clearHistory() {
        browseHistoryService.clear(BaseContext.getCurrentId());
        return Result.success("已清空");
    }
}
