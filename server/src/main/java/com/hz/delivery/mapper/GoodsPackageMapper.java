package com.hz.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hz.delivery.entity.GoodsPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GoodsPackageMapper extends BaseMapper<GoodsPackage> {

    /**
     * 原子扣减库存：条件带 stock >= qty，防止并发超卖
     */
    @Update("UPDATE t_package SET stock = stock - #{qty}, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0 AND stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") Integer qty);

    /**
     * 回补库存（取消订单时调用）
     */
    @Update("UPDATE t_package SET stock = stock + #{qty}, update_time = NOW() " +
            "WHERE id = #{id} AND deleted = 0")
    int restoreStock(@Param("id") Long id, @Param("qty") Integer qty);
}
