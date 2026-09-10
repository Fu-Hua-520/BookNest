package com.fuhua.booknest.server.vectorstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.server.properties.ZVectorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 AnalyticDB PostgreSQL zvector 扩展的自研 {@link VectorStore} 实现。
 *
 * <p>zvector 是阿里云 AnalyticDB PostgreSQL 的向量检索能力，通过标准 PostgreSQL JDBC
 * 即可操作。本类实现 Spring AI 1.0.9 的 {@code VectorStore} 接口，将文档文本通过
 * {@link EmbeddingModel} 编码为向量后写入 PG 的 {@code real[]} 列，检索时使用
 * zvector 提供的 {@code <=>} 余弦距离操作符。</p>
 *
 * <p><b>向量语义说明：</b></p>
 * <ul>
 *   <li>{@code <=>} 为 zvector 的余弦距离操作符，<b>值越小越相似</b>（取值范围 [0,2]）。</li>
 *   <li>相似度 = 1 - 余弦距离，范围约为 [-1,1]，本类中用于与
 *       {@link SearchRequest#getSimilarityThreshold()} 比较（越大越相似）。</li>
 *   <li>embedding 由阿里云 DashScope {@code text-embedding-v3} 生成，默认维度 1024
 *       （可通过参数调整），故建表时 {@code real[]} 不固定维度，由实际向量动态决定。</li>
 * </ul>
 *
 * <p><b>注意：</b>真实向量检索需连接 AnalyticDB PostgreSQL 实例验证；本机冒烟仅验证
 * 编译与 Bean 装配，未连库时的行为不在本类覆盖范围内。</p>
 */
@Slf4j
public class ZVectorStore implements VectorStore, InitializingBean {

    // 集合表名白名单校验：仅允许字母、数字、下划线，防止 SQL 注入
    private static final Pattern COLLECTION_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    // metadata 的 JSON 序列化/反序列化工具
    private final ObjectMapper objectMapper = new ObjectMapper();

    // JDBC 操作模板（操作 AnalyticDB PostgreSQL）
    private final JdbcTemplate jdbcTemplate;

    // 文本向量化模型（DashScope text-embedding-v3）
    private final EmbeddingModel embeddingModel;

    // zvector 相关配置属性
    private final ZVectorProperties properties;

    /**
     * 构造器注入依赖。
     *
     * @param jdbcTemplate   AnalyticDB PostgreSQL JDBC 模板
     * @param embeddingModel 文本向量化模型
     * @param properties     zvector 配置属性
     */
    public ZVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, ZVectorProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    /**
     * 容器启动时校验集合表名并建表（幂等，已存在则跳过）。
     *
     * <p>建表语句：{@code id text PRIMARY KEY}、{@code content text}、
     * {@code metadata jsonb}、{@code embedding real[]}。
     * {@code real[]} 不固定维度，向量维度由 embedding 模型动态决定。</p>
     */
    @Override
    public void afterPropertiesSet() {
        String collection = validateCollectionName();
        String ddl = "CREATE TABLE IF NOT EXISTS " + collection
                + " (id text PRIMARY KEY, content text, metadata jsonb, embedding real[])";
        jdbcTemplate.execute(ddl);
        log.info("zvector 集合表 {} 已就绪（不存在则已创建）", collection);
    }

    /**
     * 批量写入文档：对每个文档文本做 embedding 后 upsert 到集合表。
     *
     * <p>embedding 通过 {@link #toPgRealArray(float[])} 转换为 PG 数组字面量
     * 字符串（如 {@code "{0.1,0.2,...}"}），再配合 {@code ?::real[]} 写入
     * PG 的 {@code real[]} 列——这是不依赖额外数组映射库、最稳妥的写入方式。</p>
     *
     * @param documents 待写入的文档列表
     */
    @Override
    public void add(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        String collection = validateCollectionName();
        // 幂等 upsert：id 冲突时更新 content / metadata / embedding
        String sql = "INSERT INTO " + collection + " (id, content, metadata, embedding) "
                + "VALUES (?, ?, ?::jsonb, ?::real[]) "
                + "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, "
                + "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding";
        for (Document doc : documents) {
            String id = resolveId(doc);
            String content = doc.getText();
            String metadataJson = toJson(doc.getMetadata());
            String embeddingLiteral = toPgRealArray(embeddingModel.embed(content));
            jdbcTemplate.update(sql, id, content, metadataJson, embeddingLiteral);
        }
        log.info("zvector 集合 {} 批量写入 {} 条文档", collection, documents.size());
    }

    /**
     * 按文档 ID 批量删除。
     *
     * <p>使用 {@code id = ANY(?)} 参数化删除，向量维度无需关心，安全且高效。</p>
     *
     * @param idList 待删除的文档 ID 列表
     */
    @Override
    public void delete(List<String> idList) {
        if (idList == null || idList.isEmpty()) {
            return;
        }
        String collection = validateCollectionName();
        String sql = "DELETE FROM " + collection + " WHERE id = ANY(?::text[])";
        // PG 数组字面量：'{a,b,c}'，配合 ?::text[] 参数化
        String idArrayLiteral = "{"
                + idList.stream().map(id -> "\"" + id.replace("\"", "\\\"") + "\"")
                        .collect(Collectors.joining(","))
                + "}";
        jdbcTemplate.update(sql, idArrayLiteral);
        log.info("zvector 集合 {} 按 ID 删除 {} 条文档", collection, idList.size());
    }

    /**
     * 按过滤条件删除文档。
     *
     * <p>当前为基础实现：filterExpression 为空时清空集合表（用于重建索引）；
     * 否则暂不支持条件删除，抛出 {@link BaseException}。filter 转 SQL 的能力
     * 留待 P4-T2 需要时再完善。</p>
     *
     * @param filterExpression 过滤条件表达式，可为空
     */
    @Override
    public void delete(Filter.Expression filterExpression) {
        String collection = validateCollectionName();
        if (filterExpression == null) {
            jdbcTemplate.update("DELETE FROM " + collection);
            log.info("zvector 集合 {} 已清空", collection);
            return;
        }
        throw new BaseException("暂不支持按条件删除");
    }

    /**
     * 相似度检索：将查询文本向量化后，用 zvector {@code <=>} 余弦距离排序取 TopK，
     * 并仅返回相似度大于等于阈值的文档。
     *
     * <p>检索 SQL 中 {@code <=>} 返回余弦距离（越小越相似），相似度 = 1 - 距离。
     * 结果按距离升序（相似度降序）排列，limit 取 {@code request.getTopK()} 条。</p>
     *
     * @param request 检索请求（包含查询文本、TopK、相似度阈值）
     * @return 满足阈值的文档列表（按相似度降序）
     */
    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        String collection = validateCollectionName();
        float[] queryVector = embeddingModel.embed(request.getQuery());
        String sql = "SELECT id, content, metadata, (embedding <=> ?::real[]) AS distance FROM "
                + collection + " ORDER BY distance ASC LIMIT ?";

        PreparedStatementSetter setter = ps -> {
            ps.setString(1, toPgRealArray(queryVector));
            ps.setInt(2, request.getTopK());
        };

        RowMapper<Document> rowMapper = (rs, rowNum) -> {
            double distance = rs.getDouble("distance");
            // 相似度 = 1 - 余弦距离
            double similarity = 1.0 - distance;
            if (similarity < request.getSimilarityThreshold()) {
                return null; // 低于阈值，后续过滤
            }
            String id = rs.getString("id");
            String content = rs.getString("content");
            String metadataJson = rs.getString("metadata");
            Document doc = new Document(id, content, parseMetadata(metadataJson));
            // 附带距离信息，便于上层做调试与展示
            doc.getMetadata().put("distance", distance);
            return doc;
        };

        List<Document> docs = jdbcTemplate.query(sql, setter, rowMapper);
        if (docs == null) {
            return Collections.emptyList();
        }
        return docs.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 校验并返回合法的集合表名（白名单：字母/数字/下划线），防止 SQL 注入。
     *
     * @return 合法的集合表名
     */
    private String validateCollectionName() {
        String collection = properties.getCollection();
        if (collection == null || !COLLECTION_NAME_PATTERN.matcher(collection).matches()) {
            throw new BaseException("非法的 zvector 集合表名: " + collection);
        }
        return collection;
    }

    /**
     * 解析文档 ID：为空时生成 UUID 作为主键。
     *
     * @param doc 文档
     * @return 非空文档 ID
     */
    private String resolveId(Document doc) {
        String id = doc.getId();
        return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    /**
     * 将 metadata 序列化为 JSON 字符串。
     *
     * @param metadata 文档元数据
     * @return JSON 字符串（null 时返回 "{}"）
     */
    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Collections.emptyMap() : metadata);
        } catch (Exception e) {
            throw new BaseException("metadata 序列化失败: " + e.getMessage());
        }
    }

    /**
     * 将 JSON 字符串反序列化为 metadata Map。
     *
     * @param json JSON 字符串（可能为 null）
     * @return metadata Map（反序列化失败或为空时返回空 Map）
     */
    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("metadata JSON 反序列化失败，返回空 Map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 将 float[] 向量转换为 PG {@code real[]} 数组字面量字符串。
     *
     * <p>示例：{@code [0.1, 0.2, 0.3]} → {@code "{0.1,0.2,0.3}"}，
     * 配合 {@code ?::real[]} 由 PG 解析为 {@code real[]} 类型。</p>
     *
     * @param vector 向量
     * @return PG 数组字面量字符串
     */
    private String toPgRealArray(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append('}');
        return sb.toString();
    }
}
