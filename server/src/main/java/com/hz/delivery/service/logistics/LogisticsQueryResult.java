package com.hz.delivery.service.logistics;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** 承运商聚合平台返回的标准化轨迹。 */
@Data
@AllArgsConstructor
public class LogisticsQueryResult {

    private boolean success;
    private String message;
    private String state;
    /** 物流平台实际返回的快递公司编码，用于发货前防止选错承运商。 */
    private String companyCode;
    private String waybillNo;
    private List<Trace> traces;

    public static LogisticsQueryResult failed(String message) {
        return new LogisticsQueryResult(false, message, null, null, null, Collections.emptyList());
    }

    @Data
    @AllArgsConstructor
    public static class Trace {
        private String description;
        private LocalDateTime time;
        private String statusName;
        private String statusCode;
    }
}
