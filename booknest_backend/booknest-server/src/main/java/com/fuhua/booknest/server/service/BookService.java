package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.entity.Book;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface BookService {

    /**
     * 创建书籍
     * @param book 书籍信息
     * @return 创建后的书籍（含生成的 id）
     */
    Book createBook(Book book);

    /**
     * 根据关键字模糊搜索书籍
     * @param keyword 书名关键字
     * @return 书籍列表
     */
    List<Book> searchBooks(String keyword);

    /**
     * 根据 ID 查询书籍
     * @param id 书籍ID
     * @return 书籍信息
     */
    Book getBookById(String id);

    /**
     * 根据 ISBN 从外部源自动补全书籍信息（不落库）
     * @param isbn ISBN号
     * @return 补全后的书籍（含 source 标记）
     */
    Book fetchBookByIsbn(String isbn);

    /**
     * 管理后台：分页查询书籍列表
     * @param keyword 书名关键字（可空）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageInfo<Book> listBooks(String keyword, Integer page, Integer pageSize);

    /**
     * 更新书籍信息
     * @param book 书籍信息（含 id）
     */
    void updateBook(Book book);

    /**
     * 删除书籍
     * @param id 书籍ID
     */
    void deleteBook(String id);
}
