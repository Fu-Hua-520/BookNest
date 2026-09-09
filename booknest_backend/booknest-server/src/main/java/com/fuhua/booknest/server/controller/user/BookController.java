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

@RestController
@RequestMapping("/book")
@Slf4j
@Tag(name = "书籍相关接口")
public class BookController {

    @Autowired
    private BookService bookService;

    /**
     * 创建书籍
     * @param book 书籍信息
     * @return 新书 id
     */
    @PostMapping
    @Operation(summary = "创建书籍")
    public Result<String> createBook(@RequestBody Book book) {
        Book created = bookService.createBook(book);
        return Result.success(created.getId());
    }

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
