package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.BarConstant;
import com.fuhua.booknest.common.constant.PostStatusConstant;
import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.BarLevelTitle;
import com.fuhua.booknest.pojo.entity.BarMember;
import com.fuhua.booknest.pojo.entity.BarModerator;
import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.BarLevelTitleVO;
import com.fuhua.booknest.pojo.vo.BarManageVO;
import com.fuhua.booknest.pojo.vo.BarMemberVO;
import com.fuhua.booknest.pojo.vo.BarModeratorVO;
import com.fuhua.booknest.server.mapper.BarLevelTitleMapper;
import com.fuhua.booknest.server.mapper.BarMemberMapper;
import com.fuhua.booknest.server.mapper.BarModeratorMapper;
import com.fuhua.booknest.server.mapper.CategoryMapper;
import com.fuhua.booknest.server.mapper.PostMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.common.RedisCacheHelper;
import com.fuhua.booknest.server.service.BarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 书吧社区化实现
 *
 * <p>两个容易搞错的点：</p>
 * <ol>
 *   <li>吧主不在 bar_moderator 里 —— 吧主看 category.owner_id，
 *       判定「能不能管这个吧」必须两个来源都查。</li>
 *   <li>经验只发给本吧成员。没关注就点赞不累计经验，
 *       否则「关注」这个动作就没有意义了。</li>
 * </ol>
 */
@Service
@Slf4j
public class BarServiceImpl implements BarService {

    @Autowired
    private BarMemberMapper barMemberMapper;
    @Autowired
    private BarModeratorMapper barModeratorMapper;
    @Autowired
    private BarLevelTitleMapper barLevelTitleMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private PostMapper postMapper;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisCacheHelper cache;

    @Override
    public BarMemberVO myMembership(String barId) {
        Category bar = requireBar(barId);
        String currentId = BaseContext.getCurrentId();
        if (currentId == null) {
            return BarMemberVO.builder()
                    .barId(bar.getId())
                    .followed(false)
                    .role(BarConstant.ROLE_NONE)
                    .exp(0)
                    .level(1)
                    .dailyExp(0)
                    .dailyLimit(BarConstant.DAILY_EXP_LIMIT)
                    .nextLevelExp(BarConstant.nextLevelExp(0))
                    .build();
        }

        BarMember member = barMemberMapper.selectByBarAndUser(bar.getId(), currentId);
        String role = resolveRole(bar, currentId, member);
        int exp = member == null || member.getExp() == null ? 0 : member.getExp();
        int level = BarConstant.levelOf(exp);
        int dailyExp = sameDay(member) ? (member.getDailyExp() == null ? 0 : member.getDailyExp()) : 0;

        User me = userMapper.getUserById(currentId);
        return BarMemberVO.builder()
                .barId(bar.getId())
                .userId(currentId)
                .username(me == null ? null : me.getUsername())
                .avatar(me == null ? null : me.getAvatar())
                .followed(member != null)
                .role(role)
                .exp(exp)
                .level(level)
                .title(barLevelTitleMapper.selectTitle(bar.getId(), level))
                .dailyExp(dailyExp)
                .dailyLimit(BarConstant.DAILY_EXP_LIMIT)
                .nextLevelExp(BarConstant.nextLevelExp(exp))
                .build();
    }

