package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.Booklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BooklistMapper {

    /**
     * 插入新书单
     * @param booklist 书单信息
     */
    void insert(Booklist booklist);

    /**
     * 根据书单ID查询书单
     * @param id 书单ID
     * @return 书单信息
     */
    Booklist selectById(@Param("id") String id);

    /**
     * 根据创建者用户ID查询书单列表
     * @param userId 用户ID
     * @return 书单列表
     */
    List<Booklist> selectByUserId(@Param("userId") String userId);

    /**
     * 条件查询书单列表
     * @param visibility 可见性（可空）
     * @param userId 创建者用户ID（可空）
     * @return 书单列表
     */
    List<Booklist> list(@Param("visibility") Integer visibility, @Param("userId") String userId);

    /**
     * 更新书单信息
     * @param booklist 书单信息
     */
    void update(Booklist booklist);

    /**
     * 根据书单ID删除书单
     * @param id 书单ID
     */
    void deleteById(@Param("id") String id);
}
