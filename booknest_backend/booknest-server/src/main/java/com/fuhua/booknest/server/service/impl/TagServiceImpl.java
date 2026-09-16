package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Tag;
import com.fuhua.booknest.server.common.RedisCacheHelper;
import com.fuhua.booknest.server.mapper.PostTagMapper;
import com.fuhua.booknest.server.mapper.TagMapper;
import com.fuhua.booknest.server.service.TagService;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class TagServiceImpl implements TagService {

    // 热门标签默认数量
    private static final Integer DEFAULT_HOT_LIMIT = 20;
    /** 标签名称长度上限（与 tag.name VARCHAR(30) 对齐） */
    private static final int NAME_MAX_LENGTH = 30;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private PostTagMapper postTagMapper;

    @Autowired
    private RedisCacheHelper cache;

    @Override
    public List<Tag> listTags() {
        // 标签是典型的「读多写极少」字典表：全站每个发帖页 / 筛选面板都要拉全量，
        // 而标签的增删改一个月也未必有一次。整表缓存一把梭，失效逻辑只在一处（evictTagCache）。
        return filterEnabled(loadAllTags());
    }

    @Override
    public List<Tag> getHotTags(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = DEFAULT_HOT_LIMIT;
        }
        // 直接从整表缓存里按实时引用数排序取前 N —— 不再走一次带相关子查询的 SQL。
        // 状态在取回结果后过滤：热门榜条数可能因此略少于 limit，属预期行为
        List<Tag> all = filterEnabled(loadAllTags());
        if (all.size() <= limit) {
            return all;
        }
        List<Tag> sorted = new ArrayList<>(all);
        // 引用数降序；同引用数按标签名，保证顺序稳定可复现
        sorted.sort((a, b) -> {
            int ua = a.getUseCount() == null ? 0 : a.getUseCount();
            int ub = b.getUseCount() == null ? 0 : b.getUseCount();
            if (ua != ub) {
                return Integer.compare(ub, ua);
            }
            return String.valueOf(a.getName()).compareTo(String.valueOf(b.getName()));
        });
        return new ArrayList<>(sorted.subList(0, limit));
    }

    @Override
    public List<Tag> searchTags(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        // 标签总量小，在内存里做包含匹配即可，省掉一次 LIKE 全表扫描
        String kw = lower(keyword.trim());
        List<Tag> result = new ArrayList<>();
        for (Tag tag : filterEnabled(loadAllTags())) {
            if (tag.getName() != null && lower(tag.getName()).contains(kw)) {
                result.add(tag);
            }
        }
        return result;
    }

    @Override
    public PageInfo<Tag> listTagPage(String keyword, Integer page, Integer pageSize) {
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }

        // 管理端不过滤状态，禁用标签同样需要被看到和恢复
        List<Tag> source;
        if (kw == null) {
            source = loadAllTags();
        } else {
            String lower = lower(kw);
            source = new ArrayList<>();
            for (Tag tag : loadAllTags()) {
                if (tag.getName() != null && lower(tag.getName()).contains(lower)) {
                    source.add(tag);
                }
            }
        }
        // 手工分页：数据源已是内存里的整表快照，再用 PageHelper 包一层反而要求原 SQL 具备
        // 「外层不加工」的形状，得不偿失。这里直接按切片构造 PageInfo。
        return slice(source, page, pageSize);
    }

    /**
     * 用户端自由创建标签：重名即复用。
     *
     * 与管理端 createTag 的差别只在「重名怎么办」：管理端建标签是显式动作，
     * 重名几乎一定是误操作，报错更合适；而用户是在发帖时随手输入标签名，
     * 撞上已有标签是常态，这时必须静默复用，否则发帖会莫名其妙失败。
     * 并发下两个请求同时插入同名标签会被 tag.name 唯一索引拦掉一个，
     * 这里靠 @Transactional 回滚后再查一次兜底。
     */
    @Override
    public Tag getOrCreateByName(String name) {
        String tagName = normalizeName(name);

        Tag existing = tagMapper.selectByName(tagName);
        if (existing != null) {
            if (existing.getStatus() != null && existing.getStatus() == STATUS_DISABLED) {
                throw new BaseException("标签「" + tagName + "」已被停用，请换一个");
            }
            return existing;
        }

        Tag toInsert = Tag.builder()
                .id(UUID.randomUUID().toString())
                .name(tagName)
                .useCount(0)
                .status(STATUS_ENABLED)
                .createTime(LocalDateTime.now())
                .build();
        try {
            tagMapper.insert(toInsert);
            log.info("用户创建标签成功：id={}, name={}", toInsert.getId(), tagName);
        } catch (Exception e) {
            // 并发插入撞唯一索引：退回「复用已有」语义，不算失败
            log.info("标签「{}」已存在，复用已有记录", tagName);
        }
        evictTagCache();
        // 回读一次，拿到实时 useCount（insert 时不带统计值）
        Tag saved = tagMapper.selectByName(tagName);
        return saved == null ? toInsert : saved;
    }

    @Override
    public String createTag(Tag tag) {
        if (tag == null) {
            throw new BaseException("标签信息不能为空");
        }
        String name = normalizeName(tag.getName());
        // 依赖应用层查重，避免直接抛 SQLIntegrityConstraintViolationException（tag.name 有唯一索引）
        if (tagMapper.selectByName(name) != null) {
            throw new BaseException("标签「" + name + "」已存在");
        }

        Tag toInsert = Tag.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .useCount(0)
                .status(normalizeStatus(tag.getStatus()))
                .createTime(LocalDateTime.now())
                .build();
        tagMapper.insert(toInsert);
        log.info("新增标签成功：id={}, name={}", toInsert.getId(), name);
        evictTagCache();
        return toInsert.getId();
    }

    @Override
    public void updateTag(Tag tag) {
        if (tag == null) {
            throw new BaseException("标签信息不能为空");
        }
        String id = trimToNull(tag.getId());
        if (id == null) {
            throw new BaseException("标签ID不能为空");
        }
        Tag existing = tagMapper.selectById(id);
        if (existing == null) {
            throw new BaseException("标签不存在");
        }

        String name = normalizeName(tag.getName());
        Tag sameName = tagMapper.selectByName(name);
        if (sameName != null && !id.equals(sameName.getId())) {
            throw new BaseException("标签「" + name + "」已存在");
        }

        Tag toUpdate = Tag.builder()
                .id(id)
                .name(name)
                // useCount 已不再由业务维护：读取时一律由 post_tag 关联表实时统计覆盖
                // （见 TagMapper.xml）。这里把读到的实时值原样写回，只是为了不改动 update 语句，
                // 对对外展示没有影响（展示值每次查询都会重新计算）。
                .useCount(existing.getUseCount() == null ? 0 : existing.getUseCount())
                .status(normalizeStatus(tag.getStatus()))
                .build();
        tagMapper.update(toUpdate);
        log.info("更新标签成功：id={}, name={}", id, name);
        evictTagCache();
    }

    @Override
    public void deleteTag(String id) {
        String tagId = trimToNull(id);
        if (tagId == null) {
            throw new BaseException("标签ID不能为空");
        }
        if (tagMapper.selectById(tagId) == null) {
            throw new BaseException("标签不存在");
        }
        int refCount = postTagMapper.countByTagId(tagId);
        if (refCount > 0) {
            throw new BaseException("该标签已被 " + refCount + " 篇帖子使用，无法删除");
        }
        tagMapper.deleteById(tagId);
        log.info("删除标签成功：id={}", tagId);
        evictTagCache();
    }

    /* ------------------------------ 缓存 ------------------------------ */

    /**
     * 取全量标签（带实时引用数）。命中 Redis 直接返回，未命中回源并写缓存。
     *
     * <p>为什么整表缓存是安全的：标签总量是「几十到几百」量级，一次序列化传输成本很低；
     * 而读取它的地方极多（发帖页标签选择、筛选面板、热门标签榜、管理端列表）。
     * 真值始终在 MySQL，Redis 只是加速层 —— {@link RedisCacheHelper} 保证 Redis 故障时自动回源。</p>
     */
    private List<Tag> loadAllTags() {
        List<Tag> cached = cache.get(RedisConstant.TAG_ALL);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        List<Tag> fromDb = tagMapper.listAll();
        if (fromDb != null && !fromDb.isEmpty()) {
            cache.set(RedisConstant.TAG_ALL, fromDb, RedisConstant.EXPIRE_1_HOUR);
        }
        // 统一返回可变副本：调用方（如 getHotTags）会就地排序，不能污染缓存对象
        return fromDb == null ? new ArrayList<>() : new ArrayList<>(fromDb);
    }

    /**
     * 标签数据发生任何变更后清空整表缓存。
     *
     * <p>注意「用户端自由建标签」这条路径（{@link #getOrCreateByName}）也必须清 ——
     * 它写库的频率远高于管理端，如果漏掉，新标签会在一小时内对所有人不可见。</p>
     */
    private void evictTagCache() {
        cache.evict(RedisConstant.TAG_ALL);
    }

    /* ------------------------------ 内部辅助方法 ------------------------------ */

    /**
     * 内存分页：把已在内存里的列表按页码切片，包装成 PageInfo。
     * total 取源列表长度，前端 el-pagination 依赖它算总页数。
     */
    private PageInfo<Tag> slice(List<Tag> source, int page, int pageSize) {
        int total = source.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        PageInfo<Tag> pageInfo = new PageInfo<>(new ArrayList<>(source.subList(from, to)));
        pageInfo.setTotal(total);
        pageInfo.setPageNum(page);
        pageInfo.setPageSize(pageSize);
        pageInfo.setPages((int) Math.ceil((double) total / pageSize));
        return pageInfo;
    }

    /** 过滤掉已禁用的标签（前台视图使用） */
    private List<Tag> filterEnabled(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return tags;
        }
        List<Tag> result = new ArrayList<>(tags.size());
        for (Tag tag : tags) {
            if (tag.getStatus() == null || tag.getStatus() != STATUS_DISABLED) {
                result.add(tag);
            }
        }
        return result;
    }

    /**
     * 大小写无关比较用的归一化。
     *
     * <p>显式指定 {@link java.util.Locale#ROOT}：{@code toLowerCase()} 无参会用 JVM 默认区域，
     * 在土耳其语区域下 {@code "I"} 会被转成 {@code "ı"}（无点小写 i），导致
     * 「İstanbul」这类标签名搜不到。老代码走 MySQL 的 LIKE，排序规则是 utf8mb4_general_ci，
     * 不存在这个坑；改成内存匹配后必须自己把这个坑补上。</p>
     */
    private String lower(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private String normalizeName(String name) {
        String value = trimToNull(name);
        if (value == null) {
            throw new BaseException("标签名称不能为空");
        }
        if (value.length() > NAME_MAX_LENGTH) {
            throw new BaseException("标签名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /** 状态缺省为启用，只接受 0/1 */
    private int normalizeStatus(Integer status) {
        return status != null && status == STATUS_DISABLED ? STATUS_DISABLED : STATUS_ENABLED;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