    @Override
    @Transactional
    public void follow(String barId) {
        Category bar = requireBar(barId);
        String currentId = requireLogin();
        if (barMemberMapper.selectByBarAndUser(bar.getId(), currentId) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        barMemberMapper.insert(BarMember.builder()
                .id(UUID.randomUUID().toString())
                .barId(bar.getId())
                .userId(currentId)
                .exp(0)
                .level(1)
                .dailyExp(0)
                .dailyDate(LocalDate.now())
                .createTime(now)
                .updateTime(now)
                .build());
    }

    @Override
    @Transactional
    public void unfollow(String barId) {
        String currentId = requireLogin();
        barMemberMapper.deleteByBarAndUser(barId, currentId);
    }

    @Override
    public List<BarModeratorVO> listModerators(String barId) {
        requireBar(barId);
        List<String> userIds = barModeratorMapper.listUserIdsByBar(barId);
        List<BarModeratorVO> result = new ArrayList<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        // 一次 IN 查询取回全部管理员资料，替代原来的「循环里逐条 getUserById」。
        // 顺序仍按 userIds 走：管理员的展示顺序由 listUserIdsByBar 决定，不该被 IN 的行序打乱。
        Map<String, User> byId = userMapper.mapByIds(userIds);
        for (String userId : userIds) {
            User user = byId.get(userId);
            result.add(BarModeratorVO.builder()
                    .userId(userId)
                    .username(user == null ? null : user.getUsername())
                    .avatar(user == null ? null : user.getAvatar())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public void addModerator(String barId, String userId) {
        Category bar = requireBar(barId);
        requireOwner(bar, "只有吧主可以任命管理员");
        doAddModerator(bar, userId);
    }

    @Override
    @Transactional
    public void removeModerator(String barId, String userId) {
        Category bar = requireBar(barId);
        requireOwner(bar, "只有吧主可以撤销管理员");
        barModeratorMapper.deleteByBarAndUser(bar.getId(), userId);
    }

    @Override
    public List<BarLevelTitleVO> listTitles(String barId) {
        requireBar(barId);
        List<BarLevelTitleVO> result = new ArrayList<>();
        for (BarLevelTitle item : barLevelTitleMapper.listByBar(barId)) {
            result.add(BarLevelTitleVO.builder().level(item.getLevel()).title(item.getTitle()).build());
        }
        return result;
    }

    @Override
    @Transactional
    public void setTitles(String barId, List<BarLevelTitleVO> titles) {
        Category bar = requireBar(barId);
        requireOwner(bar, "只有吧主可以设置等级称号");
        doSetTitles(bar, titles);
    }

    /** 写称号的公共实现（整批重设：先清空再写，去掉的等级要真的消失） */
    private void doSetTitles(Category bar, List<BarLevelTitleVO> titles) {
        barLevelTitleMapper.deleteByBarId(bar.getId());
        if (titles == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (BarLevelTitleVO item : titles) {
            if (item == null || item.getTitle() == null) {
                continue;
            }
            String name = item.getTitle().trim();
            if (name.isEmpty() || item.getLevel() == null) {
                continue;
            }
            if (item.getLevel() < 1 || item.getLevel() > BarConstant.MAX_LEVEL) {
                throw new BaseException("等级只能在 1 ~ " + BarConstant.MAX_LEVEL + " 之间");
            }
            if (name.length() > 20) {
                throw new BaseException("称号不超过 20 个字符");
            }
            barLevelTitleMapper.upsert(BarLevelTitle.builder()
                    .id(UUID.randomUUID().toString())
                    .barId(bar.getId())
                    .level(item.getLevel())
                    .title(name)
                    .createTime(now)
                    .updateTime(now)
                    .build());
        }
    }

    /** 写管理员的公共实现（不含「你是不是吧主」的校验，权限由调用方各自把关） */
    private void doAddModerator(Category bar, String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new BaseException("请选择要任命的用户");
        }
        if (userMapper.getUserById(userId) == null) {
            throw new BaseException("用户不存在");
        }
        if (userId.equals(bar.getOwnerId())) {
            throw new BaseException("吧主本人无需任命");
        }
        if (barModeratorMapper.countByBarAndUser(bar.getId(), userId) > 0) {
            throw new BaseException("该用户已经是本吧管理员");
        }
        barModeratorMapper.insert(BarModerator.builder()
                .id(UUID.randomUUID().toString())
                .barId(bar.getId())
                .userId(userId)
                .createTime(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public void setPostVisible(String barId, String postId, boolean visible) {
        Category bar = requireBar(barId);
        String currentId = requireLogin();
        requireManager(bar, currentId);

        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BaseException("帖子不存在");
        }
        if (!bar.getId().equals(post.getCategoryId())) {
            throw new BaseException("这篇帖子不属于本吧");
        }
        // 隐藏 = 下架（不是草稿）：帖子本身还在，只是不在公开列表与详情里出现
        postMapper.updateStatus(postId,
                visible ? PostStatusConstant.STATUS_PUBLISHED : PostStatusConstant.STATUS_OFFLINE);
        // 下架/恢复同样影响用户端列表可见性，必须清列表缓存
        cache.evictByPattern(RedisConstant.POST_LIST_PATTERN);
        log.info("吧务{}帖子成功：barId={}, postId={}, operator={}",
                visible ? "恢复" : "隐藏", bar.getId(), postId, currentId);
    }

    @Override
    @Transactional
    public void addExp(String barId, String userId, int amount) {
        if (barId == null || barId.trim().isEmpty() || userId == null || amount == 0) {
            return;
        }
        // 一条原子 UPDATE 完成「额度裁剪 + 累加经验 + 派生等级」，细节见 BarMemberMapper 的注释。
        //
        // 刻意不再先 select 判成员：那条 where 本身就是成员判定 —— 不是本吧成员时影响 0 行，
        // 与旧代码「查不到就 return」效果一致，却少一次往返，也顺带消掉了「读到的值被并发改掉」的窗口。
        //
        // 0 行受影响有两种可能（无成员行 / 额度已用尽且日期无需刷新），所以日志措辞必须中性，
        // 不能写成「不是本吧成员」—— 那会把「今天已经攒满 100 经验」误报成异常。
        LocalDate today = LocalDate.now();
        int affected = amount > 0
                ? barMemberMapper.grantExpWithinLimit(barId, userId, amount, BarConstant.DAILY_EXP_LIMIT, today)
                : barMemberMapper.deductExp(barId, userId, amount, today);
        if (affected == 0) {
            log.debug("经验未变更: barId={}, userId={}, amount={}（不是本吧成员，或当日额度已用尽）",
                    barId, userId, amount);
        }
    }

    @Override
    public boolean isBarManager(String barId, String userId) {
        if (barId == null || barId.trim().isEmpty() || userId == null) {
            return false;
        }
        Category bar = categoryMapper.selectById(barId);
        if (bar == null) {
            return false;
        }
        if (userId.equals(bar.getOwnerId())) {
            return true;
        }
        return barModeratorMapper.countByBarAndUser(bar.getId(), userId) > 0;
    }

    /* ---------------- 管理后台专用 ---------------- */

    @Override
    public BarManageVO getManageInfo(String barId) {
        Category bar = requireBar(barId);
        String ownerId = bar.getOwnerId();
        User owner = null;
        if (ownerId != null && !ownerId.isEmpty()) {
            owner = userMapper.getUserById(ownerId);
        }
        return BarManageVO.builder()
                .barId(bar.getId())
                .barName(bar.getName())
                .icon(bar.getIcon())
                .ownerId(ownerId)
                .ownerName(owner == null ? null : owner.getUsername())
                .ownerAvatar(owner == null ? null : owner.getAvatar())
                .moderators(listModerators(bar.getId()))
                .titles(listTitles(bar.getId()))
                .memberCount((long) barMemberMapper.countByBar(bar.getId()))
                .build();
    }

    @Override
    @Transactional
    public void adminSetOwner(String barId, String userId) {
        Category bar = requireBar(barId);
        String target = userId == null || userId.trim().isEmpty() ? null : userId.trim();
        if (target == null) {
            // 收回吧主 → 变成「官方吧」；吧务留着，只是没人再是吧主
            categoryMapper.updateOwner(bar.getId(), null);
            log.info("管理员收回书吧吧主：barId={}", bar.getId());
            return;
        }
        if (target.equals(bar.getOwnerId())) {
            return;
        }
        if (userMapper.getUserById(target) == null) {
            throw new BaseException("要设为吧主的用户不存在");
        }
        categoryMapper.updateOwner(bar.getId(), target);
        // 新吧主如果原先挂着管理员，那行就是冗余的（吧主权限本来就更大），顺手清掉
        barModeratorMapper.deleteByBarAndUser(bar.getId(), target);
        // 吧主必须是吧内成员，否则他在自己吧里连等级都没有
        ensureMember(bar.getId(), target);
        log.info("管理员任命书吧吧主：barId={}, ownerId={}", bar.getId(), target);
    }

    @Override
    @Transactional
    public void adminAddModerator(String barId, String userId) {
        Category bar = requireBar(barId);
        doAddModerator(bar, userId);
    }

    @Override
    @Transactional
    public void adminRemoveModerator(String barId, String userId) {
        Category bar = requireBar(barId);
        barModeratorMapper.deleteByBarAndUser(bar.getId(), userId);
    }

    @Override
    @Transactional
    public void adminSetTitles(String barId, List<BarLevelTitleVO> titles) {
        Category bar = requireBar(barId);
        doSetTitles(bar, titles);
    }

    /** 确保用户在吧成员表里有一行（已存在时静默跳过） */
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

    /** 记录里的 daily_date 是否就是今天 */
    private boolean sameDay(BarMember member) {
        return member != null && member.getDailyDate() != null && member.getDailyDate().equals(LocalDate.now());
    }

    /**
     * 判定某人在某吧的角色
     * @param member 成员记录（未关注时为 null）
     */
    private String resolveRole(Category bar, String userId, BarMember member) {
        if (userId.equals(bar.getOwnerId())) {
            return BarConstant.ROLE_OWNER;
        }
        if (barModeratorMapper.countByBarAndUser(bar.getId(), userId) > 0) {
            return BarConstant.ROLE_MODERATOR;
        }
        return member == null ? BarConstant.ROLE_NONE : BarConstant.ROLE_MEMBER;
    }

    /** 校验书吧存在 */
    private Category requireBar(String barId) {
        if (barId == null || barId.trim().isEmpty()) {
            throw new BaseException("书吧不存在");
        }
        Category bar = categoryMapper.selectById(barId);
        if (bar == null) {
            throw new BaseException("书吧不存在");
        }
        return bar;
    }

    /** 校验当前用户是吧主 */
    private void requireOwner(Category bar, String message) {
        String currentId = requireLogin();
        if (!currentId.equals(bar.getOwnerId())) {
            throw new BaseException(message);
        }
    }

    /** 校验当前用户是吧主或管理员 */
    private void requireManager(Category bar, String currentId) {
        if (currentId.equals(bar.getOwnerId())) {
            return;
        }
        if (barModeratorMapper.countByBarAndUser(bar.getId(), currentId) > 0) {
            return;
        }
        throw new BaseException("只有吧主或管理员可以操作");
    }

    private String requireLogin() {
        String currentId = BaseContext.getCurrentId();
        if (currentId == null) {
            throw new BaseException("请先登录");
        }
        return currentId;
    }
}
