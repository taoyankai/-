package com.hz.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.delivery.entity.Grantee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GranteeMapper extends BaseMapper<Grantee> {

    @Select("SELECT * FROM t_grantee WHERE phone = #{phone} AND deleted = 0 LIMIT 1")
    Grantee selectByPhone(@Param("phone") String phone);

    /**
     * 原子扣减领取额度：条件里带 used < quota，避免并发下超额领取。
     * 返回受影响行数，0 表示额度已用尽（本项目每人限领 1 份）。
     */
    @Update("UPDATE t_grantee SET used = used + 1, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0 AND used < quota")
    int consumeQuota(@Param("id") Long id);

    /**
     * 回退额度（取消订单时调用）
     */
    @Update("UPDATE t_grantee SET used = CASE WHEN used > 0 THEN used - 1 ELSE 0 END, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0")
    int releaseQuota(@Param("id") Long id);
}
