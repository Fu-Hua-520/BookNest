package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.PostTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostTagMapper {

    /**
     * 插入帖子标签关联
     * @param postTag 帖子标签关联信息
     */
    void insert(PostTag postTag);

    /**
     * 批量插入帖子标签关联
     * @param list 帖子标签关联列表
     */
    void batchInsert(@Param("list") List<PostTag> list);

    /**
     * 根据帖子ID删除其全部标签关联
     * @param postId 帖子ID
     */
    void deleteByPostId(@Param("postId") String postId);

    /**
     * 根据帖子ID查询其标签ID列表
     * @param postId 帖子ID
     * @return 标签ID列表
     */
    List<String> listTagIdsByPostId(@Param("postId") String postId);
}
