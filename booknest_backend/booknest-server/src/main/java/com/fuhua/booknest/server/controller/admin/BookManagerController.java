package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.entity.Book;
import com.fuhua.booknest.server.service.BookService;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 - 书籍库管理
 */
@RestController
@RequestMapping("/admin/book")
@Slf4j
@Tag(name = "管理后台-书籍库")
public class BookManagerController {

    @Autowired
    private BookService bookService;

    /**
     * 分页查询书籍列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询书籍列表")
    public Result<PageInfo<Book>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(bookService.listBooks(keyword, page, pageSize));
    }

    /**
     * 新增书籍
     */
    @PostMapping
    @Operation(summary = "新增书籍")
    public Result<String> create(@RequestBody Book book) {
        Book created = bookService.createBook(book);
        return Result.success(created.getId());
    }

    /**
     * 更新书籍
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新书籍")
    public Result<String> update(@PathVariable String id, @RequestBody Book book) {
        book.setId(id);
        bookService.updateBook(book);
        return Result.success("更新成功");
    }

    /**
     * 删除书籍
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除书籍")
    public Result<String> delete(@PathVariable String id) {
        bookService.deleteBook(id);
        return Result.success("删除成功");
    }
}
