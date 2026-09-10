package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.BooklistAddItemDTO;
import com.fuhua.booknest.pojo.dto.BooklistCreateDTO;
import com.fuhua.booknest.pojo.dto.BooklistUpdateDTO;
import com.fuhua.booknest.pojo.vo.BooklistDetailVO;
import com.fuhua.booknest.pojo.vo.BooklistItemVO;
import com.fuhua.booknest.pojo.vo.BooklistVO;
import com.fuhua.booknest.server.service.BooklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/booklist")
@Slf4j
@Tag(name = "书单相关接口")
public class BooklistController {

    @Autowired
    private BooklistService booklistService;

    /**
     * 创建书单
     * @param dto 创建信息
     * @return 书单卡片 VO
     */
    @PostMapping
    @Operation(summary = "创建书单")
    public Result<BooklistVO> createBooklist(@RequestBody @Valid BooklistCreateDTO dto) {
        return Result.success(booklistService.createBooklist(dto));
    }

    /**
     * 查询书单详情
     * @param id 书单ID
     * @return 书单详情 VO
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询书单详情")
    public Result<BooklistDetailVO> getBooklistDetail(@PathVariable String id) {
        return Result.success(booklistService.getBooklistDetail(id));
    }

    /**
     * 分页查询公开书单列表
     * @param userId 创建者用户ID（可空）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 书单卡片列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询公开书单列表")
    public Result<List<BooklistVO>> listBooklists(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(booklistService.listBooklists(userId, page, pageSize));
    }

    /**
     * 查询我的书单列表
     * @return 书单卡片列表
     */
    @GetMapping("/my")
    @Operation(summary = "查询我的书单列表")
    public Result<List<BooklistVO>> listMyBooklists() {
        return Result.success(booklistService.listMyBooklists());
    }

    /**
     * 更新书单
     * @param id 书单ID
     * @param dto 更新信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新书单")
    public Result<String> updateBooklist(@PathVariable String id, @RequestBody @Valid BooklistUpdateDTO dto) {
        booklistService.updateBooklist(id, dto);
        return Result.success("更新成功");
    }

    /**
     * 删除书单
     * @param id 书单ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除书单")
    public Result<String> deleteBooklist(@PathVariable String id) {
        booklistService.deleteBooklist(id);
        return Result.success("删除成功");
    }

    /**
     * 向书单添加条目
     * @param id 书单ID
     * @param dto 添加信息
     * @return 条目 VO
     */
    @PostMapping("/{id}/item")
    @Operation(summary = "向书单添加条目")
    public Result<BooklistItemVO> addItem(@PathVariable String id, @RequestBody @Valid BooklistAddItemDTO dto) {
        return Result.success(booklistService.addItem(id, dto));
    }

    /**
     * 删除书单条目
     * @param id 书单ID
     * @param itemId 条目ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}/item/{itemId}")
    @Operation(summary = "删除书单条目")
    public Result<String> removeItem(@PathVariable String id, @PathVariable String itemId) {
        booklistService.removeItem(id, itemId);
        return Result.success("删除成功");
    }
}
