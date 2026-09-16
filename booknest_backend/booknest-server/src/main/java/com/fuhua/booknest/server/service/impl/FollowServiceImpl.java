package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.NotificationConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.entity.UserFollow;
import com.fuhua.booknest.pojo.vo.FollowVO;
import com.fuhua.booknest.server.mapper.UserFollowMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.mq.NotificationProducer;
import com.fuhua.booknest.server.service.FollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class FollowServiceImpl implements FollowService {

    @Autowired
    private UserFollowMapper userFollowMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NotificationProducer notificationProducer;

    @Override
    public void follow(String followeeId) {
        String currentId = BaseContext.getCurrentId();
        // 不能关注自己
        if (currentId != null && currentId.equals(followeeId)) {
            throw new BaseException("不能关注自己");
        }
        // 被关注用户必须存在
        User followee = userMapper.getUserById(followeeId);
        if (followee == null) {
            throw new BaseException("用户不存在");
        }
        // 已关注则幂等返回（不重复插入、不重复发通知）
        if (userFollowMapper.selectByFollowerAndFollowee(currentId, followeeId) != null) {
            return;
        }

        // 插入关注记录
        userFollowMapper.insert(UserFollow.builder()
                .id(UUID.randomUUID().toString())
                .followerId(currentId)
                .followeeId(followeeId)
                .createTime(LocalDateTime.now())
                .build());

        // 发送关注通知（异步）
        User currentUser = userMapper.getUserById(currentId);
        String currentUserName = (currentUser == null || currentUser.getUsername() == null) ? "用户" : currentUser.getUsername();
        // sourceId 传「关注者的用户ID」，而不是 null —— 这条通知的落点是关注者的个人主页。
        // 前端 NotificationView.onOpen 里 FOLLOW 分支是 `router.push('/user/' + item.sourceId)`，
        // 而它前面还有一句 `if (!item.sourceId) return`：传 null 会让「点关注通知」什么都不发生，
        // 那段跳转逻辑成了永远走不到的死代码。
        notificationProducer.sendNotification(
                followeeId, NotificationConstant.TYPE_FOLLOW, currentUserName + " 关注了你", currentId);
    }

    @Override
    public void unfollow(String followeeId) {
        userFollowMapper.deleteByFollowerAndFollowee(BaseContext.getCurrentId(), followeeId);
    }

    @Override
    public boolean isFollowing(String followeeId) {
        return userFollowMapper.selectByFollowerAndFollowee(BaseContext.getCurrentId(), followeeId) != null;
    }

    @Override
    public List<FollowVO> listFollowing(String userId) {
        return batchToFollowVO(userFollowMapper.listFolloweeIds(userId));
    }

    @Override
    public List<FollowVO> listFollowers(String userId) {
        return batchToFollowVO(userFollowMapper.listFollowerIds(userId));
    }

    /**
     * 批量把用户ID转成摘要 VO。
     *
     * <p>一次 {@code listByIds}（单条 IN 查询）取代「循环里逐条 getUserById」：
     * 关注 500 人时查询数从 500 降到 1。</p>
     *
     * <p>刻意<b>不复用 IN 查询返回的行序</b>：它不保证与入参一致，而关注/粉丝列表的顺序
     * 有业务含义（按关注时间倒序）。所以按入参 id 顺序回填，保证顺序与原实现完全一致。</p>
     *
     * @param userIds 目标用户ID列表（可为 null/空）
     * @return 摘要 VO 列表，顺序与入参一致；查不到的 id 直接跳过（与原实现一致）
     */
    private List<FollowVO> batchToFollowVO(List<String> userIds) {
        List<FollowVO> result = new ArrayList<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        Map<String, User> byId = userMapper.mapByIds(userIds);
        for (String userId : userIds) {
            User user = byId.get(userId);
            if (user != null) {
                result.add(toFollowVO(user));
            }
        }
        return result;
    }

    /**
     * 组装用户摘要 VO
     * @param user 用户实体
     * @return 用户摘要 VO
     */
    private FollowVO toFollowVO(User user) {
        return FollowVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .build();
    }
}
