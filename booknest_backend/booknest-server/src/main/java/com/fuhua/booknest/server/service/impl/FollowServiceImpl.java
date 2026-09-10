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
        notificationProducer.sendNotification(followeeId, NotificationConstant.TYPE_FOLLOW, currentUserName + " 关注了你", null);
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
        List<String> followeeIds = userFollowMapper.listFolloweeIds(userId);
        List<FollowVO> result = new ArrayList<>();
        if (followeeIds == null) {
            return result;
        }
        for (String followeeId : followeeIds) {
            User user = userMapper.getUserById(followeeId);
            if (user != null) {
                result.add(toFollowVO(user));
            }
        }
        return result;
    }

    @Override
    public List<FollowVO> listFollowers(String userId) {
        List<String> followerIds = userFollowMapper.listFollowerIds(userId);
        List<FollowVO> result = new ArrayList<>();
        if (followerIds == null) {
            return result;
        }
        for (String followerId : followerIds) {
            User user = userMapper.getUserById(followerId);
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
