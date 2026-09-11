package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.entity.Book;
import com.fuhua.booknest.server.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 书籍查询接口（公开，无鉴权）
 * 只提供读操作：搜索 / 详情 / ISBN 补全。
 * 书籍的写操作（新增、修改、删除）统一归管理后台，见 BookManagerController（/admin/book）。
 * 该路径不在任何拦截器覆盖范围内，因此禁止在此新增写接口。
 */
@RestController
@RequestMapping("/book")
@Slf4j
@Tag(name = "书籍相关接口")
public class BookController {

    @Autowired
    private BookService bookService;

    /**
     * 按书名关键字搜索书籍
     * @param keyword 关键字
     * @return 书籍列表
     */
    @GetMapping("/search")
    @Operation(summary = "搜索书籍")
    public Result<List<Book>> search(@RequestParam String keyword) {
        return Result.success(bookService.searchBooks(keyword));
    }

    /**
     * 根据 ID 查询书籍
     * @param id 书籍ID
     * @return 书籍信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询书籍")
    public Result<Book> getById(@PathVariable String id) {
        return Result.success(bookService.getBookById(id));
    }

    /**
     * 根据 ISBN 自动补全书籍信息（外部抓取，含 source 标记）
     * @param isbn ISBN号
     * @return 补全后的书籍信息
     */
    @GetMapping("/isbn/{isbn}")
    @Operation(summary = "根据ISBN补全书籍信息")
    public Result<Book> fetchByIsbn(@PathVariable String isbn) {
        return Result.success(bookService.fetchBookByIsbn(isbn));
    }
}
