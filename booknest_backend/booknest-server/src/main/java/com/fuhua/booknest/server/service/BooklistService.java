package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.BooklistAddItemDTO;
import com.fuhua.booknest.pojo.dto.BooklistCreateDTO;
import com.fuhua.booknest.pojo.dto.BooklistUpdateDTO;
import com.fuhua.booknest.pojo.vo.BooklistDetailVO;
import com.fuhua.booknest.pojo.vo.BooklistItemVO;
import com.fuhua.booknest.pojo.vo.BooklistVO;

import java.util.List;

public interface BooklistService {

    /**
     * 创建书单
     * @param dto 创建信息
     * @return 书单卡片 VO
     */
    BooklistVO createBooklist(BooklistCreateDTO dto);

    /**
     * 根据ID查询书单（含私密访问控制）
     * @param id 书单ID
     * @return 书单卡片 VO
     */
    BooklistVO getBooklistById(String id);

    /**
     * 查询书单详情（含条目列表与书籍信息）
     * @param id 书单ID
     * @return 书单详情 VO
     */
    BooklistDetailVO getBooklistDetail(String id);

    /**
     * 分页查询公开书单列表
     * @param userId 创建者用户ID（可空，空则返回所有公开书单）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 书单卡片列表
     */
    List<BooklistVO> listBooklists(String userId, Integer page, Integer pageSize);

    /**
     * 查询当前用户的书单列表
     * @return 书单卡片列表
     */
    List<BooklistVO> listMyBooklists();

    /**
     * 更新书单
     * @param id 书单ID
     * @param dto 更新信息
     */
    void updateBooklist(String id, BooklistUpdateDTO dto);

    /**
     * 删除书单（级联删除条目）
     * @param id 书单ID
     */
    void deleteBooklist(String id);

    /**
     * 向书单添加条目
     * @param booklistId 书单ID
     * @param dto 添加信息
     * @return 条目 VO
     */
    BooklistItemVO addItem(String booklistId, BooklistAddItemDTO dto);

    /**
     * 删除书单条目
     * @param booklistId 书单ID
     * @param itemId 条目ID
     */
    void removeItem(String booklistId, String itemId);
}
