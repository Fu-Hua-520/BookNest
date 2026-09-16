package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.BarMember;
import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.AdminCategoryVO;
import com.fuhua.booknest.pojo.vo.BarVO;
import com.fuhua.booknest.pojo.vo.CategoryVO;
import com.fuhua.booknest.server.common.RedisCacheHelper;
import com.fuhua.booknest.server.mapper.BarMemberMapper;
import com.fuhua.booknest.server.mapper.CategoryMapper;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 书吧服务。
 *
 * ⚠️ 关于「分级」：书吧最早是两级书籍分类，现已取消层级 ——
 * 一个吧就是 category 表里的一条平级记录，parent_id 恒为 NULL。
 * 代码里仍保留 CategoryVO.children / parentId 字段（历史字段，恒空/恒 null），
 * 目的是不动前端已建立的字段契约；**新逻辑不要再基于 parentId 判层级**。
 * 方法名 getCategoryTree 同理，只是为了不改 /category/tree 这个调用点。
 */
@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    /** 吧名长度上限（与 category.name VARCHAR(50) 对齐） */
    private static final int NAME_MAX_LENGTH = 50;
    /** 吧简介长度上限（与 category.description VARCHAR(200) 对齐） */
    private static final int DESCRIPTION_MAX_LENGTH = 200;
    /** 书吧图标 URL 长度上限（与 category.icon VARCHAR(500) 对齐） */
    private static final int ICON_MAX_LENGTH = 500;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;

    /** 书吧创建申请的审核状态：待审核 / 已通过 / 已驳回 */
    private static final int AUDIT_PENDING = 0;
    private static final int AUDIT_APPROVED = 1;
    private static final int AUDIT_REJECTED = 2;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BarMemberMapper barMemberMapper;

    @Autowired
    private RedisCacheHelper cache;

    /* -------------------- 书吧读缓存 --------------------
     * 书吧全量列表与热门榜原本各自用「进程内 volatile + 时间戳」缓存，改为 Redis：
     *  1）多实例部署时各存一份，改数据后要等所有实例 TTL 到期才一致，现象是「刷新几次忽好忽坏」；
     *  2）listHotBars 的 synchronized 重算会把并发请求串行化，冷启动时尤其明显；
     *  3）书吧变更（建吧 / 审核 / 编辑 / 删除）之后需要立刻失效，Redis 一个 DEL 全实例生效。
     * 真值始终在 MySQL，Redis 只是加速层（RedisCacheHelper 保证故障时自动回源）。 */

    private static final int HOT_BARS_LIMIT = 20;

    /** 排序：sortOrder 升序，null 排最后；同序号按吧名，保证顺序稳定可复现 */
    private static final Comparator<Category> ORDER_COMPARATOR = Comparator
            .comparing(Category::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(c -> c.getName() == null ? "" : c.getName());

    /**
     * 查询全部可见书吧（扁平列表）。
     * <p>书吧已取消分级，这里不再组装 children、也不再按 parentId 挂载，
     * 直接把所有「已过审且未禁用」的吧平铺返回。</p>
     */
    @Override
    public List<CategoryVO> getCategoryTree() {
        List<Category> all = loadVisibleBars();

        List<CategoryVO> result = new ArrayList<>(all.size());
        for (Category category : all) {
            result.add(toVO(category));
        }
        return result;
    }

    /**
     * 管理端：完整书吧列表（不做状态过滤，额外携带 status / description / 时间戳）
     */
    @Override
    public List<AdminCategoryVO> getCategoryTreeForAdmin() {
        List<Category> all = new ArrayList<>(categoryMapper.listAll());
        all.sort(ORDER_COMPARATOR);

        List<AdminCategoryVO> result = new ArrayList<>();
        for (Category category : all) {
            result.add(toAdminVO(category));
        }
        return result;
    }

    @Override
    public String createCategory(Category category) {
        if (category == null) {
            throw new BaseException("书吧信息不能为空");
        }
        String name = normalizeName(category.getName());
        String description = normalizeDescription(category.getDescription());
        String icon = normalizeIcon(category.getIcon());

        List<Category> all = categoryMapper.listAll();
        // 书吧已无层级，重名判定是全局的（不再按 parentId 分层）
        ensureNameAvailable(all, null, name);

        Category toInsert = Category.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .icon(icon)
                // 书吧不再分级，parent_id 恒为 null
                .parentId(null)
                .sortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder())
                .description(description)
                .status(normalizeStatus(category.getStatus()))
                // 管理端后台直接建的吧视为「官方吧」：无需审批、没有吧主
                .ownerId(null)
                .auditStatus(AUDIT_APPROVED)
                .createTime(LocalDateTime.now())
                .build();
        categoryMapper.insert(toInsert);
        log.info("新增书吧成功：id={}, name={}", toInsert.getId(), name);
        evictBarCache();
        return toInsert.getId();
    }

    @Override
    public void updateCategory(Category category) {
        if (category == null) {
            throw new BaseException("书吧信息不能为空");
        }
        String id = trimToNull(category.getId());
        if (id == null) {
            throw new BaseException("书吧ID不能为空");
        }
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BaseException("书吧不存在");
        }

        String name = normalizeName(category.getName());
        String description = normalizeDescription(category.getDescription());
        String icon = normalizeIcon(category.getIcon());

        List<Category> all = categoryMapper.listAll();
        ensureNameAvailable(all, id, name);

        Category toUpdate = Category.builder()
                .id(id)
                .name(name)
                .icon(icon)
                .parentId(null)
                .sortOrder(category.getSortOrder() == null ? 0 : category.getSortOrder())
                .description(description)
                .status(normalizeStatus(category.getStatus()))
                // 吧主与审核态不属于「编辑书吧」的范畴，必须原样带回去：
                // update 语句按 if 判空拼 set，这里传 null 虽然不会覆盖，但显式回填更不容易被后人改坏
                .ownerId(existing.getOwnerId())
                .auditStatus(existing.getAuditStatus())
                .build();
        categoryMapper.update(toUpdate);
        log.info("更新书吧成功：id={}, name={}", id, name);
        evictBarCache();
    }

    @Override
    public void deleteCategory(String id) {
        String barId = trimToNull(id);
        if (barId == null) {
            throw new BaseException("书吧ID不能为空");
        }
        if (categoryMapper.selectById(barId) == null) {
            throw new BaseException("书吧不存在");
        }
        // 书吧已无层级，不再需要「先删子级」的前置校验
        int postCount = postMapper.countByCategoryId(barId);
        if (postCount > 0) {
            throw new BaseException("该吧下仍有 " + postCount + " 篇帖子，无法删除");
        }
        categoryMapper.deleteById(barId);
        log.info("删除书吧成功：id={}", barId);
        evictBarCache();
    }

    /* ------------------------------ 书吧（贴吧式「吧」） ------------------------------ */

    @Override
    public List<BarVO> listBars() {
        List<BarVO> cached = cache.get(RedisConstant.BAR_LIST);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        List<BarVO> result = buildBarsSortedByPostCount();
        if (!result.isEmpty()) {
            cache.set(RedisConstant.BAR_LIST, result, RedisConstant.EXPIRE_1_HOUR);
        }
        return result;
    }

    /** 书吧广场列表：可见吧 + 帖数，按帖数降序。回源逻辑独立出来便于缓存包装。 */
    private List<BarVO> buildBarsSortedByPostCount() {
        Map<String, Long> postCounts = loadPostCounts();

        List<BarVO> result = new ArrayList<>();
        for (Category category : loadVisibleBars()) {
            result.add(toBarVO(category, postCounts));
        }

        // 帖数降序；同帖数按吧名，保证顺序稳定可复现
        result.sort((a, b) -> {
            long pa = a.getPostCount() == null ? 0L : a.getPostCount();
            long pb = b.getPostCount() == null ? 0L : b.getPostCount();
            if (pa != pb) {
                return Long.compare(pb, pa);
            }
            return String.valueOf(a.getName()).compareTo(String.valueOf(b.getName()));
        });
        return result;
    }

    @Override
    public String applyBar(String name, String description, String icon) {
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || currentId.isEmpty()) {
            throw new BaseException("请先登录");
        }
        String barName = normalizeName(name);
        String barDesc = normalizeBarDescription(description);
        String barIcon = normalizeIcon(icon);

        for (Category category : categoryMapper.listAll()) {
            if (!barName.equals(category.getName())) {
                continue;
            }
            if (isVisibleBar(category)) {
                throw new BaseException("书吧「" + barName + "」已存在");
            }
            // 同一个人对同一个吧名只允许挂一条待审申请，否则后台会出现一堆重复待办
            if (Objects.equals(currentId, category.getOwnerId()) && isAuditStatus(category, AUDIT_PENDING)) {
                throw new BaseException("你已经提交过「" + barName + "」的创建申请，请等待审核");
            }
        }

        Category bar = Category.builder()
                .id(UUID.randomUUID().toString())
                .name(barName)
                .icon(barIcon)
                .parentId(null)
                .sortOrder(0)
                .description(barDesc)
                // 审核通过前先置为禁用：即便有查询路径漏掉了审核态过滤，
                // status=0 也能兜住「没过的吧不许发帖」这条底线
                .status(STATUS_DISABLED)
                .ownerId(currentId)
                .auditStatus(AUDIT_PENDING)
                .createTime(LocalDateTime.now())
                .build();
        categoryMapper.insert(bar);
        log.info("书吧创建申请已提交：id={}, name={}, ownerId={}", bar.getId(), barName, currentId);
        // 申请本身不改变「可见吧」集合（新吧 status=0 未过审），但保留失效是为了
        // 让「我的申请」这类读到全表的路径不被打脸 —— 成本一次 DEL，收益是一致性直觉
        evictBarCache();
        return bar.getId();
    }

    @Override
    public List<BarVO> listMyBars() {
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || currentId.isEmpty()) {
            throw new BaseException("请先登录");
        }
        Map<String, Long> postCounts = loadPostCounts();
        List<BarVO> result = new ArrayList<>();
        for (Category category : barMemberMapper.listFollowedBars(currentId)) {
            result.add(toBarVO(category, postCounts));
        }
        return result;
    }

    @Override
    public List<BarVO> listHotBars() {
        String key = RedisConstant.BAR_HOT + HOT_BARS_LIMIT;
        List<BarVO> cached = cache.get(key);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        List<BarVO> hot = buildHotBars();
        if (!hot.isEmpty()) {
            cache.set(key, hot, RedisConstant.EXPIRE_1_HOUR);
        }
        return hot;
    }

    /**
     * 热门书吧榜回源：按吧成员数 TopN。
     *
     * <p>排行榜是「迟一点更新也无所谓」的展示数据 —— 成员数变化不会立刻反映到榜上，
     * 这是刻意接受的代价（换来的是每次打开书吧页都不用扫全表）。</p>
     */
    private List<BarVO> buildHotBars() {
        Map<String, Long> memberCounts = new HashMap<>();
        List<Map<String, Object>> rows = barMemberMapper.countGroupByBar();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object barId = row.get("barId");
                if (barId == null) {
                    continue;
                }
                Object count = row.get("memberCount");
                memberCounts.put(String.valueOf(barId), count == null ? 0L : ((Number) count).longValue());
            }
        }

        List<BarVO> hot = new ArrayList<>();
        for (Category category : loadVisibleBars()) {
            BarVO vo = toBarVO(category, Map.of());
            // 成员数独立字段（postCount 是帖子数，两个榜互不掺和）
            vo.setMemberCount(memberCounts.getOrDefault(category.getId(), 0L));
            hot.add(vo);
        }
        hot.sort((a, b) -> {
            long ma = a.getMemberCount() == null ? 0L : a.getMemberCount();
            long mb = b.getMemberCount() == null ? 0L : b.getMemberCount();
            if (ma != mb) {
                return Long.compare(mb, ma);
            }
            return String.valueOf(a.getName()).compareTo(String.valueOf(b.getName()));
        });
        return new ArrayList<>(hot.subList(0, Math.min(HOT_BARS_LIMIT, hot.size())));
    }

    @Override
    public List<BarVO> searchBars(String keyword) {
        String kw = trimToNull(keyword);
        if (kw == null) {
            return List.of();
        }
        // 搜索命中率天然分散，缓存整体收益低、失效成本高，这里直接回源
        Map<String, Long> postCounts = loadPostCounts();
        List<BarVO> result = new ArrayList<>();
        for (Category category : loadVisibleBars()) {
            if (category.getName() != null && category.getName().contains(kw)) {
                result.add(toBarVO(category, postCounts));
            }
        }
        // 前缀命中排前面，包含命中排后面，搜索体验更接近直觉
        result.sort((a, b) -> {
            boolean aPrefix = a.getName().startsWith(kw);
            boolean bPrefix = b.getName().startsWith(kw);
            if (aPrefix != bPrefix) {
                return aPrefix ? -1 : 1;
            }
            return String.valueOf(a.getName()).compareTo(String.valueOf(b.getName()));
        });
        return result;
    }

    @Override
    public List<BarVO> listMyApplications() {
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || currentId.isEmpty()) {
            throw new BaseException("请先登录");
        }
        Map<String, Long> postCounts = loadPostCounts();
        List<BarVO> result = new ArrayList<>();
        for (Category category : categoryMapper.listByOwnerId(currentId)) {
            // 已通过的申请不再展示：对应的吧已经上了广场，「我的申请」里再挂一份
            // 只会让人误以为还有个独立的东西 —— 这里只留待审与被驳回的进度
            if (isAuditStatus(category, AUDIT_APPROVED)) {
                continue;
            }
            result.add(toBarVO(category, postCounts));
        }
        return result;
    }

    @Override
    public List<BarVO> listBarApplications(Integer auditStatus) {
        Map<String, Long> postCounts = loadPostCounts();
        List<BarVO> result = new ArrayList<>();
        for (Category category : categoryMapper.listAll()) {
            // 官方吧没有 ownerId，不属于「用户申请」，不进审核列表
            if (category.getOwnerId() == null || category.getOwnerId().isEmpty()) {
                continue;
            }
            if (auditStatus != null && !isAuditStatus(category, auditStatus)) {
                continue;
            }
            result.add(toBarVO(category, postCounts));
        }
        // 最新的申请排前面，方便管理员先处理新提交的
        result.sort((a, b) -> {
            if (a.getCreateTime() == null || b.getCreateTime() == null) {
                return 0;
            }
            return b.getCreateTime().compareTo(a.getCreateTime());
        });
        return result;
    }

    @Override
    public void auditBar(String id, boolean approve, String rejectReason, String ownerId) {
        String barId = trimToNull(id);
        if (barId == null) {
            throw new BaseException("书吧ID不能为空");
        }
        Category bar = categoryMapper.selectById(barId);
        if (bar == null) {
            throw new BaseException("书吧不存在");
        }
        if (!isAuditStatus(bar, AUDIT_PENDING)) {
            throw new BaseException("该申请已处理过，无需重复审核");
        }
        String reason = trimToNull(rejectReason);
        if (!approve && reason == null) {
            throw new BaseException("驳回时必须填写原因");
        }
        if (reason != null && reason.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BaseException("驳回原因不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }

        String finalOwnerId = bar.getOwnerId();
        if (approve) {
            // 管理员可通过 ownerId 指定吧主（比如申请人不想当吧主，指定给活跃书友）
            String assigned = trimToNull(ownerId);
            if (assigned != null && !assigned.equals(bar.getOwnerId())) {
                User owner = userMapper.getUserById(assigned);
                if (owner == null) {
                    throw new BaseException("要设为吧主的用户不存在");
                }
                categoryMapper.updateOwner(barId, assigned);
                log.info("书吧吧主由管理员指定：barId={}, ownerId={}", barId, assigned);
            }
            if (assigned != null) {
                finalOwnerId = assigned;
            }
            // 通过即入吧：吧主自动成为本吧成员（已关注则跳过），等级经验从今天开始累计
            ensureMember(barId, finalOwnerId);
        }

        categoryMapper.auditBar(barId, approve ? AUDIT_APPROVED : AUDIT_REJECTED, approve ? null : reason);
        log.info("书吧申请审核完成：id={}, name={}, approve={}, ownerId={}", barId, bar.getName(), approve, finalOwnerId);
        // 审核通过 = 新吧上广场 + 新成员进榜，这是唯一会改变热门榜名次的用户侧动作
        evictBarCache();
    }

    /** 确保用户在吧成员表里有一行（关注即入吧的唯一兜底入口，已存在时静默跳过） */
    private void ensureMember(String barId, String userId) {
        if (barId == null || userId == null) {
            return;
        }
        if (barMemberMapper.selectByBarAndUser(barId, userId) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        barMemberMapper.insert(BarMember.builder()
                .id(UUID.randomUUID().toString())
                .barId(barId)
                .userId(userId)
                .exp(0)
                .level(1)
                .dailyExp(0)
                .dailyDate(now.toLocalDate())
                .createTime(now)
                .updateTime(now)
                .build());
    }

    /* ------------------------------ 内部辅助方法 ------------------------------ */

    /**
     * 取「可见书吧」列表（已过审 + 未禁用，按 sortOrder 排好）。
     *
     * <p>这份快照被 getCategoryTree / listBars / listHotBars / searchBars 共用，
     * 但它们各自的缓存 TTL 与失效时机不同（榜单要算成员数、搜索不缓存），
     * 所以这里不做缓存，只做「一次查库 + 一次过滤 + 一次排序」的收敛 —— 真正省下的是
     * 每个调用点各写一遍过滤逻辑时容易漏掉审核态判断的风险。</p>
     */
    private List<Category> loadVisibleBars() {
        List<Category> all = new ArrayList<>(categoryMapper.listAll());
        all.sort(ORDER_COMPARATOR);
        List<Category> visible = new ArrayList<>(all.size());
        for (Category category : all) {
            if (isVisibleBar(category)) {
                visible.add(category);
            }
        }
        return visible;
    }

    /**
     * 书吧数据变更后清空读缓存。
     *
     * <p>失效粒度说明：BAR_LIST 与 BAR_HOT 必须一起清 —— 书吧广场列表和热门榜
     * 数据源重叠但排序维度不同，任何一次建吧/改吧都可能同时影响两者。
     * 用 DEL 而不是「更新缓存」，是因为写路径分散（用户申请 / 管理端审核 / 编辑 / 删除），
     * 让下一次读来重建更不容易漏。</p>
     */
    private void evictBarCache() {
        cache.evict(RedisConstant.BAR_LIST);
        // 热门榜按 limit 分键，这里用前缀清理，避免将来改动 limit 时漏清
        cache.evictByPattern(RedisConstant.BAR_HOT + "*");
    }

    /**
     * 书吧对普通用户是否可见：必须已通过审核且未被禁用。
     * auditStatus 为空按「已通过」处理，兼容升级脚本执行前写入的历史数据。
     */
    private boolean isVisibleBar(Category category) {
        return !isDisabled(category) && isAuditStatus(category, AUDIT_APPROVED);
    }

    private boolean isAuditStatus(Category category, int expected) {
        Integer actual = category.getAuditStatus();
        return (actual == null ? AUDIT_APPROVED : actual) == expected;
    }

    /** 一次性取回「吧ID到帖子数」的映射，避免在循环里逐个 count 造成 N+1 */
    private Map<String, Long> loadPostCounts() {
        Map<String, Long> counts = new HashMap<>();
        List<Map<String, Object>> rows = categoryMapper.countPostsGroupByCategory();
        if (rows == null) {
            return counts;
        }
        for (Map<String, Object> row : rows) {
            Object categoryId = row.get("categoryId");
            Object postCount = row.get("postCount");
            if (categoryId == null) {
                continue;
            }
            counts.put(String.valueOf(categoryId),
                    postCount == null ? 0L : ((Number) postCount).longValue());
        }
        return counts;
    }

    /**
     * 书吧实体转前台 VO。
     *
     * <p>吧主昵称/头像<b>直接从实体上读</b>，不在这里查库：它们由列表 SQL 的 left join 带出
     * （见 Category.ownerName 的说明）。原来这里逐条 {@code getUserById}，是书吧广场、
     * 热门榜、搜索、我的申请、审核列表这五处的共同 N+1 来源 —— 一页 N 个吧就是 N 次查用户。
     * 现在这些接口查用户次数为 <b>0</b>。</p>
     */
    private BarVO toBarVO(Category category, Map<String, Long> postCounts) {
        Long postCount = postCounts.get(category.getId());
        return BarVO.builder()
                .id(category.getId())
                .name(category.getName())
                .icon(trimToNull(category.getIcon()))
                .description(category.getDescription())
                .ownerId(category.getOwnerId())
                .ownerName(category.getOwnerName())
                .ownerAvatar(category.getOwnerAvatar())
                .postCount(postCount == null ? 0L : postCount)
                .auditStatus(category.getAuditStatus())
                .rejectReason(category.getRejectReason())
                .createTime(category.getCreateTime())
                .build();
    }

    private String normalizeBarDescription(String description) {
        String value = trimToNull(description);
        if (value != null && value.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BaseException("吧简介不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /**
     * 吧名全局唯一校验（书吧已不分级，不再有「同级」的限定）。
     * @param excludeId 查重时排除的吧ID（更新场景传自身），新增时传 null
     */
    private void ensureNameAvailable(List<Category> all, String excludeId, String name) {
        for (Category category : all) {
            if (excludeId != null && excludeId.equals(category.getId())) {
                continue;
            }
            if (name.equals(category.getName())) {
                throw new BaseException("已存在名为「" + name + "」的书吧");
            }
        }
    }

    private String normalizeName(String name) {
        String value = trimToNull(name);
        if (value == null) {
            throw new BaseException("书吧名称不能为空");
        }
        if (value.length() > NAME_MAX_LENGTH) {
            throw new BaseException("书吧名称不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    private String normalizeDescription(String description) {
        String value = trimToNull(description);
        if (value != null && value.length() > DESCRIPTION_MAX_LENGTH) {
            throw new BaseException("书吧简介不能超过 " + DESCRIPTION_MAX_LENGTH + " 个字符");
        }
        return value;
    }

    /**
     * 图标校验。允许为空（前端回退成吧名首字），为空时归一成 null 而不是空串，
     * 避免库里出现 '' 与 NULL 两种「没图标」的写法。
     */
    private String normalizeIcon(String icon) {
        String value = trimToNull(icon);
        if (value != null && value.length() > ICON_MAX_LENGTH) {
            throw new BaseException("书吧图标地址过长，请重新上传");
        }
        return value;
    }

    /** 状态缺省为启用，只接受 0/1 */
    private int normalizeStatus(Integer status) {
        return status != null && status == STATUS_DISABLED ? STATUS_DISABLED : STATUS_ENABLED;
    }

    private boolean isDisabled(Category category) {
        return category.getStatus() != null && category.getStatus() == STATUS_DISABLED;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 书吧实体转 VO（children 恒为空 —— 书吧已不分级）
     */
    private CategoryVO toVO(Category category) {
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .icon(trimToNull(category.getIcon()))
                .parentId(null)
                .sortOrder(category.getSortOrder())
                .build();
    }

    /**
     * 书吧实体转管理端 VO（children 恒为空 —— 书吧已不分级）
     *
     * <p>同 {@code toBarVO}：吧主昵称由 SQL 的 left join 带出（见 Category.ownerName），
     * 这里不再逐条查用户。调用方是 {@code categoryMapper.listAll()}，已带 join。</p>
     */
    private AdminCategoryVO toAdminVO(Category category) {
        return AdminCategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .icon(trimToNull(category.getIcon()))
                .parentId(null)
                .sortOrder(category.getSortOrder())
                .description(category.getDescription())
                .status(category.getStatus())
                .ownerId(category.getOwnerId())
                .ownerName(category.getOwnerName())
                .auditStatus(category.getAuditStatus())
                .createTime(category.getCreateTime())
                .updateTime(category.getUpdateTime())
                .build();
    }
}
