package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.BarMember;
import com.fuhua.booknest.pojo.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface BarMemberMapper {

    /**
     * 入吧（关注）。uid 由调用方生成。
     * @param member 成员记录
     */
    void insert(BarMember member);

    /**
     * 退吧（取关）
     * @param barId 书吧ID
     * @param userId 用户ID
     */
    void deleteByBarAndUser(@Param("barId") String barId, @Param("userId") String userId);

    /**
     * 查询成员记录（不存在返回 null）
     * @param barId 书吧ID
     * @param userId 用户ID
     * @return 成员记录
     */
    BarMember selectByBarAndUser(@Param("barId") String barId, @Param("userId") String userId);

    /**
     * 原子加分：在<b>同一条 UPDATE</b> 里完成「当日额度裁剪 → 累加经验 → 派生等级快照」。
     *
     * <p>原先是「先 select 读出来 → 在内存里算 → 再无条件覆盖写回」。那个写法有两个真问题：
     * 同一 (吧, 人) 并发加分时后写的会把先写的整体覆盖掉（<b>丢经验</b>），
     * 而且两个请求各自读到「今日还剩 100」时会 <b>一起加过上限</b>。
     * 读与写分离是根因，所以这里合成单条带条件的原子 UPDATE。</p>
     *
     * <p>额度裁剪表达为 {@code least(amount, greatest(0, limit - base))}，
     * 其中 {@code base} = 「记录的日期是否等于今天 ? 当日已得经验 : 0」—— 跨天自动重置额度。
     * 等级由 {@code levelFromExp} 片段派生，它引用的 exp 是<b>本语句刚更新后</b>的值
     * （MySQL 的 SET 赋值从左到右求值），故 exp 与 level 不可能写得不一致。</p>
     *
     * @param barId 书吧ID
     * @param userId 用户ID
     * @param amount 申请加的分（必须 &gt; 0）
     * @param limit 每日经验上限
     * @param today 今天（调用方传入，不用 curdate()：可读、可测、不受容器时区影响）
     * @return 影响行数；0 表示不是本吧成员（无记录可更新）或本次无任何列发生变化
     */
    int grantExpWithinLimit(@Param("barId") String barId,
                            @Param("userId") String userId,
                            @Param("amount") int amount,
                            @Param("limit") int limit,
                            @Param("today") LocalDate today);

    /**
     * 原子扣回经验（取消点赞等），经验不低于 0。
     *
     * <p>刻意<b>不返还</b>当日额度，避免「点赞 → 取消 → 再点赞」刷经验；
     * 但日期照旧刷成今天（跨天时当日经验归零），与原逻辑一致。</p>
     *
     * @param barId 书吧ID
     * @param userId 用户ID
     * @param amount 扣减额（负数）
     * @param today 今天
     * @return 影响行数；0 表示不是本吧成员
     */
    int deductExp(@Param("barId") String barId,
                  @Param("userId") String userId,
                  @Param("amount") int amount,
                  @Param("today") LocalDate today);

    /**
     * 按经验降序取某吧的成员（等级榜）
     * @param barId 书吧ID
     * @param limit 取前几条
     * @return 成员记录
     */
    List<BarMember> listTopByExp(@Param("barId") String barId, @Param("limit") int limit);

    /**
     * 统计某吧的成员数（即关注数）
     * @param barId 书吧ID
     * @return 成员数
     */
    int countByBar(@Param("barId") String barId);

    /**
     * 查询我关注的所有书吧ID
     * @param userId 用户ID
     * @return 书吧ID列表
     */
    List<String> listBarIdsByUser(@Param("userId") String userId);

    /**
     * 查询我关注的、当前可见（已过审 + 未禁用）的书吧完整信息。
     * 关注时间倒序：最近关注的排最前，侧边栏「我关注的吧」直接可用。
     * @param userId 用户ID
     * @return 书吧实体列表
     */
    List<Category> listFollowedBars(@Param("userId") String userId);

    /**
     * 统计每个吧的成员数（一次 group by 拿全量，供「热门书吧」排序）
     * @return [{ barId, memberCount }]
     */
    List<Map<String, Object>> countGroupByBar();

    /**
     * 删掉某吧的全部成员（书吧被删除时清理）
     * @param barId 书吧ID
     */
    void deleteByBarId(@Param("barId") String barId);
}
