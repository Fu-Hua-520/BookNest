package com.fuhua.booknest.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 计数落库幂等账本。
 *
 * <h3>它存在的唯一理由</h3>
 * 计数缓冲的落库是「RENAME 换出 → 批量 UPDATE → DEL 暂存区」。若 UPDATE 提交成功、
 * 而 DEL 失败（Redis 抖动 / 连接被掐 / 进程恰好被 kill），那批增量会留在暂存区里，
 * 被下一轮当成残留再落一次 —— 同一批增量累加两次。
 *
 * <p>这个窗口消不掉：「把 DEL 提到 UPDATE 之前」换来的是丢计数，
 * 两个存储之间不存在第三个原子点。唯一出路是让落库本身幂等 ——
 * 每批增量带一个批次ID（写在缓冲 HASH 的 {@code __batch} 字段里，
 * 由 RENAME 与增量原子地一起搬进暂存区），落库时先在这里按ID 登记。</p>
 *
 * <p><b>关键约束：登记与计数 UPDATE 必须在同一个事务里。</b>
 * 只有那样，「本表里有这个ID」才严格等价于「这批已经写进 MySQL」：
 * 重试时命中已有ID → 跳过更新、只做确认删除。
 * 表结构见 {@code booknest_data/sql/upgrade/2026-09-16-count-flush-ledger-upgrade.sql}。</p>
 *
 * @see com.fuhua.booknest.server.job.CountFlushJob
 */
@Mapper
public interface CountFlushLedgerMapper {

    /**
     * 这个批次是否已经登记过（也就是：这批增量是否已经落过库）。
     *
     * @param batchId 批次ID
     * @return 登记过返回 &gt;0；否则 0
     */
    int countBatch(@Param("batchId") String batchId);

    /**
     * 登记一批已落库的增量。
     *
     * <p>用普通 INSERT 而不是 {@code INSERT IGNORE}：主键冲突时它应该抛异常并回滚整个事务，
     * 而不是被静默吞掉 ——「以为登记成功其实没登记」与「以为没登记其实登记了」都是错的，
     * 后者更糟（会重复累加）。正常情况下调用方已经先查过 {@link #countBatch}，
     * 这里撞主键只可能是多实例并发落了同一批，回滚正是想要的结局。</p>
     */
    void insert(@Param("batchId") String batchId);

    /**
     * 清理过期登记。
     *
     * <p>登记只在「残留可能被重落」的窗口里有意义，而这个窗口以落库周期计（秒级）。
     * 保留 N 天是极大的余量 —— 一条残留能躺 N 天，说明落库早就在停摆，那时的问题
     * 也不在账本上了。</p>
     *
     * @param days 保留天数
     * @return 删除的行数
     */
    int deleteAppliedBeforeDays(@Param("days") int days);
}
