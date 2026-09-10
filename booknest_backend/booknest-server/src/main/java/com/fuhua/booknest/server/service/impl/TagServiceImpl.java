package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.pojo.entity.Tag;
import com.fuhua.booknest.server.mapper.TagMapper;
import com.fuhua.booknest.server.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class TagServiceImpl implements TagService {

    // 热门标签默认数量
    private static final Integer DEFAULT_HOT_LIMIT = 20;

    @Autowired
    private TagMapper tagMapper;

    @Override
    public List<Tag> listTags() {
        return tagMapper.listAll();
    }

    @Override
    public List<Tag> getHotTags(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = DEFAULT_HOT_LIMIT;
        }
        return tagMapper.listHotTags(limit);
    }

    @Override
    public List<Tag> searchTags(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return tagMapper.searchByKeyword(keyword.trim());
    }
}
