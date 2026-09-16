package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.BarLevelTitleVO;
import com.fuhua.booknest.pojo.vo.BarManageVO;
import com.fuhua.booknest.pojo.vo.BarModeratorVO;
import com.fuhua.booknest.server.service.BarService;
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
 * 管理后台 - 书吧吧务管理（吧主 / 管理员 / 等级称号）
 *
 * <p>和用户端 {@code /bar} 那套接口的区别：用户端的吧务操作全部校验「你是不是吧主」，
 * 这里一个都不校验 —— 调用方已经过了管理员令牌 + role=ADMIN 的双重拦截，
 * 而且管理员本来就要能在吧主长期失联时直接接管一个吧。</p>
 *
 * <p>⚠️ 因此这组接口只能挂在 /admin 下。挪到用户端前缀等于谁都能改吧主。</p>
 */
@RestController
@RequestMapping("/admin/bar")
@Slf4j
@Tag(name = "管理后台-书吧吧务")
public class AdminBarController {

    @Autowired
    private BarService barService;

    /**
     * 书吧吧务全貌（吧主 + 管理员 + 等级称号 + 成员数）
     * @param barId 书吧ID
     * @return 吧务视图
     */
    @GetMapping("/{barId}")
    @Operation(summary = "查询书吧吧务信息")
    public Result<BarManageVO> manageInfo(@PathVariable String barId) {
        return Result.success(barService.getManageInfo(barId));
    }

    /**
     * 任命 / 更换吧主。userId 传空表示收回吧主（变成官方吧）。
     * @param barId 书吧ID
     * @param body 只读取 userId
     * @return 最新吧务视图
     */
    @PutMapping("/{barId}/owner")
    @Operation(summary = "设置书吧吧主")
    public Result<BarManageVO> setOwner(@PathVariable String barId,
                                        @RequestBody(required = false) Map<String, String> body) {
        String userId = body == null ? null : body.get("userId");
        barService.adminSetOwner(barId, userId);
        return Result.success(barService.getManageInfo(barId));
    }

    /**
     * 任命管理员
     * @param barId 书吧ID
     * @param body 只读取 userId
     * @return 最新管理员列表
     */
    @PostMapping("/{barId}/moderators")
    @Operation(summary = "任命书吧管理员")
    public Result<List<BarModeratorVO>> addModerator(@PathVariable String barId,
                                                     @RequestBody(required = false) Map<String, String> body) {
        String userId = body == null ? null : body.get("userId");
        barService.adminAddModerator(barId, userId);
        return Result.success(barService.listModerators(barId));
    }

    /**
     * 撤销管理员
     * @param barId 书吧ID
     * @param userId 用户ID
     * @return 最新管理员列表
     */
    @DeleteMapping("/{barId}/moderators/{userId}")
    @Operation(summary = "撤销书吧管理员")
    public Result<List<BarModeratorVO>> removeModerator(@PathVariable String barId,
                                                        @PathVariable String userId) {
        barService.adminRemoveModerator(barId, userId);
        return Result.success(barService.listModerators(barId));
    }

    /**
     * 整批重设等级称号（传空数组表示清空）
     * @param barId 书吧ID
     * @param titles 称号列表
     * @return 最新称号列表
     */
    @PutMapping("/{barId}/titles")
    @Operation(summary = "设置书吧等级称号")
    public Result<List<BarLevelTitleVO>> setTitles(@PathVariable String barId,
                                                   @RequestBody(required = false) List<BarLevelTitleVO> titles) {
        barService.adminSetTitles(barId, titles);
        return Result.success(barService.listTitles(barId));
    }
}
