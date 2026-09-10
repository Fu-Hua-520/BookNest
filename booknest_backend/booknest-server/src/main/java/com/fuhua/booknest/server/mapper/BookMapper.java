package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.Book;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookMapper {

    /**
     * 插入新书籍
     * @param book 书籍信息
     */
    void insert(Book book);

    /**
     * 根据书籍ID查询书籍
     * @param id 书籍ID
     * @return 书籍信息
     */
    Book selectById(@Param("id") String id);

    /**
     * 根据ISBN查询书籍
     * @param isbn ISBN号
     * @return 书籍信息
     */
    Book selectByIsbn(@Param("isbn") String isbn);

    /**
     * 根据书名模糊查询书籍
     * @param title 书名关键字
     * @return 书籍列表
     */
    List<Book> selectByTitle(@Param("title") String title);

    /**
     * 更新书籍信息
     * @param book 书籍信息
     */
    void update(Book book);

    /**
     * 根据书籍ID删除书籍
     * @param id 书籍ID
     */
    void deleteById(@Param("id") String id);

    /**
     * 管理后台：关键字条件查询书籍列表（供 PageHelper 分页）
     * @param keyword 书名关键字（可空）
     * @return 书籍列表
     */
    List<Book> list(@Param("keyword") String keyword);
}
