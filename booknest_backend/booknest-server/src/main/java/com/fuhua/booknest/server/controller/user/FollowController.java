package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.FollowVO;
import com.fuhua.booknest.server.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/follow")
@Slf4j
@Tag(name = "用户关注相关接口")
public class FollowController {

    @Autowired
    private FollowService followService;

    /**
     * 关注用户
     * @param userId 被关注者用户ID
     * @return 关注结果
     */
    @PostMapping("/{userId}")
    @Operation(summary = "关注用户")
    public Result<String> follow(@PathVariable String userId) {
        followService.follow(userId);
        return Result.success("关注成功");
    }

    /**
     * 取消关注
     * @param userId 被关注者用户ID
     * @return 取消关注结果
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "取消关注")
    public Result<String> unfollow(@PathVariable String userId) {
        followService.unfollow(userId);
        return Result.success("已取消关注");
    }

    /**
     * 查询当前用户是否已关注目标用户
     * @param userId 被关注者用户ID
     * @return 是否已关注
     */
    @GetMapping("/{userId}/status")
    @Operation(summary = "查询是否已关注")
    public Result<Boolean> isFollowing(@PathVariable String userId) {
        return Result.success(followService.isFollowing(userId));
    }

    /**
     * 查询用户的关注列表
     * @param userId 用户ID
     * @return 关注列表
     */
    @GetMapping("/{userId}/following")
    @Operation(summary = "查询关注列表")
    public Result<List<FollowVO>> listFollowing(@PathVariable String userId) {
        return Result.success(followService.listFollowing(userId));
    }

    /**
     * 查询用户的粉丝列表
     * @param userId 用户ID
     * @return 粉丝列表
     */
    @GetMapping("/{userId}/followers")
    @Operation(summary = "查询粉丝列表")
    public Result<List<FollowVO>> listFollowers(@PathVariable String userId) {
        return Result.success(followService.listFollowers(userId));
    }
}
