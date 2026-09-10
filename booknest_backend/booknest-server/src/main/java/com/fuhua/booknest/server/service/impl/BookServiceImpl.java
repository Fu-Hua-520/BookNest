package com.fuhua.booknest.server.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Book;
import com.fuhua.booknest.server.mapper.BookMapper;
import com.fuhua.booknest.server.service.BookService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class BookServiceImpl implements BookService {

    @Autowired
    private BookMapper bookMapper;

    // Spring 6 自带 RestClient，用于调用外部书籍信息 API（显式设置超时，避免外部无响应永久阻塞线程）
    private final RestClient restClient = buildRestClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构建带连接/读取超时的 RestClient
     */
    private static RestClient buildRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Book createBook(Book book) {
        // 入参校验：书名不能为空
        if (book.getTitle() == null || book.getTitle().trim().isEmpty()) {
            throw new BaseException("书名不能为空");
        }

        // 入参校验：ISBN 不能为空
        String isbn = book.getIsbn();
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new BaseException("ISBN 不能为空");
        }
        isbn = isbn.trim();

        // ISBN 查重，避免依赖 SQL 唯一约束兜底
        if (bookMapper.selectByIsbn(isbn) != null) {
            throw new BaseException("该 ISBN 已存在");
        }

        book.setIsbn(isbn);
        book.setId(UUID.randomUUID().toString());
        book.setCreateTime(LocalDateTime.now());
        bookMapper.insert(book);
        return book;
    }

    @Override
    public List<Book> searchBooks(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return bookMapper.selectByTitle(keyword);
    }

    @Override
    public Book getBookById(String id) {
        Book book = bookMapper.selectById(id);
        if (book == null) {
            throw new BaseException("书籍不存在");
        }
        return book;
    }

    /**
     * 根据 ISBN 从外部源自动补全书籍信息：优先 Google Books，兜底 Open Library，均不落库
     * @param isbn ISBN号
     * @return 补全后的书籍（含 source 标记）
     */
    @Override
    public Book fetchBookByIsbn(String isbn) {
        if (isbn == null || isbn.trim().isEmpty()) {
            throw new BaseException("ISBN 不能为空");
        }
        isbn = isbn.trim();

        Book book = fetchFromGoogleBooks(isbn);
        if (book == null) {
            book = fetchFromOpenLibrary(isbn);
        }
        if (book == null) {
            throw new BaseException("未找到该 ISBN 对应的书籍信息");
        }
        book.setIsbn(isbn);
        return book;
    }

    /**
     * 从 Google Books 查询书籍信息
     * @param isbn ISBN号
     * @return 书籍信息；确无结果返回 null，调用/解析失败抛 BaseException
     */
    private Book fetchFromGoogleBooks(String isbn) {
        String json;
        try {
            String url = "https://www.googleapis.com/books/v1/volumes?q=isbn:" + isbn;
            json = restClient.get().uri(url).retrieve().body(String.class);
        } catch (Exception e) {
            log.error("Google Books 调用失败，isbn: {}", isbn, e);
            throw new BaseException("书籍信息源暂不可用，请稍后再试");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Google Books 返回解析失败，isbn: {}", isbn, e);
            throw new BaseException("书籍信息源暂不可用，请稍后再试");
        }

        int totalItems = root.path("totalItems").asInt();
        JsonNode items = root.path("items");
        if (totalItems == 0 || !items.isArray() || items.isEmpty()) {
            return null;
        }

        JsonNode volumeInfo = items.get(0).path("volumeInfo");

        // 标题：存在副标题时拼接
        String title = volumeInfo.path("title").asText(null);
        String subtitle = volumeInfo.path("subtitle").asText(null);
        if (subtitle != null && !subtitle.isEmpty()) {
            title = title + ": " + subtitle;
        }

        // 作者：多作者用逗号拼接
        String author = joinArray(volumeInfo.path("authors"));

        String coverUrl = volumeInfo.path("imageLinks").path("thumbnail").asText(null);
        String description = volumeInfo.path("description").asText(null);
        String publisher = volumeInfo.path("publisher").asText(null);
        String publishDate = volumeInfo.path("publishedDate").asText(null);

        // 评分与评分人数可能缺省，且需校验节点类型
        BigDecimal rating = null;
        JsonNode ratingNode = volumeInfo.path("averageRating");
        if (ratingNode.isNumber()) {
            rating = ratingNode.decimalValue();
        }

        Integer ratingCount = null;
        JsonNode ratingCountNode = volumeInfo.path("ratingsCount");
        if (ratingCountNode.canConvertToInt()) {
            ratingCount = ratingCountNode.asInt();
        }

        return Book.builder()
                .title(title)
                .author(author)
                .coverUrl(coverUrl)
                .description(description)
                .publisher(publisher)
                .publishDate(publishDate)
                .rating(rating)
                .ratingCount(ratingCount)
                .source("google")
                .build();
    }

    /**
     * 从 Open Library 查询书籍信息（Google Books 兜底）
     * @param isbn ISBN号
     * @return 书籍信息；确无结果返回 null，调用/解析失败抛 BaseException
     */
    private Book fetchFromOpenLibrary(String isbn) {
        String json;
        try {
            String url = "https://openlibrary.org/api/books?bibkeys=ISBN:" + isbn + "&format=json&jscmd=data";
            json = restClient.get().uri(url).retrieve().body(String.class);
        } catch (Exception e) {
            log.error("Open Library 调用失败，isbn: {}", isbn, e);
            throw new BaseException("书籍信息源暂不可用，请稍后再试");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Open Library 返回解析失败，isbn: {}", isbn, e);
            throw new BaseException("书籍信息源暂不可用，请稍后再试");
        }

        JsonNode node = root.path("ISBN:" + isbn);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }

        String title = node.path("title").asText(null);

        // 作者：取 authors[].name，多个用逗号拼接
        String author = null;
        JsonNode authors = node.path("authors");
        if (authors.isArray() && !authors.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (JsonNode a : authors) {
                String name = a.path("name").asText(null);
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
            author = String.join(", ", names);
        }

        String coverUrl = node.path("cover").path("medium").asText(null);

        // description 可能是 {value} 对象，也可能是字符串
        String description = null;
        JsonNode descNode = node.path("description");
        if (descNode.isObject()) {
            description = descNode.path("value").asText(null);
        } else if (descNode.isTextual()) {
            description = descNode.asText();
        }

        // 出版社：取 publishers[0].name
        String publisher = null;
        JsonNode publishers = node.path("publishers");
        if (publishers.isArray() && !publishers.isEmpty()) {
            publisher = publishers.get(0).path("name").asText(null);
        }

        String publishDate = node.path("publish_date").asText(null);

        return Book.builder()
                .title(title)
                .author(author)
                .coverUrl(coverUrl)
                .description(description)
                .publisher(publisher)
                .publishDate(publishDate)
                .source("openlibrary")
                .build();
    }

    /**
     * 将 JsonNode 字符串数组拼接为逗号分隔字符串
     * @param arrayNode 数组节点
     * @return 拼接结果，空则返回 null
     */
    private String joinArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            values.add(node.asText());
        }
        return String.join(", ", values);
    }

    /**
     * 管理后台：分页查询书籍列表
     * @param keyword 书名关键字（可空）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public PageInfo<Book> listBooks(String keyword, Integer page, Integer pageSize) {
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }

        PageHelper.startPage(page, pageSize);
        List<Book> list = bookMapper.list(keyword);
        return new PageInfo<>(list);
    }

    /**
     * 更新书籍信息
     * @param book 书籍信息（含 id）
     */
    @Override
    public void updateBook(Book book) {
        if (book.getId() == null || book.getId().trim().isEmpty()) {
            throw new BaseException("书籍ID不能为空");
        }
        if (bookMapper.selectById(book.getId()) == null) {
            throw new BaseException("书籍不存在");
        }
        // 修改 ISBN 时校验唯一性，避免依赖 SQL 唯一约束兜底
        if (book.getIsbn() != null && !book.getIsbn().trim().isEmpty()) {
            Book existing = bookMapper.selectByIsbn(book.getIsbn().trim());
            if (existing != null && !existing.getId().equals(book.getId())) {
                throw new BaseException("该 ISBN 已存在");
            }
            book.setIsbn(book.getIsbn().trim());
        }
        book.setUpdateTime(LocalDateTime.now());
        bookMapper.update(book);
    }

    /**
     * 删除书籍
     * @param id 书籍ID
     */
    @Override
    public void deleteBook(String id) {
        if (bookMapper.selectById(id) == null) {
            throw new BaseException("书籍不存在");
        }
        bookMapper.deleteById(id);
    }
}
