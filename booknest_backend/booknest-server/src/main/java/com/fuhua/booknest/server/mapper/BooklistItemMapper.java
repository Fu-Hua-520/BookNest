package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.BooklistItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BooklistItemMapper {

    /**
     * 插入书单条目
     * @param booklistItem 书单条目信息
     */
    void insert(BooklistItem booklistItem);

    /**
     * 批量插入书单条目
     * @param list 书单条目列表
     */
    void batchInsert(@Param("list") List<BooklistItem> list);

    /**
     * 根据书单ID删除其全部条目
     * @param booklistId 书单ID
     */
    void deleteByBooklistId(@Param("booklistId") String booklistId);

    /**
     * 根据书单ID查询其条目列表
     * @param booklistId 书单ID
     * @return 书单条目列表
     */
    List<BooklistItem> listByBooklistId(@Param("booklistId") String booklistId);

    /**
     * 根据条目ID查询书单条目
     * @param id 条目ID
     * @return 书单条目
     */
    BooklistItem selectById(@Param("id") String id);

    /**
     * 根据条目ID删除书单条目
     * @param id 条目ID
     */
    void deleteById(@Param("id") String id);
}
