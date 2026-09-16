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

    /**
     * 批量查询多篇帖子的「帖子ID + 标签ID」扁平行（帖子列表组装专用）。
     *
     * <p><b>为什么不返回 {@code Map<postId, List<tagId>>}：</b>MyBatis 的 {@code @MapKey}
     * 语义是 {@code map.put(key, row)} —— 同一个 key 出现多次时是<b>覆盖</b>而不是合并成 List，
     * 所以直接用 @MapKey 表达「一对多」会静默丢掉除最后一条外的所有标签。
     * 这里改为返回扁平行，由调用方（PostServiceImpl.prefetchForPosts）在内存里做分组，
     * 语义确定、可读性也好。</p>
     *
     * @param postIds 帖子ID集合
     * @return 每行含 postId 与 tagId，可能为空列表
     */
    List<PostTagRow> listTagRowsByPostIds(@Param("postIds") List<String> postIds);

    /**
     * 扁平行载体：帖子ID + 标签ID。
     * 用静态内部类而不是 {@code Map<String,Object>}，是为了让字段类型在编译期就确定，
     * 不必在调用方做 {@code (String) row.get("tagId")} 这类容易写错列名的强转。
     */
    class PostTagRow {
        private String postId;
        private String tagId;

        public String getPostId() {
            return postId;
        }

        public void setPostId(String postId) {
            this.postId = postId;
        }

        public String getTagId() {
            return tagId;
        }

        public void setTagId(String tagId) {
            this.tagId = tagId;
        }
    }

    /**
     * 统计使用指定标签的帖子数（删除标签前的引用检查）
     * @param tagId 标签ID
     * @return 关联帖子数
     */
    int countByTagId(@Param("tagId") String tagId);
}
