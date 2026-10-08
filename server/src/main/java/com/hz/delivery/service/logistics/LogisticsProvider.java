package com.hz.delivery.service.logistics;

import com.hz.delivery.entity.Order;

/** 真实物流查询通道，业务层只依赖此接口，便于后续更换供应商。 */
public interface LogisticsProvider {

    boolean isEnabled();

    /** 将系统承运商编码转换为供应商编码；返回空表示暂不支持。 */
    String providerCode(String carrierCode);

    LogisticsQueryResult query(Order order);
}
