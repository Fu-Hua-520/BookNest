package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.UserAdminVO;
import com.fuhua.booknest.pojo.vo.UserBriefVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据电话查询用户
     * @param userPhone 手机号
     * @return 用户信息
     */
    User getUserByPhone(@Param("userPhone") String userPhone);

    /**
     * 根据邮箱查询用户
     * @param userEmail 邮箱
     * @return 用户信息
     */
    User getUserByEmail(@Param("userEmail") String userEmail);

    /**
     * 根据账户编号查询用户
     * @param account 账户编号
     * @return 用户信息
     */
    User getUserByAccount(@Param("account") String account);

    /**
     * 根据用户ID查询用户
     * @param userId 用户ID
     * @return 用户信息
     */
    User getUserById(@Param("userId") String userId);

    /**
     * 按ID批量查询用户（帖子/评论/会话列表组装时一次性取回作者，避免逐条 getUserById）。
     * 只取展示需要的字段，不返回密码。
     * @param userIds 用户ID集合
     * @return 用户列表
     */
    List<User> listByIds(@Param("userIds") List<String> userIds);

    /**
     * 批量取用户并转成「用户ID → 用户」，供列表组装统一使用。
     *
     * <p><b>默认方法而不是 XML 语句</b>：它要做的是「去重 → 一次 IN → 建索引」这套固定编排，
     * 写成默认方法就能被所有注入 UserMapper 的地方复用，不必在每个 Service 里各抄一份；
     * 真正查库的仍是上面那条 {@code listByIds}。</p>
     *
     * <p>两个必须由它兜住的边界：</p>
     * <ul>
     *   <li><b>空集合直接返回</b> —— {@code where id in ()} 是语法错误，绝不能把空列表传下去；</li>
     *   <li><b>先去重</b> —— 列表页里同一个人会反复出现（同一作者的多篇帖子、
     *       自己发的连续几条消息），不去重会让 IN 列表塞满重复 ID。</li>
     * </ul>
     *
     * @param userIds 用户ID集合（可为 null、可含重复、可含 null 元素）
     * @return 用户ID到用户实体的映射；<b>查不到的 ID 不会出现在 Map 里</b>，调用方按 null 处理
     */
    default Map<String, User> mapByIds(Collection<String> userIds) {
        Map<String, User> byId = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return byId;
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String userId : userIds) {
            if (userId != null && !userId.isEmpty()) {
                distinct.add(userId);
            }
        }
        if (distinct.isEmpty()) {
            return byId;
        }
        List<User> users = listByIds(new ArrayList<>(distinct));
        if (users != null) {
            for (User user : users) {
                if (user != null && user.getId() != null) {
                    byId.put(user.getId(), user);
                }
            }
        }
        return byId;
    }

    /**
     * 插入新用户
     * @param user 用户信息
     */
    void insertUser(User user);

    /**
     * 管理后台：关键字/状态条件查询用户列表（不含密码，供 PageHelper 分页）
     * @param keyword 关键字（用户名/账户/邮箱模糊匹配，可空）
     * @param status 状态（可空）
     * @return 用户列表
     */
    List<UserAdminVO> list(@Param("keyword") String keyword,
                           @Param("status") Integer status);

    /**
     * 更新用户状态
     * @param id 用户ID
     * @param status 状态
     */
    void updateStatus(@Param("id") String id, @Param("status") Integer status);

    /**
     * 更新用户资料（昵称 / 头像）
     * <p>两个字段都是「传 null 即不改」，因此调用方必须先保证至少有一个非空，
     * 否则 SQL 的 set 片段会拼成空的导致语法错误。</p>
     * @param id 用户ID
     * @param username 新昵称（null 表示不改）
     * @param avatar 新头像URL（null 表示不改）
     */
    /**
     * 按昵称 / 账号模糊搜索用户（只返回简要信息，不含手机号邮箱）
     * @param keyword 关键字
     * @param limit 最多返回条数
     * @return 用户简要信息
     */
    List<UserBriefVO> searchBrief(@Param("keyword") String keyword, @Param("limit") int limit);

    void updateProfile(@Param("id") String id,
                       @Param("username") String username,
                       @Param("avatar") String avatar);

    /**
     * 更新用户角色
     * @param id 用户ID
     * @param role 角色
     */
    void updateRole(@Param("id") String id, @Param("role") String role);
}
