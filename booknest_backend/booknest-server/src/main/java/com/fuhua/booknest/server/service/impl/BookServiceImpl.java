package com.fuhua.booknest.server.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Book;
import com.fuhua.booknest.server.mapper.BookMapper;
import com.fuhua.booknest.server.service.BookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    // Spring 6 自带 RestClient，用于调用外部书籍信息 API
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Book createBook(Book book) {
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
     * @return 书籍信息，未找到或异常返回 null
     */
    private Book fetchFromGoogleBooks(String isbn) {
        try {
            String url = "https://www.googleapis.com/books/v1/volumes?q=isbn:" + isbn;
            String json = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);

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

            // 评分与评分人数可能缺省
            BigDecimal rating = volumeInfo.hasNonNull("averageRating")
                    ? volumeInfo.path("averageRating").decimalValue() : null;
            Integer ratingCount = volumeInfo.hasNonNull("ratingsCount")
                    ? volumeInfo.path("ratingsCount").asInt() : null;

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
        } catch (Exception e) {
            log.warn("Google Books 查询失败或解析异常，isbn: {}, 原因: {}", isbn, e.getMessage());
            return null;
        }
    }

    /**
     * 从 Open Library 查询书籍信息（Google Books 兜底）
     * @param isbn ISBN号
     * @return 书籍信息，未找到或异常返回 null
     */
    private Book fetchFromOpenLibrary(String isbn) {
        try {
            String url = "https://openlibrary.org/api/books?bibkeys=ISBN:" + isbn + "&format=json&jscmd=data";
            String json = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(json);

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
        } catch (Exception e) {
            log.warn("Open Library 查询失败或解析异常，isbn: {}, 原因: {}", isbn, e.getMessage());
            return null;
        }
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
}
