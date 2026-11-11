package com.laimengchao.sso.job.module.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.job.module.job.entity.JobLock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 选主租约的原子操作。用单条 UPDATE 抢锁，避免「先查后写」的竞态。
 */
public interface JobLockMapper extends BaseMapper<JobLock> {

    /** 首次启动补行；主键固定为 1，重复执行无副作用 */
    @Insert("INSERT IGNORE INTO job_lock (id, lock_name, owner, expire_at) "
            + "VALUES (1, 'scheduler', '', '1970-01-01 00:00:00')")
    int ensureRow();

    /**
     * 当前持有者续约，或租约已过期时抢占；返回 1 表示拿到锁。
     * 过期判断与续约时间都用数据库 NOW()，避免多实例机器时钟偏差导致双主。
     */
    @Update("UPDATE job_lock SET owner = #{owner}, expire_at = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND) "
            + "WHERE lock_name = 'scheduler' AND (owner = #{owner} OR expire_at < NOW())")
    int acquire(@Param("owner") String owner, @Param("leaseSeconds") int leaseSeconds);

    /** 停机释放：只释放自己的锁，避免误清别人的租约 */
    @Update("UPDATE job_lock SET owner = '', expire_at = '1970-01-01 00:00:00' "
            + "WHERE lock_name = 'scheduler' AND owner = #{owner}")
    int release(@Param("owner") String owner);
}
