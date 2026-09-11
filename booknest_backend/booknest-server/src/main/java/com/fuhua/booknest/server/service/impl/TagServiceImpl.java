package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Tag;
import com.fuhua.booknest.server.mapper.PostTagMapper;
import com.fuhua.booknest.server.mapper.TagMapper;
import com.fuhua.booknest.server.service.TagService;
import com.github.pagehelper.PageHelper;
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

    @Override
    public List<Tag> listTags() {
        return filterEnabled(tagMapper.listAll());
    }

    @Override
    public List<Tag> getHotTags(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = DEFAULT_HOT_LIMIT;
        }
        // 状态在取回结果后过滤：热门榜条数可能因此略少于 limit，属预期行为
        return filterEnabled(tagMapper.listHotTags(limit));
    }

    @Override
    public List<Tag> searchTags(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return filterEnabled(tagMapper.searchByKeyword(keyword.trim()));
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

        PageHelper.startPage(page, pageSize);
        // 管理端不过滤状态，禁用标签同样需要被看到和恢复
        List<Tag> list = kw == null ? tagMapper.listAll() : tagMapper.searchByKeyword(kw);
        return new PageInfo<>(list);
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
                // useCount 由发帖流程维护，此处沿用库中值，不接收表单传入
                .useCount(existing.getUseCount() == null ? 0 : existing.getUseCount())
                .status(normalizeStatus(tag.getStatus()))
                .build();
        tagMapper.update(toUpdate);
        log.info("更新标签成功：id={}, name={}", id, name);
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
    }

    /* ------------------------------ 内部辅助方法 ------------------------------ */

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
