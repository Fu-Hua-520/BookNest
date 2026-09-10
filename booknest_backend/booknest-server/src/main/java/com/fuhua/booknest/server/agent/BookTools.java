package com.fuhua.booknest.server.agent;

import com.fuhua.booknest.pojo.entity.Book;
import com.fuhua.booknest.pojo.vo.BooklistVO;
import com.fuhua.booknest.pojo.vo.PostDetailVO;
import com.fuhua.booknest.pojo.vo.PostVO;
import com.fuhua.booknest.server.service.BookService;
import com.fuhua.booknest.server.service.BooklistService;
import com.fuhua.booknest.server.service.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 助手工具类（Spring AI Function Calling 工具）
 * <p>
 * 暴露给大模型的业务查询能力：帖子搜索、书籍详情、书单列表、帖子正文。
 */
@Component
@Slf4j
public class BookTools {

    @Autowired
    private PostService postService;

    @Autowired
    private BookService bookService;

    @Autowired
    private BooklistService booklistService;

    /**
     * 按关键词搜索论坛帖子
     * @param keyword 搜索关键词
     * @return 相关帖子列表
     */
    @Tool(name = "searchPosts", description = "按关键词搜索论坛帖子，返回相关帖子列表")
    public List<PostVO> searchPosts(@ToolParam(description = "搜索关键词") String keyword) {
        log.info("AI 工具调用 searchPosts, keyword={}", keyword);
        return postService.searchPosts(keyword, 10);
    }

    /**
     * 根据书籍ID查询书籍详情
     * @param bookId 书籍ID
     * @return 书籍详情
     */
    @Tool(name = "getBookInfo", description = "根据书籍ID查询书籍详情")
    public Book getBookInfo(@ToolParam(description = "书籍ID") String bookId) {
        log.info("AI 工具调用 getBookInfo, bookId={}", bookId);
        return bookService.getBookById(bookId);
    }

    /**
     * 查询公开书单列表
     * @return 书单列表
     */
    @Tool(name = "listBooklists", description = "查询公开书单列表")
    public List<BooklistVO> listBooklists() {
        log.info("AI 工具调用 listBooklists");
        return booklistService.listBooklists(null, 1, 10);
    }

    /**
     * 根据帖子ID阅读帖子全文
     * @param postId 帖子ID
     * @return 帖子详情（含正文）
     */
    @Tool(name = "getPostContent", description = "根据帖子ID阅读帖子全文")
    public PostDetailVO getPostContent(@ToolParam(description = "帖子ID") String postId) {
        log.info("AI 工具调用 getPostContent, postId={}", postId);
        return postService.getPostDetail(postId);
    }
}
